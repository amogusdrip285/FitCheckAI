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
        it(`builds concise tailored prompt for ${mode} + ${view} + ${combo.label} within length budget`, () => {
          const prompt = buildFostaTryOnPrompt({
            mode,
            view,
            garmentKinds: combo.kinds,
          });

          expect(FOSTA_VTO_PROMPT_VERSION).toBe("v2.1");
          expect(prompt).not.toContain("FITCHECK_VTO_PROMPT_VERSION");
          if (combo.label === "TOP+BOTTOM") {
            expect(prompt.length).toBeLessThanOrEqual(750);
          } else {
            expect(prompt.length).toBeLessThanOrEqual(600);
          }

          expect(prompt).toContain("Create exactly one photorealistic");
          expect(prompt).toContain("shoes");
          expect(prompt).toContain("accessories");

          if (combo.label === "TOP") {
            expect(prompt).toContain("image 2 is the reference top");
            expect(prompt).toContain(
              "wear only the reference top, keeping their existing bottom unchanged.",
            );
            expect(prompt).not.toContain("image 3");
          } else if (combo.label === "BOTTOM") {
            expect(prompt).toContain("image 2 is the reference bottom");
            expect(prompt).toContain(
              "wear only the reference bottom, keeping their existing top unchanged.",
            );
            expect(prompt).not.toContain("image 3");
          } else {
            expect(prompt).toContain("image 2 is the reference top");
            expect(prompt).toContain("image 3 is the reference bottom");
            expect(prompt).toContain(
              "wear both the reference top and reference bottom with natural waist layering.",
            );
          }

          if (view === "BACK") {
            expect(prompt).toContain("back-view virtual try-on image");
            expect(prompt).toContain("Image 1 is the person from behind (back view)");
            expect(prompt).toContain(
              "Keep the person facing away, preserving their identity, face when visible, hairstyle, skin tone, body proportions, pose",
            );
          } else {
            expect(prompt).toContain("front-view virtual try-on image");
            expect(prompt).toContain("Image 1 is the person (front view)");
            expect(prompt).toContain(
              "Preserve the person's face, hairstyle, skin tone, body proportions, pose",
            );
          }

          if (mode === "STUDIO_WHITE") {
            expect(prompt).toContain("clean white studio background with soft even lighting.");
            expect(prompt).not.toContain("original background");
          } else {
            expect(prompt).toContain("lighting and original background.");
            expect(prompt).not.toContain("white studio background");
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
    expect(Object.keys(frontTop).sort()).toEqual([
      "input",
      "model",
      "tool_choice",
      "tools",
    ]);
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
    expect(Object.keys(studioBackCombo).sort()).toEqual([
      "input",
      "model",
      "tool_choice",
      "tools",
    ]);
    expect(studioBackCombo.input[0]?.content).toHaveLength(4);
    expect((studioBackCombo.input[0]?.content[0] as { text: string }).text).toContain(
      "clean white studio background",
    );
    expect((studioBackCombo.input[0]?.content[0] as { text: string }).text).toContain(
      "back-view virtual try-on image",
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
      "image 2 is the reference top",
    );
    expect(mock.lastCapturedRequest?.prompt).toContain(
      "image 3 is the reference bottom",
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

  it("times out cleanly without retry when FOSTA mock returns headers then hangs while streaming body", async () => {
    let networkCalls = 0;
    const provider = new FostaTryOnProvider({
      apiKey: "super-secret-fosta-key-hang",
      approvedLive: true,
      approvedQuotaUse: true,
      timeoutMs: 1_000,
      fetchImpl: async () => {
        networkCalls += 1;
        const encoder = new TextEncoder();
        const stream = new ReadableStream<Uint8Array>({
          start(controller) {
            controller.enqueue(
              encoder.encode(
                '{"id":"resp_hang","status":"completed","output":[{"type":"image_generation_call","result":"',
              ),
            );
            // Deliberately keep body stream open forever without closing
          },
        });
        return new Response(stream, {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      },
    });

    const startedAt = Date.now();
    await expect(
      provider.submitView({
        jobId: "job_body_hang",
        requestId: "req_body_hang",
        view: "FRONT",
        personImage: makePngWireImage("pf", "FRONT"),
        garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
      }),
    ).rejects.toMatchObject({
      name: "UpstreamAmbiguousTimeoutError",
      retryable: false,
    });
    const elapsedMs = Date.now() - startedAt;

    expect(elapsedMs).toBeGreaterThanOrEqual(950);
    expect(elapsedMs).toBeLessThan(3_000);
    expect(networkCalls).toBe(1);
    expect(provider.submitCallCount).toBe(1);
  });

  it("maps HTTP 401, 402, 403, 500 with retryable=false, 0 retries, and secret redaction", async () => {
    const cases: Array<{ status: number; expectedCode: string }> = [
      { status: 401, expectedCode: "UNAUTHORIZED" },
      { status: 402, expectedCode: "INSUFFICIENT_QUOTA" },
      { status: 403, expectedCode: "FORBIDDEN" },
      { status: 500, expectedCode: "UPSTREAM_ERROR" },
    ];

    for (const testCase of cases) {
      let calls = 0;
      const secretKey = "super-secret-fosta-key-http";
      const provider = new FostaTryOnProvider({
        apiKey: secretKey,
        approvedLive: true,
        approvedQuotaUse: true,
        fetchImpl: async () => {
          calls += 1;
          return new Response(
            JSON.stringify({
              error: {
                code: "http_error",
                message: `Echo ${secretKey} Bearer tok_leak_999 data:image/png;base64,iVBORw0KGgoAAAANS`,
              },
            }),
            {
              status: testCase.status,
              headers: { "content-type": "application/json" },
            },
          );
        },
      });

      try {
        await provider.submitView({
          jobId: `job_http_${testCase.status}`,
          requestId: `req_http_${testCase.status}`,
          view: "FRONT",
          personImage: makePngWireImage("pf", "FRONT"),
          garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
        });
        expect.unreachable(`Expected HTTP ${testCase.status} to throw`);
      } catch (err) {
        expect(err).toBeInstanceOf(UpstreamDeterministicError);
        const typed = err as UpstreamDeterministicError;
        expect(typed.code).toBe(testCase.expectedCode);
        expect(typed.retryable).toBe(false);
        expect(typed.message).not.toContain(secretKey);
        expect(typed.message).not.toContain("tok_leak_999");
        expect(typed.message).not.toContain("iVBORw0KGgoAAAANS");
      }
      expect(calls).toBe(1);
      expect(provider.submitCallCount).toBe(1);
    }
  });

  it("rejects empty output[], safety content_filter, refusal items, and 4xx moderation codes cleanly with redaction", async () => {
    const secretKey = "super-secret-fosta-key-mod";

    // 1. Empty output[]
    {
      let calls = 0;
      const provider = new FostaTryOnProvider({
        apiKey: secretKey,
        approvedLive: true,
        approvedQuotaUse: true,
        fetchImpl: async () => {
          calls += 1;
          return new Response(
            JSON.stringify({
              id: "resp_empty_out",
              status: "completed",
              error: null,
              output: [],
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          );
        },
      });
      await expect(
        provider.submitView({
          jobId: "job_empty_output",
          requestId: "req_empty_output",
          view: "FRONT",
          personImage: makePngWireImage("pf", "FRONT"),
          garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
        }),
      ).rejects.toMatchObject({ code: "INVALID_AI_OUTPUT", retryable: false });
      expect(calls).toBe(1);
    }

    // 2. incomplete_details.reason === "content_filter"
    {
      let calls = 0;
      const provider = new FostaTryOnProvider({
        apiKey: secretKey,
        approvedLive: true,
        approvedQuotaUse: true,
        fetchImpl: async () => {
          calls += 1;
          return new Response(
            JSON.stringify({
              id: "resp_cf",
              status: "incomplete",
              incomplete_details: { reason: "content_filter" },
              output: [],
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          );
        },
      });
      try {
        await provider.submitView({
          jobId: "job_content_filter",
          requestId: "req_content_filter",
          view: "FRONT",
          personImage: makePngWireImage("pf", "FRONT"),
          garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
        });
        expect.unreachable("Expected content_filter to throw");
      } catch (err) {
        expect(err).toBeInstanceOf(UpstreamDeterministicError);
        const typed = err as UpstreamDeterministicError;
        expect(typed.code).toBe("FORBIDDEN");
        expect(typed.retryable).toBe(false);
        expect(typed.message).toContain("content_filter");
      }
      expect(calls).toBe(1);
    }

    // 3. Refusal item inside output[] (direct refusal & message content refusal) with secret leakage attempt
    for (const outputPayload of [
      [{ type: "refusal", refusal: `Refused with key ${secretKey} Bearer secret_abc` }],
      [
        {
          type: "message",
          content: [
            {
              type: "refusal",
              refusal: `Safety refusal echoing sk-secret1234567890 ${secretKey}`,
            },
          ],
        },
      ],
    ]) {
      let calls = 0;
      const provider = new FostaTryOnProvider({
        apiKey: secretKey,
        approvedLive: true,
        approvedQuotaUse: true,
        fetchImpl: async () => {
          calls += 1;
          return new Response(
            JSON.stringify({
              id: "resp_refusal",
              status: "completed",
              error: null,
              output: outputPayload,
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          );
        },
      });
      try {
        await provider.submitView({
          jobId: "job_refusal",
          requestId: "req_refusal",
          view: "FRONT",
          personImage: makePngWireImage("pf", "FRONT"),
          garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
        });
        expect.unreachable("Expected refusal to throw");
      } catch (err) {
        expect(err).toBeInstanceOf(UpstreamDeterministicError);
        const typed = err as UpstreamDeterministicError;
        expect(typed.code).toBe("FORBIDDEN");
        expect(typed.retryable).toBe(false);
        expect(typed.message).toContain("refusal");
        expect(typed.message).not.toContain(secretKey);
        expect(typed.message).not.toContain("secret_abc");
        expect(typed.message).not.toContain("sk-secret1234567890");
      }
      expect(calls).toBe(1);
    }

    // 4. HTTP 4xx moderation code in JSON error body with redaction
    {
      let calls = 0;
      const provider = new FostaTryOnProvider({
        apiKey: secretKey,
        approvedLive: true,
        approvedQuotaUse: true,
        fetchImpl: async () => {
          calls += 1;
          return new Response(
            JSON.stringify({
              error: {
                code: "moderation_blocked",
                type: "image_generation_user_error",
                message: `Prompt blocked Bearer leak_tok_456 ${secretKey}`,
              },
            }),
            { status: 400, headers: { "content-type": "application/json" } },
          );
        },
      });
      try {
        await provider.submitView({
          jobId: "job_mod_400",
          requestId: "req_mod_400",
          view: "FRONT",
          personImage: makePngWireImage("pf", "FRONT"),
          garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
        });
        expect.unreachable("Expected 400 moderation_blocked to throw");
      } catch (err) {
        expect(err).toBeInstanceOf(UpstreamDeterministicError);
        const typed = err as UpstreamDeterministicError;
        expect(typed.code).toBe("FORBIDDEN");
        expect(typed.retryable).toBe(false);
        expect(typed.message).toContain("moderation_blocked");
        expect(typed.message).not.toContain(secretKey);
        expect(typed.message).not.toContain("leak_tok_456");
      }
      expect(calls).toBe(1);
    }
  });

  it("parses large chunked FOSTA response with >= 1.5 MB base64 synthetic PNG completely without truncation", async () => {
    const largePngBytes = buildLookbookMockPngBytes({
      width: 680,
      height: 680,
      garmentRgb: [42, 88, 138],
    });
    const largeBase64 = encodeBase64(largePngBytes);
    expect(largeBase64.length).toBeGreaterThanOrEqual(Math.ceil(1.5 * 1024 * 1024));

    const wireBytes = new TextEncoder().encode(
      JSON.stringify({
        id: "resp_large_synthetic_png",
        object: "response",
        status: "completed",
        error: null,
        output: [
          {
            id: "ig_large_1",
            type: "image_generation_call",
            status: "completed",
            output_format: "png",
            result: largeBase64,
          },
        ],
      }),
    );

    const provider = new FostaTryOnProvider({
      apiKey: "mock-large-key",
      endpoint: "https://mock.fosta.local/v1/responses",
      offlineMockMode: true,
      fetchImpl: async () => {
        const chunkSize = 32_768;
        let offset = 0;
        const stream = new ReadableStream<Uint8Array>({
          async pull(controller) {
            if (offset >= wireBytes.byteLength) {
              controller.close();
              return;
            }
            const end = Math.min(offset + chunkSize, wireBytes.byteLength);
            controller.enqueue(wireBytes.subarray(offset, end));
            offset = end;
            await Promise.resolve();
          },
        });
        return new Response(stream, {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      },
    });

    const out = await provider.submitView({
      jobId: "job_large_1",
      requestId: "req_large_1",
      view: "FRONT",
      personImage: makePngWireImage("pf", "FRONT"),
      garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
    });

    expect(out.initialState).toBe("SUCCEEDED");
    expect(out.immediateOutput?.mimeType).toBe("image/png");
    expect(out.immediateOutput?.width).toBe(680);
    expect(out.immediateOutput?.height).toBe(680);
    expect(out.immediateOutput?.base64Data.length).toBe(largeBase64.length);
    expect(out.immediateOutput?.base64Data).toBe(largeBase64);
    expect(provider.submitCallCount).toBe(1);
  });

  it("logs sanitized incompleteDiagnostic (reason redacted & bounded, outputItemTypes, errorCode=null) with 0 retries when status='incomplete'", async () => {
    const logs: SafeTryOnLogEntry[] = [];
    const secretKey = "super-secret-fosta-key-diag";
    const fakeResultB64 = "A".repeat(128);
    let calls = 0;

    const provider = new FostaTryOnProvider({
      apiKey: secretKey,
      approvedLive: true,
      approvedQuotaUse: true,
      logger: (entry) => logs.push(entry),
      fetchImpl: async () => {
        calls += 1;
        return new Response(
          JSON.stringify({
            id: "resp_incomplete_max_tokens",
            status: "incomplete",
            incomplete_details: {
              reason: `max_output_tokens Bearer ${secretKey} sk-secret1234567890 ${"X".repeat(80)}`,
            },
            output: [
              { type: "reasoning" },
              { type: "image_generation_call", status: "in_progress", result: fakeResultB64 },
            ],
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      },
    });

    await expect(
      provider.submitView({
        jobId: "job_inc_diag_1",
        requestId: "req_inc_diag_1",
        view: "FRONT",
        personImage: makePngWireImage("pf", "FRONT"),
        garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
      }),
    ).rejects.toMatchObject({
      code: "INVALID_AI_OUTPUT",
      retryable: false,
    });

    expect(calls).toBe(1);
    expect(provider.submitCallCount).toBe(1);
    expect(logs).toHaveLength(1);
    const diagLog = logs[0]!;
    expect(diagLog.event).toBe("tryon.upstream.incomplete");
    expect(diagLog.errorCode).toBe("INVALID_AI_OUTPUT");
    expect(diagLog.incompleteDiagnostic?.status).toBe("incomplete");
    expect(diagLog.incompleteDiagnostic?.errorCode).toBeNull();
    expect(diagLog.incompleteDiagnostic?.outputItemTypes).toEqual([
      "reasoning",
      "image_generation_call",
    ]);
    const loggedReason = diagLog.incompleteDiagnostic?.incompleteReason ?? "";
    expect(loggedReason.length).toBeGreaterThan(0);
    expect(loggedReason.length).toBeLessThanOrEqual(64);
    expect(loggedReason).toContain("max_output_tokens");

    const serializedLogs = JSON.stringify(logs);
    expect(serializedLogs).not.toContain(secretKey);
    expect(serializedLogs).not.toContain("sk-secret1234567890");
    expect(serializedLogs).not.toContain(fakeResultB64);
  });

  it("logs safe errorCode and outputItemTypes without leaking raw error.message or secrets when non-completed response includes error object", async () => {
    const logs: SafeTryOnLogEntry[] = [];
    const secretKey = "super-secret-fosta-key-errdiag";
    let calls = 0;

    const provider = new FostaTryOnProvider({
      apiKey: secretKey,
      approvedLive: true,
      approvedQuotaUse: true,
      logger: (entry) => logs.push(entry),
      fetchImpl: async () => {
        calls += 1;
        return new Response(
          JSON.stringify({
            id: "resp_incomplete_err",
            status: "incomplete",
            incomplete_details: null,
            error: {
              code: "max_tokens_exceeded",
              message: `raw upstream error body containing ${secretKey} and ${"B".repeat(96)}`,
            },
            output: [{ type: "message", content: [{ type: "output_text", text: "truncated" }] }],
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      },
    });

    await expect(
      provider.submitView({
        jobId: "job_inc_diag_2",
        requestId: "req_inc_diag_2",
        view: "FRONT",
        personImage: makePngWireImage("pf", "FRONT"),
        garments: [{ itemId: "t1", category: "TOP", image: makePngWireImage("tf", "FRONT") }],
      }),
    ).rejects.toMatchObject({
      code: "UPSTREAM_ERROR",
      retryable: false,
    });

    expect(calls).toBe(1);
    expect(logs).toHaveLength(1);
    expect(logs[0]!.event).toBe("tryon.upstream.incomplete");
    expect(logs[0]!.errorCode).toBe("UPSTREAM_ERROR");
    expect(logs[0]!.incompleteDiagnostic).toEqual({
      status: "incomplete",
      incompleteReason: null,
      errorCode: "max_tokens_exceeded",
      outputItemTypes: ["message"],
    });
    const serialized = JSON.stringify(logs);
    expect(serialized).not.toContain(secretKey);
    expect(serialized).not.toContain("raw upstream error body");
    expect(serialized).not.toContain("B".repeat(64));
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

  it("rejects invalid local images (<256px dimension, MIME mismatch, oversized bytes) BEFORE reserveLiveSpendAtomically with 0 quota spent and 0 upstream calls", async () => {
    const durableStore = makeMemoryBackedDurableStore();
    const mockUpstream = createFostaResponsesMock("success");
    const liveFostaProvider = new FostaTryOnProvider({
      apiKey: "simulated-live-fosta-key",
      endpoint: "https://mock.fosta.local/v1/responses",
      approvedLive: true,
      approvedQuotaUse: true,
      offlineMockMode: false,
      minInputDimensionPx: 256,
      fetchImpl: mockUpstream.fetchImpl,
    });
    const router = createTryOnRouter({
      store: durableStore,
      provider: liveFostaProvider,
    });

    const liveEnv: TryOnEnvConfig = {
      TRYON_PROVIDER: "fosta",
      TRYON_LIVE_ENABLED: "true",
      FOSTA_API_KEY: "simulated-live-fosta-key",
      FOSTA_APPROVE_QUOTA: "true",
      TRYON_LIVE_MAX_REQUESTS: "1",
      VTO_PROVIDER_CONFIG_VERSION: "v2-fosta-poc-1",
    };

    const valid256PngBytes = buildLookbookMockPngBytes({
      width: 256,
      height: 320,
      garmentRgb: [36, 62, 108],
    });
    const validPerson256: WireImageInput = {
      mediaId: "person_front_256",
      view: "FRONT",
      mimeType: "image/png",
      width: 256,
      height: 320,
      revision: 1,
      contentHash: "sha256:person_front_256",
      base64Data: encodeBase64(valid256PngBytes),
    };
    const validGarment256: WireImageInput = {
      mediaId: "top_front_256",
      view: "FRONT",
      mimeType: "image/png",
      width: 256,
      height: 320,
      revision: 1,
      contentHash: "sha256:top_front_256",
      base64Data: encodeBase64(valid256PngBytes),
    };

    // Case A: Small image (128x160 < 256px) -> rejected BEFORE quota reservation
    const smallImgRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(
          makeFostaSubmitRequest({
            requestId: "req_small_dim",
            idempotencyKey: "idem_small_dim",
          }),
        ),
      }),
      liveEnv,
      { sessionId: "sess_bad_dim" },
    );
    expect(smallImgRes.status).toBe(400);
    expect(((await smallImgRes.json()) as TryOnErrorEnvelope).error.code).toBe("INVALID_REQUEST");
    expect(mockUpstream.callCount).toBe(0);

    // Case B: Declared MIME mismatch (image/png wrapping JPEG bytes) -> rejected BEFORE quota reservation
    const jpegSpoof = buildSyntheticJpegHeaderBytes(512, 768, 1);
    const mimeMismatchRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(
          makeFostaSubmitRequest({
            requestId: "req_mime_mismatch",
            idempotencyKey: "idem_mime_mismatch",
            personImages: {
              front: {
                ...validPerson256,
                mediaId: "person_front_256",
                mimeType: "image/png",
                base64Data: encodeBase64(jpegSpoof),
              },
            },
            garmentImages: {
              item_top_1: { front: validGarment256 },
            },
            consentGrant: {
              ...makeFostaSubmitRequest().consentGrant,
              selectedMediaIds: ["person_front_256", "top_front_256"],
            },
          }),
        ),
      }),
      liveEnv,
      { sessionId: "sess_bad_mime" },
    );
    expect(mimeMismatchRes.status).toBe(400);
    expect(((await mimeMismatchRes.json()) as TryOnErrorEnvelope).error.code).toBe(
      "INVALID_REQUEST",
    );
    expect(mockUpstream.callCount).toBe(0);

    // Case C: Oversized decoded image (> 2 MiB person cap) -> rejected BEFORE quota reservation
    const oversizedBytes = new Uint8Array(2 * 1024 * 1024 + 64);
    oversizedBytes.set(valid256PngBytes.subarray(0, Math.min(valid256PngBytes.length, 64)), 0);
    const oversizedRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(
          makeFostaSubmitRequest({
            requestId: "req_oversized",
            idempotencyKey: "idem_oversized",
            personImages: {
              front: {
                ...validPerson256,
                base64Data: encodeBase64(oversizedBytes),
              },
            },
            garmentImages: {
              item_top_1: { front: validGarment256 },
            },
            consentGrant: {
              ...makeFostaSubmitRequest().consentGrant,
              selectedMediaIds: ["person_front_256", "top_front_256"],
            },
          }),
        ),
      }),
      liveEnv,
      { sessionId: "sess_bad_size" },
    );
    expect(oversizedRes.status).toBe(413);
    expect(((await oversizedRes.json()) as TryOnErrorEnvelope).error.code).toBe(
      "PAYLOAD_TOO_LARGE",
    );
    expect(mockUpstream.callCount).toBe(0);

    // Confirm usedLiveRequests is still 0 via preflight, and a valid 256x320 submit consumes the 1 quota unit
    const preflightBeforeValid = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/preflight"),
      liveEnv,
      { sessionId: "sess_preflight_check" },
    );
    const preflightJson = (await preflightBeforeValid.json()) as {
      usedLiveRequests: number;
      remainingLiveRequests: number;
      quotaExhausted: boolean;
    };
    expect(preflightJson.usedLiveRequests).toBe(0);
    expect(preflightJson.remainingLiveRequests).toBe(1);
    expect(preflightJson.quotaExhausted).toBe(false);

    const validRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(
          makeFostaSubmitRequest({
            requestId: "req_valid_256",
            idempotencyKey: "idem_valid_256",
            personImages: { front: validPerson256 },
            garmentImages: { item_top_1: { front: validGarment256 } },
            consentGrant: {
              ...makeFostaSubmitRequest().consentGrant,
              selectedMediaIds: ["person_front_256", "top_front_256"],
            },
          }),
        ),
      }),
      liveEnv,
      { sessionId: "sess_valid_after_invalid" },
    );
    expect(validRes.status).toBe(202);
    expect(mockUpstream.callCount).toBe(1);
  });

  it("reports accurate quota remaining vs exhausted on GET /v2/tryon/preflight without calling upstream or leaking secrets", async () => {
    const durableStore = makeMemoryBackedDurableStore();
    const mockUpstream = createFostaResponsesMock("success");
    const secretKey = "secret-preflight-fosta-key-never-leak";
    const liveFostaProvider = new FostaTryOnProvider({
      apiKey: secretKey,
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

    const liveEnv: TryOnEnvConfig = {
      TRYON_PROVIDER: "fosta",
      TRYON_LIVE_ENABLED: "true",
      FOSTA_API_KEY: secretKey,
      FOSTA_APPROVE_QUOTA: "true",
      TRYON_LIVE_MAX_REQUESTS: "1",
      VTO_PROVIDER_CONFIG_VERSION: "v2-fosta-poc-1",
    };

    const preflightBeforeRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/preflight"),
      liveEnv,
      { sessionId: "sess_preflight_1" },
    );
    expect(preflightBeforeRes.status).toBe(200);
    const preflightBeforeText = await preflightBeforeRes.text();
    expect(preflightBeforeText).not.toContain(secretKey);
    expect(mockUpstream.callCount).toBe(0);

    const preflightBefore = JSON.parse(preflightBeforeText) as {
      provider: string;
      liveEnabled: boolean;
      offlineMockEnabled: boolean;
      readyForLivePocCall: boolean;
      usedLiveRequests: number;
      maxRequests: number;
      remainingLiveRequests: number;
      quotaExhausted: boolean;
      checks: {
        maxLiveRequests: number;
        usedLiveRequests: number;
        remainingLiveRequests: number;
        quotaExhausted: boolean;
      };
      blockers: string[];
    };
    expect(preflightBefore.provider).toBe("fosta");
    expect(preflightBefore.liveEnabled).toBe(true);
    expect(preflightBefore.offlineMockEnabled).toBe(false);
    expect(preflightBefore.readyForLivePocCall).toBe(true);
    expect(preflightBefore.blockers).toEqual([]);
    expect(preflightBefore.usedLiveRequests).toBe(0);
    expect(preflightBefore.maxRequests).toBe(1);
    expect(preflightBefore.remainingLiveRequests).toBe(1);
    expect(preflightBefore.quotaExhausted).toBe(false);
    expect(preflightBefore.checks.usedLiveRequests).toBe(0);
    expect(preflightBefore.checks.remainingLiveRequests).toBe(1);
    expect(preflightBefore.checks.quotaExhausted).toBe(false);

    // Consume the single live request
    const submitRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/jobs", {
        method: "POST",
        body: JSON.stringify(
          makeFostaSubmitRequest({
            requestId: "req_preflight_consume",
            idempotencyKey: "idem_preflight_consume",
          }),
        ),
      }),
      liveEnv,
      { sessionId: "sess_consume_1" },
    );
    expect(submitRes.status).toBe(202);
    expect(mockUpstream.callCount).toBe(1);

    const preflightAfterRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/preflight"),
      liveEnv,
      { sessionId: "sess_preflight_2" },
    );
    expect(preflightAfterRes.status).toBe(200);
    const preflightAfterText = await preflightAfterRes.text();
    expect(preflightAfterText).not.toContain(secretKey);
    expect(mockUpstream.callCount).toBe(1);

    const preflightAfter = JSON.parse(preflightAfterText) as typeof preflightBefore;
    expect(preflightAfter.usedLiveRequests).toBe(1);
    expect(preflightAfter.maxRequests).toBe(1);
    expect(preflightAfter.remainingLiveRequests).toBe(0);
    expect(preflightAfter.quotaExhausted).toBe(true);
    expect(preflightAfter.checks.usedLiveRequests).toBe(1);
    expect(preflightAfter.checks.remainingLiveRequests).toBe(0);
    expect(preflightAfter.checks.quotaExhausted).toBe(true);
  });

  it("serializes concurrent Promise.all requests from two different sessionIds when TRYON_LIVE_MAX_REQUESTS=1 so exactly 1 hits upstream and the other gets 503 BUDGET_EXCEEDED", async () => {
    const durableStore = makeMemoryBackedDurableStore();
    const baseMock = createFostaResponsesMock("success");
    let asyncUpstreamCalls = 0;
    const delayedProvider = new FostaTryOnProvider({
      apiKey: "simulated-live-fosta-key",
      endpoint: "https://mock.fosta.local/v1/responses",
      approvedLive: true,
      approvedQuotaUse: true,
      offlineMockMode: false,
      fetchImpl: async (url, init) => {
        asyncUpstreamCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 25));
        return baseMock.fetchImpl(url, init);
      },
    });
    const router = createTryOnRouter({
      store: durableStore,
      provider: delayedProvider,
    });

    const liveEnv: TryOnEnvConfig = {
      TRYON_PROVIDER: "fosta",
      TRYON_LIVE_ENABLED: "true",
      FOSTA_API_KEY: "simulated-live-fosta-key",
      FOSTA_APPROVE_QUOTA: "true",
      TRYON_LIVE_MAX_REQUESTS: "1",
      VTO_PROVIDER_CONFIG_VERSION: "v2-fosta-poc-1",
    };

    const reqA = new Request("https://fitcheck.local/v2/tryon/jobs", {
      method: "POST",
      body: JSON.stringify(
        makeFostaSubmitRequest({
          requestId: "req_race_a",
          idempotencyKey: "idem_race_a",
        }),
      ),
    });
    const reqB = new Request("https://fitcheck.local/v2/tryon/jobs", {
      method: "POST",
      body: JSON.stringify(
        makeFostaSubmitRequest({
          requestId: "req_race_b",
          idempotencyKey: "idem_race_b",
        }),
      ),
    });

    const [resA, resB] = await Promise.all([
      router.handle(reqA, liveEnv, { sessionId: "sess_race_a" }),
      router.handle(reqB, liveEnv, { sessionId: "sess_race_b" }),
    ]);

    const sortedStatuses = [resA.status, resB.status].sort((a, b) => a - b);
    expect(sortedStatuses).toEqual([202, 503]);

    const rejectedRes = resA.status === 503 ? resA : resB;
    const rejectedJson = (await rejectedRes.json()) as TryOnErrorEnvelope;
    expect(rejectedJson.error.code).toBe("BUDGET_EXCEEDED");
    expect(asyncUpstreamCalls).toBe(1);
    expect(delayedProvider.submitCallCount).toBe(1);

    const preflightRes = await router.handle(
      new Request("https://fitcheck.local/v2/tryon/preflight"),
      liveEnv,
      { sessionId: "sess_preflight_after_race" },
    );
    const preflight = (await preflightRes.json()) as {
      usedLiveRequests: number;
      remainingLiveRequests: number;
      quotaExhausted: boolean;
    };
    expect(preflight.usedLiveRequests).toBe(1);
    expect(preflight.remainingLiveRequests).toBe(0);
    expect(preflight.quotaExhausted).toBe(true);
  });
});
