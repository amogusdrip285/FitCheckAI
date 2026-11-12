import { describe, expect, it } from "vitest";
import {
  createTryOnRouter,
  DUAL_DEADLINE_MS,
  FRONT_DEADLINE_MS,
} from "../src/tryon/handler";
import {
  DEV_PLACEHOLDER_PNG_BASE64,
  FakeUpstreamVtoProvider,
  FashnUpstreamProvider,
} from "../src/tryon/providers";
import { InMemoryTryOnJobStore } from "../src/tryon/store";
import type {
  SafeTryOnLogEntry,
  TryOnEnvConfig,
  TryOnResultWire,
  TryOnSubmitWireRequest,
} from "../src/tryon/types";

const BASE_ENV: TryOnEnvConfig = {
  VTO_MODE: "FAKE",
  VTO_FEASIBILITY_DECISION: "GO_DUAL",
  VTO_PROVIDER_CONFIG_VERSION: "v2-poc-1",
};

function makeValidSubmitBody(
  overrides?: Partial<TryOnSubmitWireRequest>,
): TryOnSubmitWireRequest {
  const base: TryOnSubmitWireRequest = {
    schemaVersion: 2,
    requestId: "req-001",
    idempotencyKey: "idem-001",
    outfitSnapshot: {
      outfitId: "outfit-1",
      wardrobeSnapshotId: "snap-1",
      items: [
        { itemId: "top-1", revision: 1, category: "TOP", colors: ["WHITE"] },
        { itemId: "shoes-1", revision: 1, category: "SHOES", colors: ["BLACK"] },
      ],
    },
    profileRevision: 3,
    personImages: {
      front: {
        mediaId: "person-front-1",
        view: "FRONT",
        mimeType: "image/jpeg",
        width: 768,
        height: 1024,
        contentHash: "sha256:person-front-aaa",
        revision: 1,
        base64Data: DEV_PLACEHOLDER_PNG_BASE64,
      },
      back: null,
    },
    garmentImages: {
      "top-1": {
        front: {
          mediaId: "garment-top-front-1",
          view: "FRONT",
          mimeType: "image/png",
          width: 768,
          height: 1024,
          contentHash: "sha256:top-front-bbb",
          revision: 1,
          base64Data: DEV_PLACEHOLDER_PNG_BASE64,
        },
        back: null,
      },
    },
    requestedViews: ["FRONT"],
    selectedItemIds: ["top-1"],
    consentGrant: {
      id: "consent-001",
      purpose: "TRYON",
      providerId: "fake-tryon-v2",
      providerConfigVersion: "v2-poc-1",
      selectedMediaIds: ["person-front-1", "garment-top-front-1"],
      allowedViews: ["FRONT"],
      requestGeneration: 1,
      confirmedByUser: true,
      issuedAtEpochMillis: 1_700_000_000_000,
      revokedAtEpochMillis: null,
    },
    inputGeneration: 1,
    providerConfigVersion: "v2-poc-1",
  };
  return { ...base, ...overrides };
}

function makeDualSubmitBody(idempotencyKey = "idem-dual-1"): TryOnSubmitWireRequest {
  return makeValidSubmitBody({
    idempotencyKey,
    requestedViews: ["FRONT", "BACK"],
    personImages: {
      front: {
        mediaId: "person-front-1",
        view: "FRONT",
        mimeType: "image/jpeg",
        width: 768,
        height: 1024,
        contentHash: "sha256:person-front-aaa",
        revision: 1,
        base64Data: DEV_PLACEHOLDER_PNG_BASE64,
      },
      back: {
        mediaId: "person-back-1",
        view: "BACK",
        mimeType: "image/jpeg",
        width: 768,
        height: 1024,
        contentHash: "sha256:person-back-ccc",
        revision: 1,
        base64Data: DEV_PLACEHOLDER_PNG_BASE64,
      },
    },
    garmentImages: {
      "top-1": {
        front: {
          mediaId: "garment-top-front-1",
          view: "FRONT",
          mimeType: "image/png",
          width: 768,
          height: 1024,
          contentHash: "sha256:top-front-bbb",
          revision: 1,
          base64Data: DEV_PLACEHOLDER_PNG_BASE64,
        },
        back: {
          mediaId: "garment-top-back-1",
          view: "BACK",
          mimeType: "image/png",
          width: 768,
          height: 1024,
          contentHash: "sha256:top-back-ddd",
          revision: 1,
          base64Data: DEV_PLACEHOLDER_PNG_BASE64,
        },
      },
    },
    consentGrant: {
      id: "consent-dual-1",
      purpose: "TRYON",
      providerId: "fake-tryon-v2",
      providerConfigVersion: "v2-poc-1",
      selectedMediaIds: [
        "person-front-1",
        "person-back-1",
        "garment-top-front-1",
        "garment-top-back-1",
      ],
      allowedViews: ["FRONT", "BACK"],
      requestGeneration: 1,
      confirmedByUser: true,
      issuedAtEpochMillis: 1_700_000_000_000,
      revokedAtEpochMillis: null,
    },
  });
}

describe("C-207 /v2/tryon/jobs server adapter", () => {
  it("enforces session authentication and blocks cross-session job access", async () => {
    const router = createTryOnRouter();

    const unauthRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeValidSubmitBody()),
      }),
      BASE_ENV,
      { sessionId: null },
    );
    expect(unauthRes.status).toBe(401);
    expect(await unauthRes.json()).toMatchObject({
      error: { code: "UNAUTHORIZED", retryable: false },
    });

    const submitRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeValidSubmitBody()),
      }),
      BASE_ENV,
      { sessionId: "session-alice" },
    );
    expect(submitRes.status).toBe(202);
    const { jobId } = (await submitRes.json()) as { jobId: string };

    // Bob tries to read, cancel, and delete Alice's job
    const bobGet = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${jobId}`, { method: "GET" }),
      BASE_ENV,
      { sessionId: "session-bob" },
    );
    expect(bobGet.status).toBe(403);
    expect(await bobGet.json()).toMatchObject({ error: { code: "FORBIDDEN" } });

    const bobCancel = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${jobId}/cancel`, { method: "POST" }),
      BASE_ENV,
      { sessionId: "session-bob" },
    );
    expect(bobCancel.status).toBe(403);

    const bobDelete = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${jobId}`, { method: "DELETE" }),
      BASE_ENV,
      { sessionId: "session-bob" },
    );
    expect(bobDelete.status).toBe(403);
  });

  it("rejects forbidden file paths, arbitrary URLs, unconsented uploads, and oversized payloads", async () => {
    const provider = new FakeUpstreamVtoProvider();
    const router = createTryOnRouter({ provider });

    // 1. Forbidden localRelativePath / URL in payload
    const withLocalPath = {
      ...makeValidSubmitBody(),
      localRelativePath: "/data/user/0/com.fitcheckai/files/person.jpg",
    };
    const pathRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(withLocalPath),
      }),
      BASE_ENV,
      { sessionId: "s1" },
    );
    expect(pathRes.status).toBe(400);
    expect(await pathRes.json()).toMatchObject({ error: { code: "INVALID_REQUEST" } });

    // 2. Arbitrary remote URL injected in string field (also must NOT leak into requestId log/envelope)
    const maliciousLogs: SafeTryOnLogEntry[] = [];
    const safeLogRouter = createTryOnRouter({
      provider,
      logger: (entry) => maliciousLogs.push(entry),
    });
    const withRemoteUrl = makeValidSubmitBody({
      requestId: "https://evil.example.com/exfiltrate",
    });
    const urlRes = await safeLogRouter.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(withRemoteUrl),
      }),
      BASE_ENV,
      { sessionId: "s1" },
    );
    expect(urlRes.status).toBe(400);
    expect(JSON.stringify(maliciousLogs)).not.toContain("evil.example.com");

    // 3. Consent false or revoked -> 403 CONSENT_REQUIRED with 0 upstream calls
    const unconsented = makeValidSubmitBody({
      consentGrant: {
        ...makeValidSubmitBody().consentGrant,
        confirmedByUser: false,
      },
    });
    const consentRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(unconsented),
      }),
      BASE_ENV,
      { sessionId: "s1" },
    );
    expect(consentRes.status).toBe(403);
    expect(await consentRes.json()).toMatchObject({ error: { code: "CONSENT_REQUIRED" } });
    expect(provider.submitCallCount).toBe(0);

    // 4. Garment decoded bytes > 1 MiB -> 413 PAYLOAD_TOO_LARGE
    const overOneMiBBase64 = "A".repeat(Math.ceil(((1024 * 1024 + 512) * 4) / 3 / 4) * 4);
    const hugeGarment = makeValidSubmitBody();
    hugeGarment.garmentImages["top-1"]!.front!.base64Data = overOneMiBBase64;
    const hugeRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(hugeGarment),
      }),
      BASE_ENV,
      { sessionId: "s1" },
    );
    expect(hugeRes.status).toBe(413);
    expect(await hugeRes.json()).toMatchObject({ error: { code: "PAYLOAD_TOO_LARGE" } });
    expect(provider.submitCallCount).toBe(0);
  });

  it("enforces capability gates, missing back input, and blocks front-cloned-as-back", async () => {
    const provider = new FakeUpstreamVtoProvider();
    const router = createTryOnRouter({ provider });

    // 1. GO_FRONT_ONLY rejects BACK view before upstream
    const frontOnlyRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeDualSubmitBody()),
      }),
      { ...BASE_ENV, VTO_FEASIBILITY_DECISION: "GO_FRONT_ONLY" },
      { sessionId: "s1" },
    );
    expect(frontOnlyRes.status).toBe(400);
    expect(await frontOnlyRes.json()).toMatchObject({
      error: { code: "UNSUPPORTED_CAPABILITY" },
    });

    // 2. Requesting BACK without personImages.back -> 400 NEEDS_INPUT
    const missingBack = makeDualSubmitBody("idem-missing-back");
    missingBack.personImages.back = null;
    const missingRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(missingBack),
      }),
      BASE_ENV,
      { sessionId: "s1" },
    );
    expect(missingRes.status).toBe(400);
    expect(await missingRes.json()).toMatchObject({ error: { code: "NEEDS_INPUT" } });

    // 3. Cloning person front hash into back -> 400 INVALID_REQUEST
    const clonedBack = makeDualSubmitBody("idem-cloned-back");
    clonedBack.personImages.back!.contentHash =
      clonedBack.personImages.front!.contentHash;
    const clonedRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(clonedBack),
      }),
      BASE_ENV,
      { sessionId: "s1" },
    );
    expect(clonedRes.status).toBe(400);
    expect(await clonedRes.json()).toMatchObject({ error: { code: "INVALID_REQUEST" } });

    // 4. Unsupported SHOES category -> 400 UNSUPPORTED_CAPABILITY
    const shoesReq = makeValidSubmitBody({
      idempotencyKey: "idem-shoes",
      selectedItemIds: ["shoes-1"],
      garmentImages: {
        "shoes-1": {
          front: {
            mediaId: "garment-shoes-front",
            view: "FRONT",
            mimeType: "image/png",
            width: 512,
            height: 512,
            contentHash: "sha256:shoes-front",
            revision: 1,
            base64Data: DEV_PLACEHOLDER_PNG_BASE64,
          },
        },
      },
      consentGrant: {
        ...makeValidSubmitBody().consentGrant,
        selectedMediaIds: ["person-front-1", "garment-shoes-front"],
      },
    });
    const shoesRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(shoesReq),
      }),
      BASE_ENV,
      { sessionId: "s1" },
    );
    expect(shoesRes.status).toBe(400);
    expect(await shoesRes.json()).toMatchObject({
      error: { code: "UNSUPPORTED_CAPABILITY" },
    });
    expect(provider.submitCallCount).toBe(0);
  });

  it("deduplicates concurrent submissions with same idempotencyKey and enforces 409 on conflict or second active job", async () => {
    const provider = new FakeUpstreamVtoProvider();
    const router = createTryOnRouter({ provider });

    const payload = JSON.stringify(makeValidSubmitBody({ idempotencyKey: "idem-concurrent" }));
    const [res1, res2] = await Promise.all([
      router.handle(
        new Request("https://proxy.test/v2/tryon/jobs", { method: "POST", body: payload }),
        BASE_ENV,
        { sessionId: "s-idem" },
      ),
      router.handle(
        new Request("https://proxy.test/v2/tryon/jobs", { method: "POST", body: payload }),
        BASE_ENV,
        { sessionId: "s-idem" },
      ),
    ]);

    expect(res1.status).toBe(202);
    expect(res2.status).toBe(202);
    const body1 = (await res1.json()) as { jobId: string };
    const body2 = (await res2.json()) as { jobId: string };
    expect(body1.jobId).toBe(body2.jobId);
    expect(provider.submitCallCount).toBe(1);

    // Same idempotencyKey + different profileRevision -> 409 CONFLICT
    const mutated = makeValidSubmitBody({
      idempotencyKey: "idem-concurrent",
      profileRevision: 99,
    });
    const conflictRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(mutated),
      }),
      BASE_ENV,
      { sessionId: "s-idem" },
    );
    expect(conflictRes.status).toBe(409);
    expect(await conflictRes.json()).toMatchObject({ error: { code: "CONFLICT" } });

    // Different idempotencyKey while first job is still RUNNING -> 409 CONFLICT (1 active job/session)
    const secondActive = makeValidSubmitBody({ idempotencyKey: "idem-second-active" });
    const activeConflictRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(secondActive),
      }),
      BASE_ENV,
      { sessionId: "s-idem" },
    );
    expect(activeConflictRes.status).toBe(409);
  });

  it("handles ambiguous upstream submit timeout as UNKNOWN_SUBMISSION without auto-resubmitting", async () => {
    const provider = new FakeUpstreamVtoProvider({
      defaultFrontScenario: "AMBIGUOUS_SUBMIT_TIMEOUT",
    });
    const router = createTryOnRouter({ provider });

    const res = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeValidSubmitBody()),
      }),
      BASE_ENV,
      { sessionId: "s-ambiguous" },
    );
    expect(res.status).toBe(202);
    const submitted = (await res.json()) as { jobId: string; status: string };
    expect(submitted.status).toBe("UNKNOWN_SUBMISSION");
    expect(provider.submitCallCount).toBe(1);

    const pollRes = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${submitted.jobId}`, { method: "GET" }),
      BASE_ENV,
      { sessionId: "s-ambiguous" },
    );
    const polled = (await pollRes.json()) as TryOnResultWire;
    expect(polled.status).toBe("UNKNOWN_SUBMISSION");
    expect(provider.submitCallCount).toBe(1);
  });

  it("reports PARTIAL (never SUCCEEDED) when front succeeds and back fails, with UNREVIEWED quality", async () => {
    const provider = new FakeUpstreamVtoProvider({
      defaultFrontScenario: "SUCCEED_AFTER_ONE_POLL",
      defaultBackScenario: "FAIL_ON_POLL",
    });
    const router = createTryOnRouter({ provider });

    const submitRes = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeDualSubmitBody()),
      }),
      BASE_ENV,
      { sessionId: "s-partial" },
    );
    const { jobId } = (await submitRes.json()) as { jobId: string };

    const pollRes = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${jobId}`, { method: "GET" }),
      BASE_ENV,
      { sessionId: "s-partial" },
    );
    const result = (await pollRes.json()) as TryOnResultWire;
    expect(result.status).toBe("PARTIAL");
    expect(result.source).toBe("FAKE");
    expect(result.appliedItemIds).toEqual(["top-1"]);
    expect(result.unappliedItemIds).toEqual(["shoes-1"]);
    expect(result.views.FRONT?.state).toBe("SUCCEEDED");
    expect(result.views.FRONT?.qualityReview).toBe("UNREVIEWED");
    expect(result.views.BACK?.state).toBe("FAILED");
    expect(result.views.BACK?.output).toBeNull();
    expect(result.warnings.some((w) => w.startsWith("INDEPENDENT_VIEW_GENERATION"))).toBe(
      true,
    );
  });

  it("enforces technical deadlines, output TTL expiry, cancel receipt honesty, and delete suppression", async () => {
    let clock = 1_700_000_000_000;
    const provider = new FakeUpstreamVtoProvider({
      defaultFrontScenario: "STAY_RUNNING",
    });
    const router = createTryOnRouter({
      provider,
      now: () => clock,
    });

    // 1. Technical timeout after FRONT_DEADLINE_MS (120s)
    const submit1 = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeValidSubmitBody({ idempotencyKey: "k-timeout" })),
      }),
      BASE_ENV,
      { sessionId: "s-time" },
    );
    const { jobId: timeoutJobId } = (await submit1.json()) as { jobId: string };

    clock += FRONT_DEADLINE_MS + 1;
    const timedOutRes = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${timeoutJobId}`, { method: "GET" }),
      BASE_ENV,
      { sessionId: "s-time" },
    );
    const timedOutBody = (await timedOutRes.json()) as TryOnResultWire;
    expect(timedOutBody.status).toBe("TIMED_OUT");
    expect(timedOutBody.views.FRONT?.error?.code).toBe("UPSTREAM_TIMEOUT");

    // 2. Cancel + Delete + Output TTL expiry
    provider.setScenarios("SUCCEED_AFTER_ONE_POLL", "STAY_RUNNING");
    const submit2 = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeDualSubmitBody("k-cancel-del")),
      }),
      { ...BASE_ENV, VTO_OUTPUT_TTL_MS: "5000" },
      { sessionId: "s-del" },
    );
    const { jobId: job2 } = (await submit2.json()) as { jobId: string };

    // Cancel while active
    const cancelRes = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${job2}/cancel`, { method: "POST" }),
      BASE_ENV,
      { sessionId: "s-del" },
    );
    expect(cancelRes.status).toBe(200);
    const cancelBody = await cancelRes.json();
    expect(cancelBody).toMatchObject({
      jobId: job2,
      status: "CANCELLED",
      refundGuaranteed: false,
      upstreamCancelAttempted: true,
    });

    // Delete wipes outputs and prevents resurrection on subsequent GET
    const delRes = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${job2}`, { method: "DELETE" }),
      BASE_ENV,
      { sessionId: "s-del" },
    );
    expect(delRes.status).toBe(200);
    expect(await delRes.json()).toMatchObject({
      jobId: job2,
      status: "DELETED",
      localOutputsDeleted: true,
      lateCompletionSuppressed: true,
      remoteDeletionState: "NOT_UPLOADED",
    });

    const afterDelRes = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${job2}`, { method: "GET" }),
      BASE_ENV,
      { sessionId: "s-del" },
    );
    const afterDelBody = (await afterDelRes.json()) as TryOnResultWire;
    expect(afterDelBody.status).toBe("DELETED");
    expect(afterDelBody.views.FRONT?.output).toBeNull();
    expect(afterDelBody.views.BACK?.output).toBeNull();

    // 3. Output TTL expiration clears output bytes and marks view EXPIRED
    provider.setScenarios("SUCCEED_IMMEDIATELY");
    const submit3 = await router.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeValidSubmitBody({ idempotencyKey: "k-expire" })),
      }),
      { ...BASE_ENV, VTO_OUTPUT_TTL_MS: "10000" },
      { sessionId: "s-expire" },
    );
    const { jobId: job3 } = (await submit3.json()) as { jobId: string };
    clock += DUAL_DEADLINE_MS; // well past 10s TTL
    const expiredPoll = await router.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${job3}`, { method: "GET" }),
      { ...BASE_ENV, VTO_OUTPUT_TTL_MS: "10000" },
      { sessionId: "s-expire" },
    );
    const expiredBody = (await expiredPoll.json()) as TryOnResultWire;
    expect(expiredBody.status).toBe("FAILED");
    expect(expiredBody.views.FRONT?.state).toBe("EXPIRED");
    expect(expiredBody.views.FRONT?.output).toBeNull();
  });

  it("guards AI_LIVE mode with durable store requirement, kill-switch, budget cap, base64 enforcement, and log redaction", async () => {
    const logs: SafeTryOnLogEntry[] = [];

    // 1. Ephemeral InMemoryTryOnJobStore (isDurable=false) MUST be rejected in AI_LIVE mode
    const ephemeralRouter = createTryOnRouter({
      store: new InMemoryTryOnJobStore({ simulateDurableForTests: false }),
      logger: (entry) => logs.push(entry),
    });
    const ephemeralRes = await ephemeralRouter.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeValidSubmitBody()),
      }),
      {
        VTO_MODE: "AI_LIVE",
        VTO_FEASIBILITY_DECISION: "GO_FRONT_ONLY",
        VTO_PROVIDER_CONFIG_VERSION: "v2-poc-1",
        FASHN_API_KEY: "secret_fashn_key_do_not_log",
      },
      { sessionId: "s-live" },
    );
    expect(ephemeralRes.status).toBe(503);
    expect(await ephemeralRes.json()).toMatchObject({
      error: { code: "UNSUPPORTED_CAPABILITY" },
    });

    // 2. Simulated durable store + mocked FASHN fetch: verifies return_base64=true, budget cap, and log redaction
    let capturedRunBody = "";
    const fashnProvider = new FashnUpstreamProvider({
      apiKey: "secret_fashn_key_do_not_log",
      fetchImpl: async (input, init) => {
        const url = String(input);
        if (url.endsWith("/run")) {
          capturedRunBody = String(init?.body ?? "");
          return new Response(JSON.stringify({ id: "fashn-pred-1", error: null }), {
            status: 200,
          });
        }
        return new Response(
          JSON.stringify({
            id: "fashn-pred-1",
            status: "completed",
            output: [`data:image/png;base64,${DEV_PLACEHOLDER_PNG_BASE64}`],
          }),
          { status: 200 },
        );
      },
    });

    const liveRouter = createTryOnRouter({
      store: new InMemoryTryOnJobStore({ simulateDurableForTests: true }),
      provider: fashnProvider,
      logger: (entry) => logs.push(entry),
    });

    const liveEnv: TryOnEnvConfig = {
      VTO_MODE: "AI_LIVE",
      VTO_FEASIBILITY_DECISION: "GO_FRONT_ONLY",
      VTO_PROVIDER_CONFIG_VERSION: "v2-poc-1",
      VTO_BUDGET_CAP_UNITS: "1",
      FASHN_API_KEY: "secret_fashn_key_do_not_log",
    };

    const liveSubmit = await liveRouter.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(
          makeValidSubmitBody({
            idempotencyKey: "live-1",
            consentGrant: {
              ...makeValidSubmitBody().consentGrant,
              providerId: "fashn-tryon-v1.6",
            },
          }),
        ),
      }),
      liveEnv,
      { sessionId: "s-live-1" },
    );
    expect(liveSubmit.status).toBe(202);
    expect(JSON.parse(capturedRunBody)).toMatchObject({
      model_name: "tryon-v1.6",
      inputs: { return_base64: true, category: "tops" },
    });

    const { jobId: liveJobId } = (await liveSubmit.json()) as { jobId: string };
    const livePoll = await liveRouter.handle(
      new Request(`https://proxy.test/v2/tryon/jobs/${liveJobId}`, { method: "GET" }),
      liveEnv,
      { sessionId: "s-live-1" },
    );
    const liveResult = (await livePoll.json()) as TryOnResultWire;
    expect(liveResult.status).toBe("SUCCEEDED");
    expect(liveResult.source).toBe("AI_LIVE");
    expect(liveResult.remoteDeletionState).toBe("BASE64_EPHEMERAL_60M");

    // Next job exceeds VTO_BUDGET_CAP_UNITS=1 -> 503 BUDGET_EXCEEDED
    const overBudget = await liveRouter.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(
          makeValidSubmitBody({
            idempotencyKey: "live-2",
            consentGrant: {
              ...makeValidSubmitBody().consentGrant,
              providerId: "fashn-tryon-v1.6",
            },
          }),
        ),
      }),
      liveEnv,
      { sessionId: "s-live-2" },
    );
    expect(overBudget.status).toBe(503);
    expect(await overBudget.json()).toMatchObject({
      error: { code: "BUDGET_EXCEEDED" },
    });

    // Kill-switch immediately disables service -> 503
    const killedRes = await liveRouter.handle(
      new Request("https://proxy.test/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(makeValidSubmitBody({ idempotencyKey: "live-3" })),
      }),
      { ...liveEnv, VTO_KILL_SWITCH: "true" },
      { sessionId: "s-live-3" },
    );
    expect(killedRes.status).toBe(503);

    // Verify SafeTryOnLogger NEVER logged API keys, base64 image data, or media hashes
    const serializedLogs = JSON.stringify(logs);
    expect(serializedLogs).not.toContain("secret_fashn_key_do_not_log");
    expect(serializedLogs).not.toContain(DEV_PLACEHOLDER_PNG_BASE64);
    expect(serializedLogs).not.toContain("sha256:person-front-aaa");
  });
});
