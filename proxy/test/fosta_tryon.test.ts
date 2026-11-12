import { describe, expect, it } from "vitest";
import { createFostaResponsesMock } from "../src/tryon/fosta_mock";
import {
  buildFostaTryOnPrompt,
  FOSTA_EXPERIMENTAL_CAPABILITIES,
  FOSTA_VTO_PROMPT_VERSION,
} from "../src/tryon/fosta_prompt";
import {
  buildFostaResponsesPayload,
  DEFAULT_FOSTA_ENDPOINT,
  DEFAULT_FOSTA_MODEL,
  FostaTryOnProvider,
} from "../src/tryon/fosta_provider";
import { createTryOnRouter } from "../src/tryon/handler";
import {
  buildLookbookMockPngBytes,
  buildSyntheticJpegHeaderBytes,
  buildSyntheticWebpHeaderBytes,
  decodeStrictBase64,
  encodeBase64,
  inspectImageBytes,
} from "../src/tryon/image_pipeline";
import {
  UpstreamAmbiguousTimeoutError,
  UpstreamDeterministicError,
} from "../src/tryon/providers";
import { InMemoryTryOnJobStore, JsonLedgerTryOnJobStore } from "../src/tryon/store";
import type {
  DeletionReceiptWire,
  FostaGarmentKind,
  ImageView,
  SafeTryOnLogEntry,
  TryOnEnvConfig,
  TryOnErrorEnvelope,
  TryOnJobSubmitResponse,
  TryOnPresentationMode,
  TryOnResultWire,
  TryOnSubmitWireRequest,
  WireImageInput,
} from "../src/tryon/types";

function makePngWireImage(
  mediaId: string,
  view: ImageView,
  garmentRgb: [number, number, number] = [52, 86, 130],
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

function makeMemoryBackedDurableStore(): JsonLedgerTryOnJobStore {
  let ledgerJson: string | null = null;
  return new JsonLedgerTryOnJobStore({
    read: () => ledgerJson,
    write: (next) => {
      ledgerJson = next;
    },
  });
}

function makeFostaSubmitRequest(
  overrides: Partial<TryOnSubmitWireRequest> = {},
): TryOnSubmitWireRequest {
  const base: TryOnSubmitWireRequest = {
    schemaVersion: 2,
    requestId: "req_fosta_001",
    idempotencyKey: "idem_fosta_001",
    inputGeneration: 1,
    profileRevision: 1,
    presentationMode: "ORIGINAL_PHOTO",
    outfitSnapshot: {
      outfitId: "outfit_fosta_1",
      wardrobeSnapshotId: "ward_snap_fosta_1",
      items: [
        {
          itemId: "item_top_1",
          category: "TOP",
          revision: 1,
          colors: ["NAVY"],
        },
        {
          itemId: "item_bottom_1",
          category: "BOTTOM",
          revision: 1,
          colors: ["BEIGE"],
        },
        {
          itemId: "item_shoes_1",
          category: "SHOES",
          revision: 1,
          colors: ["BROWN"],
        },
      ],
    },
    selectedItemIds: ["item_top_1"],
    requestedViews: ["FRONT"],
    consentGrant: {
      id: "consent_fosta_1",
      purpose: "TRYON",
      providerId: "fosta",
      providerConfigVersion: "v2-fosta-poc-1",
      confirmedByUser: true,
      issuedAtEpochMillis: 1_762_900_000_000,
      allowedViews: ["FRONT"],
      selectedMediaIds: ["person_front_1", "top_front_1"],
      requestGeneration: 1,
      revokedAtEpochMillis: null,
    },
    providerConfigVersion: "v2-fosta-poc-1",
    personImages: {
      front: makePngWireImage("person_front_1", "FRONT", [190, 165, 140]),
    },
    garmentImages: {
      item_top_1: {
        front: makePngWireImage("top_front_1", "FRONT", [36, 62, 108]),
      },
    },
  };
  return { ...base, ...overrides };
}

describe("FOSTA Luna Prompt Matrix — All 12 (Mode x View x Garment) Combinations", () => {
  const modes: TryOnPresentationMode[] = ["ORIGINAL_PHOTO", "STUDIO_WHITE"];
  const views: ImageView[] = ["FRONT", "BACK"];
  const combos: Array<{ label: string; kinds: FostaGarmentKind[] }> = [
    { label: "TOP", kinds: ["TOP"] },
    { label: "BOTTOM", kinds: ["BOTTOM"] },
    { label: "TOP+BOTTOM", kinds: ["TOP", "BOTTOM"] },
  ];

  for (const mode of modes) {
    for (const view of views) {
      for (const combo of combos) {
        it(`builds tailored prompt for ${mode} + ${view} + ${combo.label} with untouched-clothing protection`, () => {
          const prompt = buildFostaTryOnPrompt({
            mode,
            view,
            garmentKinds: combo.kinds,
          });

          expect(prompt).toContain(`FITCHECK_VTO_PROMPT_VERSION=${FOSTA_VTO_PROMPT_VERSION}`);
          expect(prompt).toContain("REFERENCE IMAGE 1 is the base PERSON photograph.");
          expect(prompt).toContain(`MODE=${mode}.`);
          expect(prompt).toContain(`VIEW=${view}.`);

          if (combo.label === "TOP") {
            expect(prompt).toContain("REFERENCE IMAGE 2 is the TOP garment.");
            expect(prompt).toContain(
              "Leave the existing lower garment completely unchanged",
            );
            expect(prompt).not.toContain("Apply BOTH referenced garments");
          } else if (combo.label === "BOTTOM") {
            expect(prompt).toContain("REFERENCE IMAGE 2 is the BOTTOM garment.");
            expect(prompt).toContain(
              "Leave the existing top completely unchanged",
            );
            expect(prompt).not.toContain("Apply BOTH referenced garments");
          } else {
            expect(prompt).toContain("REFERENCE IMAGE 2 is the TOP garment.");
            expect(prompt).toContain("REFERENCE IMAGE 3 is the BOTTOM garment.");
            expect(prompt).toContain(
              "Apply BOTH referenced garments together in the SAME generated output image.",
            );
            expect(prompt).toContain("Ensure realistic layering at the waist.");
          }

          if (view === "BACK") {
            expect(prompt).toContain("Show the same person from BEHIND");
            expect(prompt).toContain("back-specific prints");
            expect(prompt).toContain("Do not rotate the person to face the camera");
            expect(prompt).toContain("copy a front logo onto the back");
          } else {
            expect(prompt).toContain("Show the person from the front");
            expect(prompt).toContain("Do not invent a rear view.");
          }

          if (mode === "STUDIO_WHITE") {
            expect(prompt).toContain("upright full-body standing pose");
            expect(prompt).toContain("clean seamless white/ivory backdrop");
            expect(prompt).toContain(
              "not a body measurement or reliable physical fit prediction",
            );
            if (view === "BACK") {
              expect(prompt).toContain(
                "For this studio REAR view, the subject must face AWAY from the camera",
              );
            }
          } else {
            expect(prompt).toContain(
              "Keep the original person photograph as the base composition",
            );
          }
        });
      }
    }
  }
});

describe("FOSTA Payload Assembly, Provider Safety & Error Handling", () => {
  const samplePersonJpeg = buildSyntheticJpegHeaderBytes(768, 1024, 1);
  const sampleTopWebp = buildSyntheticWebpHeaderBytes(768, 1024);
  const sampleBottomPng = buildLookbookMockPngBytes({ width: 128, height: 160 });

  it("assembles Responses API JSON with gpt-6-luna, forced image_generation tool, and deterministic PERSON->TOP->BOTTOM order", () => {
    const frontTop = buildFostaResponsesPayload({
      mode: "ORIGINAL_PHOTO",
      view: "FRONT",
      personBytes: samplePersonJpeg,
      topBytes: sampleTopWebp,
    });
    expect(frontTop.model).toBe(DEFAULT_FOSTA_MODEL);
    expect(frontTop.tools).toEqual([{ type: "image_generation" }]);
    expect(frontTop.tool_choice).toEqual({ type: "image_generation" });
    expect(frontTop.input[0]?.content).toHaveLength(3);
    expect(frontTop.input[0]?.content[1]).toMatchObject({
      type: "input_image",
      image_url: expect.stringMatching(/^data:image\/jpeg;base64,/),
    });
    expect(frontTop.input[0]?.content[2]).toMatchObject({
      type: "input_image",
      image_url: expect.stringMatching(/^data:image\/webp;base64,/),
    });

    const studioBackCombo = buildFostaResponsesPayload({
      mode: "STUDIO_WHITE",
      view: "BACK",
      personBytes: samplePersonJpeg,
      topBytes: sampleTopWebp,
      bottomBytes: sampleBottomPng,
    });
    expect(studioBackCombo.input[0]?.content).toHaveLength(4);
    expect((studioBackCombo.input[0]?.content[0] as { text: string }).text).toContain(
      "MODE=STUDIO_WHITE",
    );
    expect((studioBackCombo.input[0]?.content[0] as { text: string }).text).toContain(
      "VIEW=BACK",
    );
    expect(studioBackCombo.input[0]?.content[2]).toMatchObject({
      type: "input_image",
      image_url: expect.stringMatching(/^data:image\/webp;base64,/),
    });
    expect(studioBackCombo.input[0]?.content[3]).toMatchObject({
      type: "input_image",
      image_url: expect.stringMatching(/^data:image\/png;base64,/),
    });
  });

  it("keeps FOSTA LIVE blocked unless both approvedLive and approvedQuotaUse are true (0 network calls)", () => {
    expect(
      () =>
        new FostaTryOnProvider({
          apiKey: "dummy-fosta-key",
          approvedLive: false,
          approvedQuotaUse: true,
        }),
    ).toThrow(UpstreamDeterministicError);

    expect(
      () =>
        new FostaTryOnProvider({
          apiKey: "dummy-fosta-key",
          approvedLive: true,
          approvedQuotaUse: false,
        }),
    ).toThrow(UpstreamDeterministicError);
  });

  it("orders TOP before BOTTOM even when caller submits [BOTTOM, TOP] and rejects duplicate TOP+TOP", async () => {
    const mock = createFostaResponsesMock("success");
    const provider = new FostaTryOnProvider({
      apiKey: "mock-key",
      endpoint: "https://mock.fosta.local/v1/responses",
      offlineMockMode: true,
      fetchImpl: mock.fetchImpl,
    });

    const personImg = makePngWireImage("p_front", "FRONT");
    const topImg = makePngWireImage("g_top", "FRONT", [180, 40, 40]);
    const bottomImg = makePngWireImage("g_bottom", "FRONT", [30, 50, 120]);

    const res = await provider.submitView({
      jobId: "job_order_1",
      requestId: "req_order_1",
      view: "FRONT",
      presentationMode: "STUDIO_WHITE",
      personImage: personImg,
      // Deliberately pass BOTTOM first, TOP second
      garments: [
        { itemId: "item_bottom", category: "BOTTOM", image: bottomImg },
        { itemId: "item_top", category: "TOP", image: topImg },
      ],
    });

    expect(res.initialState).toBe("SUCCEEDED");
    expect(mock.lastCapturedRequest?.imageCount).toBe(3);
    expect(mock.lastCapturedRequest?.detectedMode).toBe("STUDIO_WHITE");
    expect(mock.lastCapturedRequest?.prompt).toContain(
      "REFERENCE IMAGE 2 is the TOP garment.",
    );
    expect(mock.lastCapturedRequest?.prompt).toContain(
      "REFERENCE IMAGE 3 is the BOTTOM garment.",
    );

    // Duplicate TOP + TOP must be rejected before network call
    const beforeCalls = mock.callCount;
    await expect(
      provider.submitView({
        jobId: "job_dup_top",
        requestId: "req_dup_top",
        view: "FRONT",
        personImage: personImg,
        garments: [
          { itemId: "top_a", category: "TOP", image: topImg },
          { itemId: "top_b", category: "TOP", image: topImg },
        ],
      }),
    ).rejects.toThrow(/tối đa 1 TOP và 1 BOTTOM/);
    expect(mock.callCount).toBe(beforeCalls);
  });

  it("never retries on network timeout (UNKNOWN_SUBMISSION) and redacts upstream bodies/keys on HTTP errors", async () => {
    let networkCalls = 0;
    const timeoutProvider = new FostaTryOnProvider({
      apiKey: "super-secret-fosta-key-999",
      endpoint: DEFAULT_FOSTA_ENDPOINT,
      approvedLive: true,
      approvedQuotaUse: true,
      fetchImpl: async () => {
        networkCalls += 1;
        throw new Error("socket hang up");
      },
    });

    await expect(
      timeoutProvider.submitView({
        jobId: "job_timeout",
        requestId: "req_timeout",
        view: "FRONT",
        personImage: makePngWireImage("pf", "FRONT"),
        garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
      }),
    ).rejects.toThrow(UpstreamAmbiguousTimeoutError);
    expect(networkCalls).toBe(1);

    const rateLimitedProvider = new FostaTryOnProvider({
      apiKey: "super-secret-fosta-key-999",
      approvedLive: true,
      approvedQuotaUse: true,
      fetchImpl: async () =>
        new Response("Upstream body echoing super-secret-fosta-key-999", { status: 429 }),
    });

    try {
      await rateLimitedProvider.submitView({
        jobId: "job_429",
        requestId: "req_429",
        view: "FRONT",
        personImage: makePngWireImage("pf", "FRONT"),
        garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
      });
      expect.unreachable("Should throw RATE_LIMITED");
    } catch (err) {
      expect(err).toBeInstanceOf(UpstreamDeterministicError);
      expect((err as UpstreamDeterministicError).code).toBe("RATE_LIMITED");
      expect((err as Error).message).not.toContain("super-secret-fosta-key-999");
    }
  });

  it("rejects HTTP 200 incomplete responses, missing image_generation_call, and corrupt base64 outputs", async () => {
    for (const scenario of [
      "incomplete_response",
      "no_image_output",
      "corrupt_base64",
      "corrupt_image_bytes",
    ] as const) {
      const mock = createFostaResponsesMock(scenario);
      const provider = new FostaTryOnProvider({
        apiKey: "mock-key",
        endpoint: "https://mock.fosta.local/v1/responses",
        offlineMockMode: true,
        fetchImpl: mock.fetchImpl,
      });

      await expect(
        provider.submitView({
          jobId: `job_${scenario}`,
          requestId: `req_${scenario}`,
          view: "FRONT",
          personImage: makePngWireImage("pf", "FRONT"),
          garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
        }),
      ).rejects.toMatchObject({ code: "INVALID_AI_OUTPUT" });
    }

    expect(FOSTA_EXPERIMENTAL_CAPABILITIES.frontTop).toBe("POC_VALIDATED_SINGLE_EXAMPLE");
    expect(FOSTA_EXPERIMENTAL_CAPABILITIES.studioTop).toBe("POC_VALIDATED_SINGLE_EXAMPLE");
    expect(FOSTA_EXPERIMENTAL_CAPABILITIES.back).toBe("UNVALIDATED_EXPERIMENTAL");
    expect(FOSTA_EXPERIMENTAL_CAPABILITIES.bottomOnly).toBe("UNVALIDATED_EXPERIMENTAL");
    expect(FOSTA_EXPERIMENTAL_CAPABILITIES.topBottom).toBe("UNVALIDATED_EXPERIMENTAL");
  });
});

describe("/v2/tryon Router — FOSTA Dual-Provider Isolation, Consent, Rear Photos & Quota Cap", () => {
  it("blocks FOSTA by default when TRYON_LIVE_ENABLED=false and never falls back to OpenAI", async () => {
    const store = makeMemoryBackedDurableStore();
    const logs: SafeTryOnLogEntry[] = [];
    const router = createTryOnRouter({ store, logger: (entry) => logs.push(entry) });

    const envLocked: TryOnEnvConfig = {
      TRYON_PROVIDER: "fosta",
      TRYON_LIVE_ENABLED: "false",
      FOSTA_API_KEY: "present-but-locked-key",
      OPENAI_API_KEY: "openai-key-must-never-be-used",
      VTO_PROVIDER_CONFIG_VERSION: "v2-fosta-poc-1",
    };

    const preflightRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/preflight"),
      envLocked,
      { sessionId: "sess_1" },
    );
    const preflight = (await preflightRes.json()) as {
      provider: string;
      readyForLivePocCall: boolean;
      blockers: string[];
    };
    expect(preflight.provider).toBe("fosta");
    expect(preflight.readyForLivePocCall).toBe(false);
    expect(preflight.blockers.some((b) => b.startsWith("LIVE_GATE_LOCKED"))).toBe(true);

    const submitRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(makeFostaSubmitRequest()),
      }),
      envLocked,
      { sessionId: "sess_1" },
    );
    expect(submitRes.status).toBe(503);
    const errBody = (await submitRes.json()) as TryOnErrorEnvelope;
    expect(errBody.error.code).toBe("FORBIDDEN");
    expect(JSON.stringify(logs)).not.toContain("present-but-locked-key");
  });

  it("enforces consent bound to providerId='fosta' and requires genuine rear photos for BACK", async () => {
    const store = new InMemoryTryOnJobStore();
    const router = createTryOnRouter({ store });
    const mockEnv: TryOnEnvConfig = {
      TRYON_PROVIDER: "fosta",
      TRYON_LIVE_ENABLED: "false",
      TRYON_OFFLINE_MOCK_FOSTA: "true",
      VTO_PROVIDER_CONFIG_VERSION: "v2-fosta-poc-1",
    };

    // 1. Consent bound to "openai" MUST be rejected when provider is "fosta"
    const wrongProviderConsent = makeFostaSubmitRequest({
      consentGrant: {
        ...makeFostaSubmitRequest().consentGrant,
        providerId: "openai",
      },
    });
    const resWrongConsent = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(wrongProviderConsent),
      }),
      mockEnv,
      { sessionId: "sess_consent" },
    );
    expect(resWrongConsent.status).toBe(403);
    expect(((await resWrongConsent.json()) as TryOnErrorEnvelope).error.code).toBe(
      "CONSENT_REQUIRED",
    );

    // 2. Requesting BACK without rear garment photo MUST fail with NEEDS_INPUT
    const missingGarmentBack = makeFostaSubmitRequest({
      idempotencyKey: "idem_back_missing",
      requestedViews: ["BACK"],
      personImages: {
        back: makePngWireImage("person_back_real", "BACK", [150, 140, 130]),
      },
      garmentImages: {
        item_top_1: {
          back: null,
        },
      },
      consentGrant: {
        ...makeFostaSubmitRequest().consentGrant,
        allowedViews: ["BACK"],
        selectedMediaIds: ["person_back_real", "top_back_real"],
      },
    });
    const resMissingBack = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(missingGarmentBack),
      }),
      mockEnv,
      { sessionId: "sess_consent" },
    );
    expect(resMissingBack.status).toBe(400);
    expect(((await resMissingBack.json()) as TryOnErrorEnvelope).error.code).toBe("NEEDS_INPUT");

    // 3. Requesting BACK with genuine rear person + rear garment succeeds in mock with EXPERIMENTAL warning
    const validBackOnly = makeFostaSubmitRequest({
      idempotencyKey: "idem_back_valid",
      presentationMode: "STUDIO_WHITE",
      requestedViews: ["BACK"],
      selectedItemIds: ["item_top_1", "item_bottom_1"],
      personImages: {
        back: makePngWireImage("person_back_real", "BACK", [150, 140, 130]),
      },
      garmentImages: {
        item_top_1: {
          back: makePngWireImage("top_back_real", "BACK", [36, 62, 108]),
        },
        item_bottom_1: {
          back: makePngWireImage("bottom_back_real", "BACK", [70, 68, 64]),
        },
      },
      consentGrant: {
        ...makeFostaSubmitRequest().consentGrant,
        allowedViews: ["BACK"],
        selectedMediaIds: ["person_back_real", "top_back_real", "bottom_back_real"],
      },
    });
    const submitBackRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(validBackOnly),
      }),
      mockEnv,
      { sessionId: "sess_consent" },
    );
    expect(submitBackRes.status).toBe(202);
    const submitBackJson = (await submitBackRes.json()) as TryOnJobSubmitResponse;
    expect(submitBackJson.status).toBe("SUCCEEDED");

    const pollRes = await router.handle(
      new Request(`https://fitcheck.local/v2/tryon/jobs/${submitBackJson.jobId}`),
      mockEnv,
      { sessionId: "sess_consent" },
    );
    const result = (await pollRes.json()) as TryOnResultWire;
    expect(result.source).toBe("FAKE");
    expect(result.providerId).toBe("fosta");
    expect(result.presentationMode).toBe("STUDIO_WHITE");
    expect(result.warnings.some((w) => w.startsWith("OFFLINE_MOCK_FOSTA:"))).toBe(true);
    expect(result.warnings.some((w) => w.startsWith("EXPERIMENTAL_UNVALIDATED_BACK:"))).toBe(true);
    expect(result.warnings.some((w) => w.startsWith("EXPERIMENTAL_UNVALIDATED_TOP_BOTTOM:"))).toBe(
      true,
    );
    expect(result.warnings.some((w) => w.startsWith("STUDIO_WHITE_REPOSING_WARNING:"))).toBe(true);

    const decodedOut = decodeStrictBase64(result.views.BACK?.output?.base64Data ?? "");
    expect(decodedOut).not.toBeNull();
    expect(inspectImageBytes(decodedOut!)?.mimeType).toBe("image/png");
  });

  it("enforces FOSTA_APPROVE_QUOTA and TRYON_LIVE_MAX_REQUESTS=1 durable ledger cap in LIVE mode, then cleans up on DELETE", async () => {
    const durableStore = makeMemoryBackedDurableStore();
    const mockUpstream = createFostaResponsesMock("success");
    const liveFostaProvider = new FostaTryOnProvider({
      apiKey: "simulated-live-fosta-key",
      endpoint: "https://mock.fosta.local/v1/responses",
      approvedLive: true,
      approvedQuotaUse: true,
      offlineMockMode: false,
      fetchImpl: mockUpstream.fetchImpl,
    });
    const router = createTryOnRouter({
      store: durableStore,
      provider: liveFostaProvider,
    });

    // Without FOSTA_APPROVE_QUOTA=true -> 403 FORBIDDEN
    const unapprovedEnv: TryOnEnvConfig = {
      TRYON_PROVIDER: "fosta",
      TRYON_LIVE_ENABLED: "true",
      FOSTA_API_KEY: "simulated-live-fosta-key",
      FOSTA_APPROVE_QUOTA: "false",
      TRYON_LIVE_MAX_REQUESTS: "1",
      VTO_PROVIDER_CONFIG_VERSION: "v2-fosta-poc-1",
    };
    const blockedQuotaRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeFostaSubmitRequest()),
      }),
      unapprovedEnv,
      { sessionId: "sess_live" },
    );
    expect(blockedQuotaRes.status).toBe(403);
    expect(mockUpstream.callCount).toBe(0);

    // With FOSTA_APPROVE_QUOTA=true and TRYON_LIVE_MAX_REQUESTS=1 -> 1st request succeeds, 2nd is blocked!
    const approvedEnv: TryOnEnvConfig = {
      ...unapprovedEnv,
      FOSTA_APPROVE_QUOTA: "true",
    };
    const firstRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeFostaSubmitRequest({ idempotencyKey: "idem_live_1" })),
      }),
      approvedEnv,
      { sessionId: "sess_live" },
    );
    expect(firstRes.status).toBe(202);
    const firstSubmit = (await firstRes.json()) as TryOnJobSubmitResponse;
    expect(mockUpstream.callCount).toBe(1);

    const pollFirst = await router.handle(
      new Request(`https://fitcheck.local/v2/tryon/jobs/${firstSubmit.jobId}`),
      approvedEnv,
      { sessionId: "sess_live" },
    );
    const liveResult = (await pollFirst.json()) as TryOnResultWire;
    expect(liveResult.source).toBe("AI_LIVE");
    expect(liveResult.providerId).toBe("fosta");
    expect(liveResult.remoteDeletionState).toBe("UNSUPPORTED");

    // Delete first job locally -> honest FOSTA third-party proxy deletion receipt
    const delRes = await router.handle(
      new Request(`https://fitcheck.local/v2/tryon/jobs/${firstSubmit.jobId}`, {
        method: "DELETE",
      }),
      approvedEnv,
      { sessionId: "sess_live" },
    );
    expect(delRes.status).toBe(200);
    const delReceipt = (await delRes.json()) as DeletionReceiptWire;
    expect(delReceipt.remoteDeletionState).toBe("UNSUPPORTED");
    expect(delReceipt.message).toContain("FOSTA");

    // 2nd distinct live request MUST be rejected by TRYON_LIVE_MAX_REQUESTS=1 durable ledger cap!
    const secondRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(
          makeFostaSubmitRequest({
            requestId: "req_fosta_002",
            idempotencyKey: "idem_live_2",
          }),
        ),
      }),
      approvedEnv,
      { sessionId: "sess_live" },
    );
    expect(secondRes.status).toBe(503);
    expect(((await secondRes.json()) as TryOnErrorEnvelope).error.code).toBe("BUDGET_EXCEEDED");
    expect(mockUpstream.callCount).toBe(1);
  });
});
