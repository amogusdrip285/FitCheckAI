// OWNER: Track C (Offline High-Fidelity Mock Responder for FOSTA `POST /v1/responses`)
// Validates Responses API JSON structure (`model: "gpt-6-luna"`, `tools: [{ type: "image_generation" }]`,
// `tool_choice: { type: "image_generation" }`, `input[0].content` data-URL ordering) and returns
// valid PNG bytes inside `output: [{ type: "image_generation_call", result: "<base64>" }]`.

import {
  buildLookbookMockPngBytes,
  decodeStrictBase64,
  encodeBase64,
  inspectImageBytes,
} from "./image_pipeline";
import { detectFostaImageMime } from "./fosta_provider";
import type { ImageView, TryOnPresentationMode } from "./types";

export type FostaMockScenario =
  | "success"
  | "unauthorized"
  | "insufficient_quota"
  | "forbidden"
  | "rate_limit"
  | "server_error"
  | "incomplete_response"
  | "no_image_output"
  | "api_error_in_200"
  | "corrupt_base64"
  | "corrupt_image_bytes"
  | "network_timeout";

export interface CapturedFostaResponsesSummary {
  url: string;
  method: string;
  hasBearerAuth: boolean;
  model: string | null;
  prompt: string;
  detectedMode: TryOnPresentationMode | null;
  detectedView: ImageView | null;
  toolsForcedImageGeneration: boolean;
  imageCount: number;
  imageMimes: string[];
}

export interface FostaMockController {
  readonly fetchImpl: typeof fetch;
  scenario: FostaMockScenario;
  callCount: number;
  lastCapturedRequest: CapturedFostaResponsesSummary | null;
}

function fostaJson(body: unknown, status = 200, callCount = 1): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "x-request-id": `req_mock_fosta_${callCount}`,
    },
  });
}

const DATA_IMAGE_URL_REGEX = /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/]+={0,2})$/;

export function createFostaResponsesMock(
  initialScenario: FostaMockScenario = "success",
): FostaMockController {
  const controller: FostaMockController = {
    scenario: initialScenario,
    callCount: 0,
    lastCapturedRequest: null,
    fetchImpl: async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      controller.callCount += 1;
      const req = input instanceof Request ? input : new Request(String(input), init);
      const url = req.url;
      const method = req.method.toUpperCase();

      if (method !== "POST" || !url.endsWith("/responses")) {
        return fostaJson(
          { error: { message: `Invalid FOSTA route: ${method} ${url}` } },
          404,
          controller.callCount,
        );
      }

      const authHeader = req.headers.get("authorization") ?? "";
      const hasBearerAuth = /^Bearer\s+\S+$/.test(authHeader.trim());
      if (!hasBearerAuth || controller.scenario === "unauthorized") {
        return fostaJson(
          { error: { message: "Invalid or missing FOSTA API key." } },
          401,
          controller.callCount,
        );
      }

      if (controller.scenario === "network_timeout") {
        throw new TypeError("Mock FOSTA network connection dropped before response.");
      }

      let body: Record<string, unknown>;
      try {
        body = (await req.json()) as Record<string, unknown>;
      } catch {
        return fostaJson(
          { error: { message: "Expected application/json body for /v1/responses." } },
          400,
          controller.callCount,
        );
      }

      const model = typeof body.model === "string" ? body.model : null;
      const tools = Array.isArray(body.tools) ? body.tools : [];
      const toolChoice = body.tool_choice as { type?: unknown } | undefined;
      const toolsForcedImageGeneration =
        tools.some(
          (t) =>
            Boolean(t) &&
            typeof t === "object" &&
            (t as { type?: unknown }).type === "image_generation",
        ) && toolChoice?.type === "image_generation";

      const inputArr = Array.isArray(body.input) ? body.input : [];
      const firstMsg = inputArr[0] as { role?: unknown; content?: unknown } | undefined;
      const contentArr = Array.isArray(firstMsg?.content) ? firstMsg!.content : [];

      if (
        !model ||
        !toolsForcedImageGeneration ||
        firstMsg?.role !== "user" ||
        contentArr.length < 3 ||
        contentArr.length > 4
      ) {
        return fostaJson(
          { error: { message: "Invalid FOSTA Responses payload shape." } },
          400,
          controller.callCount,
        );
      }

      const textPart = contentArr[0] as { type?: unknown; text?: unknown } | undefined;
      if (textPart?.type !== "input_text" || typeof textPart.text !== "string") {
        return fostaJson(
          { error: { message: "First content part must be input_text." } },
          400,
          controller.callCount,
        );
      }
      const prompt = textPart.text;
      const imageMimes: string[] = [];
      let topColorSeed: [number, number, number] = [48, 84, 126];
      let bottomColorSeed: [number, number, number] = [56, 54, 52];

      for (let i = 1; i < contentArr.length; i += 1) {
        const imgPart = contentArr[i] as { type?: unknown; image_url?: unknown } | undefined;
        if (imgPart?.type !== "input_image" || typeof imgPart.image_url !== "string") {
          return fostaJson(
            { error: { message: `Content part[${i}] must be input_image.` } },
            400,
            controller.callCount,
          );
        }
        const match = DATA_IMAGE_URL_REGEX.exec(imgPart.image_url);
        if (!match || !match[1] || !match[2]) {
          return fostaJson(
            { error: { message: `Content part[${i}] must be a base64 data:image/* URL.` } },
            400,
            controller.callCount,
          );
        }
        const decoded = decodeStrictBase64(match[2]);
        const detectedMime = decoded ? detectFostaImageMime(decoded) : null;
        if (!decoded || !detectedMime || (!inspectImageBytes(decoded) && decoded.byteLength < 16)) {
          return fostaJson(
            { error: { message: `Content part[${i}] image bytes failed validation.` } },
            400,
            controller.callCount,
          );
        }
        imageMimes.push(detectedMime);

        if (i === 2 && decoded.byteLength >= 16) {
          const b1 = decoded[decoded.byteLength - 7] ?? 65;
          const b2 = decoded[decoded.byteLength - 5] ?? 95;
          const b3 = decoded[decoded.byteLength - 3] ?? 135;
          if (prompt.includes("BOTTOM garment") && !prompt.includes("TOP garment")) {
            bottomColorSeed = [35 + (b1 % 120), 40 + (b2 % 120), 45 + (b3 % 120)];
          } else {
            topColorSeed = [42 + (b1 % 140), 48 + (b2 % 130), 56 + (b3 % 140)];
          }
        } else if (i === 3 && decoded.byteLength >= 16) {
          const b1 = decoded[decoded.byteLength - 6] ?? 50;
          const b2 = decoded[decoded.byteLength - 4] ?? 70;
          const b3 = decoded[decoded.byteLength - 2] ?? 90;
          bottomColorSeed = [35 + (b1 % 120), 40 + (b2 % 120), 45 + (b3 % 120)];
        }
      }

      const detectedMode: TryOnPresentationMode | null = prompt.includes("MODE=STUDIO_WHITE")
        ? "STUDIO_WHITE"
        : prompt.includes("MODE=ORIGINAL_PHOTO")
          ? "ORIGINAL_PHOTO"
          : null;
      const detectedView: ImageView | null = prompt.includes("VIEW=BACK")
        ? "BACK"
        : prompt.includes("VIEW=FRONT")
          ? "FRONT"
          : null;

      controller.lastCapturedRequest = {
        url,
        method,
        hasBearerAuth,
        model,
        prompt,
        detectedMode,
        detectedView,
        toolsForcedImageGeneration,
        imageCount: imageMimes.length,
        imageMimes,
      };

      switch (controller.scenario) {
        case "insufficient_quota":
          return fostaJson({ error: { message: "Quota exhausted." } }, 402, controller.callCount);
        case "forbidden":
          return fostaJson({ error: { message: "Access denied." } }, 403, controller.callCount);
        case "rate_limit":
          return fostaJson({ error: { message: "Rate limited." } }, 429, controller.callCount);
        case "server_error":
          return fostaJson({ error: { message: "Upstream failure." } }, 502, controller.callCount);
        case "incomplete_response":
          return fostaJson({ id: `resp_${controller.callCount}`, status: "incomplete", output: [] }, 200, controller.callCount);
        case "no_image_output":
          return fostaJson(
            {
              id: `resp_${controller.callCount}`,
              status: "completed",
              error: null,
              output: [{ type: "message", content: [] }],
            },
            200,
            controller.callCount,
          );
        case "api_error_in_200":
          return fostaJson(
            {
              id: `resp_${controller.callCount}`,
              status: "completed",
              error: { code: "generation_failed", message: "Failed." },
              output: [],
            },
            200,
            controller.callCount,
          );
        case "corrupt_base64":
          return fostaJson(
            {
              id: `resp_${controller.callCount}`,
              status: "completed",
              error: null,
              output: [{ type: "image_generation_call", result: "###INVALID_B64###" }],
            },
            200,
            controller.callCount,
          );
        case "corrupt_image_bytes":
          return fostaJson(
            {
              id: `resp_${controller.callCount}`,
              status: "completed",
              error: null,
              output: [
                {
                  type: "image_generation_call",
                  result: encodeBase64(new TextEncoder().encode("NOT_A_VALID_IMAGE_HEADER_BYTES_123456")),
                },
              ],
            },
            200,
            controller.callCount,
          );
        case "success":
        default: {
          const bgRgb: [number, number, number] =
            detectedMode === "STUDIO_WHITE" ? [250, 250, 250] : [232, 222, 208];
          const mockPngBytes = buildLookbookMockPngBytes({
            width: 192,
            height: 256,
            backgroundRgb: bgRgb,
            garmentRgb: topColorSeed,
            trouserRgb: bottomColorSeed,
          });
          return fostaJson(
            {
              id: `resp_mock_fosta_${controller.callCount}`,
              status: "completed",
              error: null,
              output: [
                {
                  type: "image_generation_call",
                  status: "completed",
                  result: encodeBase64(mockPngBytes),
                },
              ],
            },
            200,
            controller.callCount,
          );
        }
      }
    },
  };

  return controller;
}
