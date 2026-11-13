import { describe, expect, it } from "vitest";
import { createTryOnRouter } from "../src/tryon/handler";
import {
  buildLookbookMockPngBytes,
  buildSyntheticJpegHeaderBytes,
  buildSyntheticWebpHeaderBytes,
  decodeStrictBase64,
  encodeBase64,
  inspectImageBytes,
  validateTryOnImageBuffer,
} from "../src/tryon/image_pipeline";
import { createOpenAiImageEditsMock } from "../src/tryon/openai_mock";
import {
  buildOpenAiTryOnPrompt,
  createUnreviewedOpenAiRubric,
  OPENAI_VTO_PROMPT_VERSION,
} from "../src/tryon/openai_prompt";
import {
  computeOpenAiUsageUsd,
  estimateOpenAiEditMaxUsd,
  OpenAITryOnProvider,
  redactSecretsFromDiagnostic,
  validateOpenAiModelSelection,
} from "../src/tryon/openai_provider";
import {
  UpstreamAmbiguousTimeoutError,
  UpstreamDeterministicError,
} from "../src/tryon/providers";
import { InMemoryTryOnJobStore, JsonLedgerTryOnJobStore } from "../src/tryon/store";
import type {
  DeletionReceiptWire,
  SafeTryOnLogEntry,
  TryOnEnvConfig,
  TryOnErrorEnvelope,
  TryOnJobSubmitResponse,
  TryOnResultWire,
  TryOnSubmitWireRequest,
  WireImageInput,
} from "../src/tryon/types";

function makePngWireImage(
  mediaId: string,
  view: "FRONT" | "BACK",
  garmentRgb: [number, number, number] = [44, 88, 132],
  revision = 1,
): WireImageInput {
  const pngBytes = buildLookbookMockPngBytes({
    width: 128,
    height: 160,
    garmentRgb,
  });
  return {
    mediaId,
    view,
    mimeType: "image/png",
    width: 128,
    height: 160,
    revision,
    contentHash: `sha256:${mediaId}_v${revision}`,
    base64Data: encodeBase64(pngBytes),
  };
}

function makeValidOpenAiSubmitRequest(
  overrides: Partial<TryOnSubmitWireRequest> = {},
): TryOnSubmitWireRequest {
  const base: TryOnSubmitWireRequest = {
    schemaVersion: 2,
    requestId: "req_openai_001",
    idempotencyKey: "idem_openai_001",
    inputGeneration: 1,
    profileRevision: 1,
    outfitSnapshot: {
      outfitId: "outfit_openai_1",
      wardrobeSnapshotId: "ward_snap_1",
      items: [
        {
          itemId: "item_top_logo",
          category: "TOP",
          revision: 1,
          colors: ["navy", "white"],
        },
      ],
    },
    selectedItemIds: ["item_top_logo"],
    requestedViews: ["FRONT"],
    consentGrant: {
      id: "consent_openai_1",
      purpose: "TRYON",
      providerId: "openai",
      providerConfigVersion: "v2-openai-poc-1",
      confirmedByUser: true,
      issuedAtEpochMillis: 1_760_000_000_000,
      allowedViews: ["FRONT"],
      selectedMediaIds: ["person_front_1", "garment_front_1"],
      requestGeneration: 1,
      revokedAtEpochMillis: null,
    },
    providerConfigVersion: "v2-openai-poc-1",
    personImages: {
      front: makePngWireImage("person_front_1", "FRONT", [60, 70, 90]),
    },
    garmentImages: {
      item_top_logo: {
        front: makePngWireImage("garment_front_1", "FRONT", [32, 68, 128]),
      },
    },
  };
  return { ...base, ...overrides };
}

describe("Track C OpenAI Virtual Try-On Scaffold (Sections 3–15)", () => {
  describe("A. Image Validation, Magic Bytes & EXIF Orientation", () => {
    it("validates PNG, JPEG (including EXIF orientation 6 swap), and WebP buffers", () => {
      const pngBytes = buildLookbookMockPngBytes({ width: 192, height: 256 });
      const pngRes = validateTryOnImageBuffer(pngBytes, {
        label: "person.FRONT",
        declaredMimeType: "image/png",
        strictMimeMatch: true,
        maxBytes: 2 * 1024 * 1024,
        minDimensionPx: 64,
      });
      expect(pngRes.ok).toBe(true);
      if (pngRes.ok) {
        expect(pngRes.metadata.mimeType).toBe("image/png");
        expect(pngRes.metadata.effectiveWidth).toBe(192);
        expect(pngRes.metadata.effectiveHeight).toBe(256);
        expect(pngRes.metadata.exifOrientation).toBe(1);
      }

      // Portrait phone photo stored sideways (1024x768 raw sensor, EXIF Orientation = 6 -> 768x1024 upright)
      const jpegRotated = buildSyntheticJpegHeaderBytes(1024, 768, 6);
      const jpegRes = validateTryOnImageBuffer(jpegRotated, {
        label: "person.FRONT.jpeg",
        declaredMimeType: "image/jpeg",
        strictMimeMatch: true,
        maxBytes: 2 * 1024 * 1024,
        minDimensionPx: 64,
      });
      expect(jpegRes.ok).toBe(true);
      if (jpegRes.ok) {
        expect(jpegRes.metadata.mimeType).toBe("image/jpeg");
        expect(jpegRes.metadata.width).toBe(1024);
        expect(jpegRes.metadata.height).toBe(768);
        expect(jpegRes.metadata.exifOrientation).toBe(6);
        expect(jpegRes.metadata.effectiveWidth).toBe(768);
        expect(jpegRes.metadata.effectiveHeight).toBe(1024);
        expect(jpegRes.metadata.needsOrientationNormalization).toBe(true);
      }

      const webpBytes = buildSyntheticWebpHeaderBytes(640, 960);
      const webpRes = validateTryOnImageBuffer(webpBytes, {
        label: "garment.FRONT.webp",
        declaredMimeType: "image/webp",
        strictMimeMatch: true,
        maxBytes: 1024 * 1024,
        minDimensionPx: 64,
      });
      expect(webpRes.ok).toBe(true);
      if (webpRes.ok) {
        expect(webpRes.metadata.mimeType).toBe("image/webp");
        expect(webpRes.metadata.effectiveWidth).toBe(640);
        expect(webpRes.metadata.effectiveHeight).toBe(960);
      }
    });

    it("rejects corrupted bytes, MIME-vs-magic mismatch, oversized buffers, and out-of-range dimensions", () => {
      const corrupt = new TextEncoder().encode("NOT_A_VALID_RASTER_FILE_HEADER_1234567890");
      expect(inspectImageBytes(corrupt)).toBeNull();

      const mismatch = validateTryOnImageBuffer(buildSyntheticJpegHeaderBytes(512, 768, 1), {
        label: "spoofed.png",
        declaredMimeType: "image/png",
        strictMimeMatch: true,
        maxBytes: 1024 * 1024,
      });
      expect(mismatch.ok).toBe(false);
      if (!mismatch.ok) {
        expect(mismatch.code).toBe("INVALID_REQUEST");
      }

      const tooSmall = validateTryOnImageBuffer(buildLookbookMockPngBytes({ width: 32, height: 48 }), {
        label: "tiny.png",
        maxBytes: 1024 * 1024,
        minDimensionPx: 64,
      });
      expect(tooSmall.ok).toBe(false);
      if (!tooSmall.ok) {
        expect(tooSmall.code).toBe("INVALID_REQUEST");
      }

      const tooLargeBytes = validateTryOnImageBuffer(
        buildLookbookMockPngBytes({ width: 128, height: 128 }),
        {
          label: "oversized.png",
          maxBytes: 100, // force byte ceiling failure
        },
      );
      expect(tooLargeBytes.ok).toBe(false);
      if (!tooLargeBytes.ok) {
        expect(tooLargeBytes.code).toBe("PAYLOAD_TOO_LARGE");
      }
    });
  });

  describe("B. OpenAITryOnProvider, Prompt Engineering & Mock Schema Compliance", () => {
    it("validates supported GPT Image models and blocks retired or unverified models", () => {
      expect(validateOpenAiModelSelection("gpt-image-2").ok).toBe(true);
      expect(validateOpenAiModelSelection("gpt-image-2.6-sunburst").ok).toBe(true);
      expect(validateOpenAiModelSelection("gpt-image-1.5").ok).toBe(true);

      for (const retired of ["dall-e-2", "dall-e-3", "gpt-image-1"]) {
        const res = validateOpenAiModelSelection(retired, true);
        expect(res.ok).toBe(false);
        if (!res.ok) {
          expect(res.code).toBe("UNSUPPORTED_CAPABILITY");
        }
      }

      expect(validateOpenAiModelSelection("gpt-image-experimental-99", false).ok).toBe(false);
      expect(validateOpenAiModelSelection("gpt-image-experimental-99", true).ok).toBe(true);
    });

    it("builds versioned VTO prompt with Image 1 + Image 2 bindings, preservation rules, and zero PII", () => {
      const built = buildOpenAiTryOnPrompt({
        view: "FRONT",
        category: "TOP",
        colors: ["navy", "white"],
        personExifOrientation: 6,
      });
      expect(built.promptVersion).toBe(OPENAI_VTO_PROMPT_VERSION);
      expect(built.prompt).toContain("Image 1");
      expect(built.prompt).toContain("Image 2");
      expect(built.prompt).toContain("logo");
      expect(built.prompt).toContain("printed text");
      expect(built.prompt).toContain("identity");
      expect(built.prompt).not.toMatch(/email|phone|userId|\/home\//i);

      const rubric = createUnreviewedOpenAiRubric();
      expect(rubric.status).toBe("UNREVIEWED");
      expect(rubric.criteria.identityPreservation).toBeNull();
    });

    it("sends spec-compliant multipart POST /v1/images/edits (omitting input_fidelity on gpt-image-2*, sending high on gpt-image-1.5)", async () => {
      const mock2 = createOpenAiImageEditsMock("success");
      const provider2 = new OpenAITryOnProvider({
        apiKey: "sk-test-openai-secret-key-do-not-log-12345",
        model: "gpt-image-2.6-sunburst",
        quality: "medium",
        size: "1024x1536",
        outputFormat: "png",
        fetchImpl: mock2.fetchImpl,
      });

      const out2 = await provider2.submitView({
        jobId: "job_m2",
        requestId: "req_m2",
        view: "FRONT",
        personImage: makePngWireImage("p_front", "FRONT"),
        garments: [
          {
            itemId: "top_1",
            category: "TOP",
            colors: ["black"],
            image: makePngWireImage("g_front", "FRONT"),
          },
        ],
      });

      expect(mock2.callCount).toBe(1);
      expect(mock2.lastCapturedRequest?.imageCount).toBe(2);
      expect(mock2.lastCapturedRequest?.n).toBe("1");
      expect(mock2.lastCapturedRequest?.hasForbiddenResponseFormat).toBe(false);
      expect(mock2.lastCapturedRequest?.inputFidelity).toBeNull();
      expect(mock2.lastCapturedRequest?.promptMentionsBothReferenceImages).toBe(true);
      expect(out2.initialState).toBe("SUCCEEDED");
      expect(out2.immediateOutput?.width).toBe(192);
      expect(out2.immediateOutput?.height).toBe(256);
      expect(out2.usageReport?.estimatedCostUsd).toBeGreaterThan(0);

      // Verify gpt-image-1.5 automatically attaches input_fidelity="high"
      const mock15 = createOpenAiImageEditsMock("success");
      const provider15 = new OpenAITryOnProvider({
        apiKey: "sk-test-openai-secret-key-do-not-log-12345",
        model: "gpt-image-1.5",
        fetchImpl: mock15.fetchImpl,
      });
      await provider15.submitView({
        jobId: "job_m15",
        requestId: "req_m15",
        view: "FRONT",
        personImage: makePngWireImage("p_front", "FRONT"),
        garments: [
          {
            itemId: "top_1",
            category: "TOP",
            image: makePngWireImage("g_front", "FRONT"),
          },
        ],
      });
      expect(mock15.lastCapturedRequest?.inputFidelity).toBe("high");
    });

    it("maps quota, auth, org verification, rate-limit, corrupt AI outputs, and network timeout (0 retries)", async () => {
      const runScenario = async (scenario: Parameters<typeof createOpenAiImageEditsMock>[0]) => {
        const mock = createOpenAiImageEditsMock(scenario);
        const provider = new OpenAITryOnProvider({
          apiKey: "sk-test-super-secret-value-99999999",
          model: "gpt-image-2",
          fetchImpl: mock.fetchImpl,
        });
        try {
          await provider.submitView({
            jobId: "job_err",
            requestId: "req_err",
            view: "FRONT",
            personImage: makePngWireImage("p_front", "FRONT"),
            garments: [
              {
                itemId: "top_1",
                category: "TOP",
                image: makePngWireImage("g_front", "FRONT"),
              },
            ],
          });
          throw new Error("Expected scenario to throw");
        } catch (err) {
          return { err, callCount: mock.callCount };
        }
      };

      const quota = await runScenario("insufficient_quota");
      expect(quota.err).toBeInstanceOf(UpstreamDeterministicError);
      expect((quota.err as UpstreamDeterministicError).code).toBe("INSUFFICIENT_QUOTA");
      expect((quota.err as UpstreamDeterministicError).retryable).toBe(false);
      expect(quota.callCount).toBe(1);

      const auth = await runScenario("invalid_api_key");
      expect((auth.err as UpstreamDeterministicError).code).toBe("UNAUTHORIZED");
      expect((auth.err as Error).message).not.toContain("sk-test-super-secret");

      const org = await runScenario("org_not_verified");
      expect((org.err as UpstreamDeterministicError).code).toBe("FORBIDDEN");
      expect((org.err as Error).message).toContain("Organization Verification");

      const rl = await runScenario("rate_limit");
      expect((rl.err as UpstreamDeterministicError).code).toBe("RATE_LIMITED");
      expect((rl.err as UpstreamDeterministicError).retryable).toBe(true);

      const notFound = await runScenario("model_not_found");
      expect((notFound.err as UpstreamDeterministicError).code).toBe("UNSUPPORTED_CAPABILITY");

      const serverErr = await runScenario("server_error");
      expect((serverErr.err as UpstreamDeterministicError).code).toBe("UPSTREAM_ERROR");
      expect((serverErr.err as UpstreamDeterministicError).retryable).toBe(false);

      for (const badOut of [
        "empty_data",
        "corrupt_base64",
        "corrupt_image_bytes",
        "url_only_unexpected",
      ] as const) {
        const res = await runScenario(badOut);
        expect((res.err as UpstreamDeterministicError).code).toBe("INVALID_AI_OUTPUT");
      }

      const timeout = await runScenario("network_timeout");
      expect(timeout.err).toBeInstanceOf(UpstreamAmbiguousTimeoutError);
      // Critical billing rule: NEVER retry automatically when network drops post-dispatch!
      expect(timeout.callCount).toBe(1);

      expect(
        redactSecretsFromDiagnostic(
          "Error with Bearer sk-proj-1234567890abcdef and key sk-abcdef1234567890",
        ),
      ).not.toContain("sk-");
      expect(estimateOpenAiEditMaxUsd({ model: "gpt-image-2", quality: "medium" })).toBe(0.06);
      expect(
        computeOpenAiUsageUsd(
          "gpt-image-2",
          {
            input_tokens: 2000,
            output_tokens: 4000,
            input_tokens_details: { text_tokens: 200, image_tokens: 1800 },
          },
          0.06,
        ),
      ).toBeCloseTo(0.0677, 4);
    });

    it("times out cleanly without retry when OpenAI mock returns headers then hangs while streaming body", async () => {
      let networkCalls = 0;
      const provider = new OpenAITryOnProvider({
        apiKey: "sk-test-super-secret-body-hang",
        model: "gpt-image-2",
        timeoutMs: 250,
        fetchImpl: async () => {
          networkCalls += 1;
          const encoder = new TextEncoder();
          const stream = new ReadableStream<Uint8Array>({
            start(controller) {
              controller.enqueue(encoder.encode('{"data":[{"b64_json":"'));
              // Keep stream open forever to simulate body hang after headers arrive
            },
          });
          return new Response(stream, {
            status: 200,
            headers: { "content-type": "application/json", "x-request-id": "req_hang_openai" },
          });
        },
      });

      const startedAt = Date.now();
      await expect(
        provider.submitView({
          jobId: "job_openai_body_hang",
          requestId: "req_openai_body_hang",
          view: "FRONT",
          personImage: makePngWireImage("p_front", "FRONT"),
          garments: [
            {
              itemId: "top_1",
              category: "TOP",
              image: makePngWireImage("g_front", "FRONT"),
            },
          ],
        }),
      ).rejects.toMatchObject({
        name: "UpstreamAmbiguousTimeoutError",
        retryable: false,
      });
      const elapsedMs = Date.now() - startedAt;

      expect(elapsedMs).toBeLessThan(1_500);
      expect(networkCalls).toBe(1);
      expect(provider.submitCallCount).toBe(1);
    });
  });

  describe("C & D. Router LIVE Gates, Cost Guard, Durable Ledger, Offline Mock E2E & Privacy", () => {
    it("keeps OpenAI LIVE hard-locked by default and validates preflight & secret-free logs", async () => {
      const logs: SafeTryOnLogEntry[] = [];
      const store = new InMemoryTryOnJobStore({ simulateDurableForTests: true });
      const router = createTryOnRouter({ store, logger: (e) => logs.push(e) });

      // 1. Default TRYON_PROVIDER=openai with TRYON_LIVE_ENABLED=false -> 503 FORBIDDEN
      const defaultEnv: TryOnEnvConfig = {
        TRYON_PROVIDER: "openai",
        TRYON_LIVE_ENABLED: "false",
        VTO_PROVIDER_CONFIG_VERSION: "v2-openai-poc-1",
      };
      const blockedRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_1" },
          body: JSON.stringify(makeValidOpenAiSubmitRequest()),
        }),
        defaultEnv,
      );
      expect(blockedRes.status).toBe(503);
      const blockedErr = (await blockedRes.json()) as TryOnErrorEnvelope;
      expect(blockedErr.error.code).toBe("FORBIDDEN");

      // 2. LIVE enabled without API key -> UNAUTHORIZED (503)
      const missingKeyRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_1" },
          body: JSON.stringify(makeValidOpenAiSubmitRequest()),
        }),
        {
          ...defaultEnv,
          TRYON_LIVE_ENABLED: "true",
          TRYON_BUDGET_CONFIRMED: "true",
          TRYON_LIVE_MAX_ESTIMATED_USD: "1.00",
        },
      );
      expect(missingKeyRes.status).toBe(503);
      expect(((await missingKeyRes.json()) as TryOnErrorEnvelope).error.code).toBe("UNAUTHORIZED");

      // 3. LIVE enabled with key, but budget NOT confirmed -> FORBIDDEN (403)
      const unconfirmedBudgetRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_1" },
          body: JSON.stringify(makeValidOpenAiSubmitRequest()),
        }),
        {
          ...defaultEnv,
          TRYON_LIVE_ENABLED: "true",
          OPENAI_API_KEY: "sk-proj-secret-not-logged-987654321",
          TRYON_BUDGET_CONFIRMED: "false",
          TRYON_LIVE_MAX_ESTIMATED_USD: "1.00",
        },
      );
      expect(unconfirmedBudgetRes.status).toBe(403);
      expect(((await unconfirmedBudgetRes.json()) as TryOnErrorEnvelope).error.code).toBe(
        "FORBIDDEN",
      );

      // 4. Unpriced custom model blocked unless TRYON_ALLOW_UNPRICED_MODEL=true
      const unpricedRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_1" },
          body: JSON.stringify(makeValidOpenAiSubmitRequest()),
        }),
        {
          ...defaultEnv,
          TRYON_LIVE_ENABLED: "true",
          OPENAI_API_KEY: "sk-proj-secret-not-logged-987654321",
          OPENAI_IMAGE_MODEL: "gpt-image-custom-preview",
          OPENAI_ALLOW_CUSTOM_MODEL: "true",
          TRYON_BUDGET_CONFIRMED: "true",
          TRYON_LIVE_MAX_ESTIMATED_USD: "1.00",
          TRYON_ALLOW_UNPRICED_MODEL: "false",
        },
      );
      expect(unpricedRes.status).toBe(403);
      expect(((await unpricedRes.json()) as TryOnErrorEnvelope).error.code).toBe("FORBIDDEN");

      // 5. Non-durable store blocked in LIVE mode
      const ephemeralRouter = createTryOnRouter({ store: new InMemoryTryOnJobStore() });
      const nonDurableRes = await ephemeralRouter.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_1" },
          body: JSON.stringify(makeValidOpenAiSubmitRequest()),
        }),
        {
          ...defaultEnv,
          TRYON_LIVE_ENABLED: "true",
          OPENAI_API_KEY: "sk-proj-secret-not-logged-987654321",
          OPENAI_IMAGE_MODEL: "gpt-image-2",
          TRYON_BUDGET_CONFIRMED: "true",
          TRYON_LIVE_MAX_ESTIMATED_USD: "1.00",
        },
      );
      expect(nonDurableRes.status).toBe(503);
      expect(((await nonDurableRes.json()) as TryOnErrorEnvelope).error.code).toBe(
        "UNSUPPORTED_CAPABILITY",
      );

      // 6. Preflight endpoint reports blockers accurately and NEVER leaks OPENAI_API_KEY
      const preflightRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/preflight", {
          method: "GET",
          headers: { "x-session-id": "sess_1" },
        }),
        {
          ...defaultEnv,
          TRYON_LIVE_ENABLED: "false",
          OPENAI_API_KEY: "sk-proj-secret-not-logged-987654321",
          OPENAI_IMAGE_MODEL: "gpt-image-2",
        },
      );
      expect(preflightRes.status).toBe(200);
      const preflightText = await preflightRes.text();
      expect(preflightText).not.toContain("sk-proj-secret");
      const preflightJson = JSON.parse(preflightText) as {
        readyForLivePocCall: boolean;
        blockers: string[];
      };
      expect(preflightJson.readyForLivePocCall).toBe(false);
      expect(preflightJson.blockers.some((b) => b.includes("LIVE_GATE_LOCKED"))).toBe(true);

      const serializedLogs = JSON.stringify(logs);
      expect(serializedLogs).not.toContain("sk-proj-secret");
      expect(serializedLogs).not.toContain("base64Data");
    });

    it("executes full Offline OpenAI Mock E2E flow with FAKE labeling, provider-bound consent, and FRONT_ONLY gate", async () => {
      const store = new InMemoryTryOnJobStore({ simulateDurableForTests: true });
      const router = createTryOnRouter({ store });
      const offlineMockEnv: TryOnEnvConfig = {
        TRYON_PROVIDER: "openai",
        TRYON_LIVE_ENABLED: "false",
        TRYON_OFFLINE_MOCK_OPENAI: "true",
        OPENAI_IMAGE_MODEL: "gpt-image-2",
        VTO_FEASIBILITY_DECISION: "GO_FRONT_ONLY",
        VTO_PROVIDER_CONFIG_VERSION: "v2-openai-poc-1",
      };

      // Rejects consent bound to wrong provider ("fashn" instead of "openai")
      const wrongProviderReq = makeValidOpenAiSubmitRequest({
        consentGrant: {
          ...makeValidOpenAiSubmitRequest().consentGrant,
          providerId: "fashn",
        },
      });
      const wrongProviderRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_mock" },
          body: JSON.stringify(wrongProviderReq),
        }),
        offlineMockEnv,
      );
      expect(wrongProviderRes.status).toBe(403);
      expect(((await wrongProviderRes.json()) as TryOnErrorEnvelope).error.code).toBe(
        "CONSENT_REQUIRED",
      );

      // Rejects missing person front or missing garment front
      const missingGarmentReq = makeValidOpenAiSubmitRequest({
        garmentImages: {},
      });
      const missingGarmentRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_mock" },
          body: JSON.stringify(missingGarmentReq),
        }),
        offlineMockEnv,
      );
      expect(missingGarmentRes.status).toBe(400);
      expect(((await missingGarmentRes.json()) as TryOnErrorEnvelope).error.code).toBe(
        "NEEDS_INPUT",
      );

      // Rejects BACK view when GO_FRONT_ONLY
      const backViewReq = makeValidOpenAiSubmitRequest({
        idempotencyKey: "idem_back_blocked",
        requestedViews: ["FRONT", "BACK"],
      });
      const backViewRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_mock" },
          body: JSON.stringify(backViewReq),
        }),
        offlineMockEnv,
      );
      expect(backViewRes.status).toBe(400);
      expect(((await backViewRes.json()) as TryOnErrorEnvelope).error.code).toBe(
        "UNSUPPORTED_CAPABILITY",
      );

      // Succeeds end-to-end for FRONT view in Offline OpenAI Mock Mode
      const submitRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_mock" },
          body: JSON.stringify(makeValidOpenAiSubmitRequest()),
        }),
        offlineMockEnv,
      );
      expect(submitRes.status).toBe(202);
      const submitJson = (await submitRes.json()) as TryOnJobSubmitResponse;
      expect(submitJson.status).toBe("SUCCEEDED");

      const pollRes = await router.handle(
        new Request(`https://proxy.fitcheck.local/v2/tryon/jobs/${submitJson.jobId}`, {
          method: "GET",
          headers: { "x-session-id": "sess_mock" },
        }),
        offlineMockEnv,
      );
      expect(pollRes.status).toBe(200);
      const resultJson = (await pollRes.json()) as TryOnResultWire;
      expect(resultJson.source).toBe("FAKE");
      expect(resultJson.remoteDeletionState).toBe("NOT_UPLOADED");
      expect(resultJson.warnings.some((w) => w.includes("OFFLINE_MOCK_OPENAI"))).toBe(true);

      const frontOutput = resultJson.views.FRONT?.output;
      expect(frontOutput).toBeTruthy();
      const decodedPng = decodeStrictBase64(frontOutput!.base64Data);
      const meta = inspectImageBytes(decodedPng!);
      expect(meta?.mimeType).toBe("image/png");
      expect(meta?.effectiveWidth).toBe(192);
      expect(meta?.effectiveHeight).toBe(256);
    });

    it("enforces TRYON_LIVE_MAX_REQUESTS=1, idempotency, JsonLedgerTryOnJobStore restart persistence, and honest OpenAI delete disclosure", async () => {
      let persistedLedgerJson: string | null = null;
      const ledgerBackend = {
        read: () => persistedLedgerJson,
        write: (json: string) => {
          persistedLedgerJson = json;
        },
      };

      const durableStore = new JsonLedgerTryOnJobStore(ledgerBackend);
      const mock = createOpenAiImageEditsMock("success");
      const simulatedLiveProvider = new OpenAITryOnProvider({
        apiKey: "sk-simulated-live-unit-test-only",
        model: "gpt-image-2",
        quality: "medium",
        offlineMockMode: false, // exercises AI_LIVE spend & delete semantics locally against mock fetchImpl!
        fetchImpl: mock.fetchImpl,
      });

      const router = createTryOnRouter({
        store: durableStore,
        provider: simulatedLiveProvider,
      });

      const liveEnv: TryOnEnvConfig = {
        TRYON_PROVIDER: "openai",
        TRYON_LIVE_ENABLED: "true",
        OPENAI_API_KEY: "sk-simulated-live-unit-test-only",
        OPENAI_IMAGE_MODEL: "gpt-image-2",
        OPENAI_IMAGE_QUALITY: "medium",
        TRYON_BUDGET_CONFIRMED: "true",
        TRYON_LIVE_MAX_REQUESTS: "1",
        TRYON_LIVE_MAX_ESTIMATED_USD: "0.20",
        VTO_FEASIBILITY_DECISION: "GO_FRONT_ONLY",
        VTO_PROVIDER_CONFIG_VERSION: "v2-openai-poc-1",
      };

      const req1 = makeValidOpenAiSubmitRequest({ idempotencyKey: "idem_live_1" });
      const res1 = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_live_1" },
          body: JSON.stringify(req1),
        }),
        liveEnv,
      );
      expect(res1.status).toBe(202);
      const submit1 = (await res1.json()) as TryOnJobSubmitResponse;
      expect(submit1.status).toBe("SUCCEEDED");
      expect(mock.callCount).toBe(1);

      // Idempotent replay with same idempotencyKey MUST NOT call OpenAI again
      const replayRes = await router.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_live_1" },
          body: JSON.stringify(req1),
        }),
        liveEnv,
      );
      expect(replayRes.status).toBe(202);
      expect(((await replayRes.json()) as TryOnJobSubmitResponse).jobId).toBe(submit1.jobId);
      expect(mock.callCount).toBe(1);

      // Simulate server restart by instantiating a brand-new JsonLedgerTryOnJobStore over the same persisted JSON
      const reloadedStore = new JsonLedgerTryOnJobStore(ledgerBackend);
      const reloadedRouter = createTryOnRouter({
        store: reloadedStore,
        provider: simulatedLiveProvider,
      });

      // Second distinct LIVE request MUST be blocked because TRYON_LIVE_MAX_REQUESTS=1 was persisted!
      const req2 = makeValidOpenAiSubmitRequest({
        requestId: "req_openai_002",
        idempotencyKey: "idem_live_2",
      });
      const res2 = await reloadedRouter.handle(
        new Request("https://proxy.fitcheck.local/v2/tryon/jobs", {
          method: "POST",
          headers: { "x-session-id": "sess_live_2" },
          body: JSON.stringify(req2),
        }),
        liveEnv,
      );
      expect(res2.status).toBe(503);
      const err2 = (await res2.json()) as TryOnErrorEnvelope;
      expect(err2.error.code).toBe("BUDGET_EXCEEDED");
      expect(mock.callCount).toBe(1);

      // Delete job #1 -> wipes FitCheck server output and honestly reports UNSUPPORTED remote delete for OpenAI
      const delRes = await reloadedRouter.handle(
        new Request(`https://proxy.fitcheck.local/v2/tryon/jobs/${submit1.jobId}`, {
          method: "DELETE",
          headers: { "x-session-id": "sess_live_1" },
        }),
        liveEnv,
      );
      expect(delRes.status).toBe(200);
      const receipt = (await delRes.json()) as DeletionReceiptWire;
      expect(receipt.status).toBe("DELETED");
      expect(receipt.localOutputsDeleted).toBe(true);
      expect(receipt.remoteDeletionState).toBe("UNSUPPORTED");
      expect(receipt.message).toContain("Data Retention");
    });
  });
});
