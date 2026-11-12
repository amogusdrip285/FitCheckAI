// OWNER: Track C (Section 9 — High-fidelity Mock Responder for Official OpenAI `POST /v1/images/edits`)
// Strictly validates multipart/form-data `image[]`, `model`, `prompt`, `n=1`, `output_format`,
// and model-specific parameter rules (`response_format` forbidden, `input_fidelity` forbidden on `gpt-image-2*`).

import { buildLookbookMockPngBytes, encodeBase64, inspectImageBytes } from "./image_pipeline";

export type OpenAiMockScenario =
  | "success"
  | "insufficient_quota"
  | "rate_limit"
  | "invalid_api_key"
  | "org_not_verified"
  | "model_not_found"
  | "server_error"
  | "empty_data"
  | "corrupt_base64"
  | "corrupt_image_bytes"
  | "url_only_unexpected"
  | "network_timeout";

export interface CapturedOpenAiEditMultipartSummary {
  url: string;
  method: string;
  hasBearerAuth: boolean;
  organizationHeader: string | null;
  projectHeader: string | null;
  model: string | null;
  promptLength: number;
  promptMentionsBothReferenceImages: boolean;
  n: string | null;
  size: string | null;
  quality: string | null;
  outputFormat: string | null;
  inputFidelity: string | null;
  hasForbiddenResponseFormat: boolean;
  imageCount: number;
  imagePartNames: string[];
  imageMimes: string[];
  imageDimensions: Array<{ width: number; height: number }>;
}

export interface OpenAiMockController {
  readonly fetchImpl: typeof fetch;
  scenario: OpenAiMockScenario;
  callCount: number;
  lastCapturedRequest: CapturedOpenAiEditMultipartSummary | null;
}

function openAiErrorJson(
  status: number,
  message: string,
  type: string,
  code: string | null,
  param: string | null = null,
): Response {
  return new Response(
    JSON.stringify({
      error: {
        message,
        type,
        param,
        code,
      },
    }),
    {
      status,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "x-request-id": `req_mock_openai_${Date.now()}`,
      },
    },
  );
}

export function createOpenAiImageEditsMock(
  initialScenario: OpenAiMockScenario = "success",
): OpenAiMockController {
  const controller: OpenAiMockController = {
    scenario: initialScenario,
    callCount: 0,
    lastCapturedRequest: null,
    fetchImpl: async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      controller.callCount += 1;
      const req = input instanceof Request ? input : new Request(String(input), init);
      const url = req.url;
      const method = req.method.toUpperCase();

      if (method !== "POST" || !url.endsWith("/images/edits")) {
        return openAiErrorJson(
          404,
          `Invalid URL (${method} ${url}). Expected POST .../images/edits.`,
          "invalid_request_error",
          "invalid_url",
        );
      }

      const authHeader = req.headers.get("authorization") ?? "";
      const hasBearerAuth = /^Bearer\s+\S+$/.test(authHeader.trim());
      if (!hasBearerAuth || controller.scenario === "invalid_api_key") {
        return openAiErrorJson(
          401,
          "Incorrect API key provided.",
          "invalid_request_error",
          "invalid_api_key",
        );
      }

      if (controller.scenario === "network_timeout") {
        throw new TypeError("Mock network connection interrupted after request dispatch.");
      }

      let formData: FormData;
      try {
        formData = await req.formData();
      } catch {
        return openAiErrorJson(
          400,
          "Expected multipart/form-data body for /v1/images/edits.",
          "invalid_request_error",
          "invalid_multipart",
        );
      }

      const model = formData.get("model");
      const prompt = formData.get("prompt");
      const n = formData.get("n");
      const size = formData.get("size");
      const quality = formData.get("quality");
      const outputFormat = formData.get("output_format");
      const inputFidelity = formData.get("input_fidelity");
      const responseFormat = formData.get("response_format");

      const imageEntries = [
        ...formData.getAll("image[]"),
        ...formData.getAll("image"),
      ];

      const imagePartNames: string[] = [];
      const imageMimes: string[] = [];
      const imageDimensions: Array<{ width: number; height: number }> = [];
      let garmentColorSeed: [number, number, number] = [44, 82, 124];

      for (let i = 0; i < imageEntries.length; i += 1) {
        const entry = imageEntries[i];
        if (!(entry instanceof Blob)) {
          return openAiErrorJson(
            400,
            `Form field image[${i}] must be a binary file Blob.`,
            "invalid_request_error",
            "invalid_image_file",
            "image[]",
          );
        }
        const fileName = "name" in entry && typeof entry.name === "string" ? entry.name : `image_${i}`;
        imagePartNames.push(fileName);
        const bytes = new Uint8Array(await entry.arrayBuffer());
        const inspected = inspectImageBytes(bytes);
        if (!inspected) {
          return openAiErrorJson(
            400,
            `Uploaded image[${i}] (${fileName}) is not a valid PNG, JPEG, or WebP file.`,
            "invalid_request_error",
            "invalid_image_format",
            "image[]",
          );
        }
        imageMimes.push(inspected.mimeType);
        imageDimensions.push({
          width: inspected.effectiveWidth,
          height: inspected.effectiveHeight,
        });
        if (i === 1 && bytes.length >= 16) {
          // Derive deterministic garment tint from garment image bytes so distinct garments yield distinct PNGs
          const b1 = bytes[bytes.length - 7] ?? 60;
          const b2 = bytes[bytes.length - 5] ?? 95;
          const b3 = bytes[bytes.length - 3] ?? 140;
          garmentColorSeed = [
            40 + (b1 % 140),
            45 + (b2 % 130),
            55 + (b3 % 140),
          ];
        }
      }

      const promptStr = typeof prompt === "string" ? prompt : "";
      const modelStr = typeof model === "string" ? model : null;

      controller.lastCapturedRequest = {
        url,
        method,
        hasBearerAuth,
        organizationHeader: req.headers.get("openai-organization"),
        projectHeader: req.headers.get("openai-project"),
        model: modelStr,
        promptLength: promptStr.length,
        promptMentionsBothReferenceImages:
          promptStr.includes("Image 1") && promptStr.includes("Image 2"),
        n: typeof n === "string" ? n : null,
        size: typeof size === "string" ? size : null,
        quality: typeof quality === "string" ? quality : null,
        outputFormat: typeof outputFormat === "string" ? outputFormat : null,
        inputFidelity: typeof inputFidelity === "string" ? inputFidelity : null,
        hasForbiddenResponseFormat: responseFormat !== null,
        imageCount: imageEntries.length,
        imagePartNames,
        imageMimes,
        imageDimensions,
      };

      // Enforce real OpenAI GPT Image API parameter invariants:
      if (responseFormat !== null) {
        return openAiErrorJson(
          400,
          "Unknown parameter: 'response_format'. GPT Image models always return base64-encoded images and use 'output_format'.",
          "invalid_request_error",
          "unknown_parameter",
          "response_format",
        );
      }

      if (!modelStr) {
        return openAiErrorJson(
          400,
          "Missing required parameter: 'model'.",
          "invalid_request_error",
          "missing_required_parameter",
          "model",
        );
      }

      if (modelStr.startsWith("gpt-image-2") && inputFidelity !== null) {
        return openAiErrorJson(
          400,
          `Unknown parameter: 'input_fidelity' is not supported on ${modelStr} (high fidelity is native).`,
          "invalid_request_error",
          "unknown_parameter",
          "input_fidelity",
        );
      }

      if (imageEntries.length < 2) {
        return openAiErrorJson(
          400,
          "Virtual Try-On image edit requires at least 2 reference images in 'image[]' (person + garment).",
          "invalid_request_error",
          "invalid_image_count",
          "image[]",
        );
      }

      if (!promptStr.trim()) {
        return openAiErrorJson(
          400,
          "Missing required parameter: 'prompt'.",
          "invalid_request_error",
          "missing_required_parameter",
          "prompt",
        );
      }

      if (n !== "1") {
        return openAiErrorJson(
          400,
          "Cost guard mock requires n='1'.",
          "invalid_request_error",
          "invalid_n",
          "n",
        );
      }

      // Deterministic fault injection scenarios
      switch (controller.scenario) {
        case "insufficient_quota":
          return openAiErrorJson(
            429,
            "You exceeded your current quota, please check your plan and billing details.",
            "insufficient_quota",
            "insufficient_quota",
          );
        case "rate_limit":
          return openAiErrorJson(
            429,
            "Rate limit reached for images per minute in organization.",
            "rate_limit_error",
            "rate_limit_exceeded",
          );
        case "org_not_verified":
          return openAiErrorJson(
            403,
            "Your organization must be verified to use this GPT Image model.",
            "permission_error",
            "organization_not_verified",
          );
        case "model_not_found":
          return openAiErrorJson(
            404,
            `The model '${modelStr}' does not exist or you do not have access to it.`,
            "invalid_request_error",
            "model_not_found",
          );
        case "server_error":
          return openAiErrorJson(
            503,
            "The server had an error while processing your request.",
            "server_error",
            "server_error",
          );
        case "empty_data":
          return new Response(
            JSON.stringify({
              created: Math.floor(Date.now() / 1000),
              output_format: "png",
              data: [],
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          );
        case "corrupt_base64":
          return new Response(
            JSON.stringify({
              created: Math.floor(Date.now() / 1000),
              output_format: "png",
              data: [{ b64_json: "%%%NOT_VALID_BASE64%%%" }],
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          );
        case "corrupt_image_bytes":
          return new Response(
            JSON.stringify({
              created: Math.floor(Date.now() / 1000),
              output_format: "png",
              data: [{ b64_json: encodeBase64(new TextEncoder().encode("NOT_AN_IMAGE_HEADER_AT_ALL_1234567890")) }],
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          );
        case "url_only_unexpected":
          return new Response(
            JSON.stringify({
              created: Math.floor(Date.now() / 1000),
              data: [{ url: "https://cdn.example.invalid/unexpected.png" }],
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          );
        case "success":
        default: {
          const mockPngBytes = buildLookbookMockPngBytes({
            width: 192,
            height: 256,
            garmentRgb: garmentColorSeed,
          });
          const b64Json = encodeBase64(mockPngBytes);
          return new Response(
            JSON.stringify({
              created: Math.floor(Date.now() / 1000),
              background: "opaque",
              output_format: "png",
              size: typeof size === "string" ? size : "1024x1536",
              quality: typeof quality === "string" ? quality : "medium",
              data: [{ b64_json: b64Json }],
              usage: {
                total_tokens: 6210,
                input_tokens: 2050,
                output_tokens: 4160,
                input_tokens_details: {
                  text_tokens: 210,
                  image_tokens: 1840,
                },
              },
            }),
            {
              status: 200,
              headers: {
                "content-type": "application/json; charset=utf-8",
                "x-request-id": `req_mock_openai_${controller.callCount}`,
              },
            },
          );
        }
      }
    },
  };
  return controller;
}
