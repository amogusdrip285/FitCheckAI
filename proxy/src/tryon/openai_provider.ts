// OWNER: Track C (Deliverable A — Official OpenAI Images Edit API `POST /v1/images/edits` Provider Adapter)
// Docs verified:
// - https://developers.openai.com/api/docs/guides/image-generation
// - https://developers.openai.com/api/docs/guides/image-prompting
// - https://developers.openai.com/api/reference/resources/images/methods/edit

import {
  decodeStrictBase64,
  extensionForMime,
  inspectImageBytes,
  MAX_IMAGE_DIMENSION_PX,
  MIN_LIVE_VTO_DIMENSION_PX,
  sha256HexBytes,
  validateTryOnImageBuffer,
} from "./image_pipeline";
import { buildOpenAiTryOnPrompt, OPENAI_VTO_PROMPT_VERSION } from "./openai_prompt";
import { UpstreamAmbiguousTimeoutError, UpstreamDeterministicError } from "./providers";
import type {
  Category,
  OpenAiUsageReport,
  RemoteDeletionState,
  SupportedTryOnMime,
  TryOnErrorCode,
  TryOnSource,
  UpstreamViewPollOutput,
  UpstreamViewSubmitInput,
  UpstreamViewSubmitOutput,
  UpstreamVtoProvider,
} from "./types";

export const MAX_OPENAI_PERSON_BYTES = 2 * 1024 * 1024; // 2 MiB CT-07 cap (well within OpenAI 50 MB limit)
export const MAX_OPENAI_GARMENT_BYTES = 1 * 1024 * 1024; // 1 MiB CT-07 cap

export type OpenAiQualityTier = "low" | "medium" | "high" | "auto";
export type OpenAiOutputFormat = "png" | "jpeg" | "webp";
export type OpenAiInputFidelityOption = "auto" | "high" | "low" | "omit";

export interface OpenAiModelSpec {
  readonly modelId: string;
  readonly family: "gpt-image-2.x" | "gpt-image-1.x";
  readonly inputFidelitySupport: "none" | "low_only" | "low_or_high";
  readonly supportsArbitraryDiv16Size: boolean;
  readonly textInputUsdPer1M: number;
  readonly imageInputUsdPer1M: number;
  readonly imageOutputUsdPer1M: number;
  /** Conservative per-request upper-bound USD estimate for 2 reference images + 1 output image. */
  readonly estimatedMaxUsdByQuality: Record<OpenAiQualityTier, number>;
}

/**
 * Verified OpenAI GPT Image editing models & token rates (verified November 2026).
 * Model ID is ALWAYS selected via external configuration (`OPENAI_IMAGE_MODEL`), never scattered in code.
 */
export const VERIFIED_OPENAI_IMAGE_MODELS: Readonly<Record<string, OpenAiModelSpec>> = {
  "gpt-image-2.6-sunburst": {
    modelId: "gpt-image-2.6-sunburst",
    family: "gpt-image-2.x",
    inputFidelitySupport: "none",
    supportsArbitraryDiv16Size: true,
    textInputUsdPer1M: 5.0,
    imageInputUsdPer1M: 8.0,
    imageOutputUsdPer1M: 30.0,
    estimatedMaxUsdByQuality: { low: 0.04, medium: 0.1, high: 0.28, auto: 0.28 },
  },
  "gpt-image-2.6-flare": {
    modelId: "gpt-image-2.6-flare",
    family: "gpt-image-2.x",
    inputFidelitySupport: "none",
    supportsArbitraryDiv16Size: true,
    textInputUsdPer1M: 5.0,
    imageInputUsdPer1M: 8.0,
    imageOutputUsdPer1M: 30.0,
    estimatedMaxUsdByQuality: { low: 0.035, medium: 0.085, high: 0.24, auto: 0.24 },
  },
  "gpt-image-2.5-sunburst": {
    modelId: "gpt-image-2.5-sunburst",
    family: "gpt-image-2.x",
    inputFidelitySupport: "none",
    supportsArbitraryDiv16Size: true,
    textInputUsdPer1M: 5.0,
    imageInputUsdPer1M: 8.0,
    imageOutputUsdPer1M: 30.0,
    estimatedMaxUsdByQuality: { low: 0.04, medium: 0.1, high: 0.28, auto: 0.28 },
  },
  "gpt-image-2.5-flare": {
    modelId: "gpt-image-2.5-flare",
    family: "gpt-image-2.x",
    inputFidelitySupport: "none",
    supportsArbitraryDiv16Size: true,
    textInputUsdPer1M: 5.0,
    imageInputUsdPer1M: 8.0,
    imageOutputUsdPer1M: 30.0,
    estimatedMaxUsdByQuality: { low: 0.035, medium: 0.085, high: 0.24, auto: 0.24 },
  },
  "gpt-image-2": {
    modelId: "gpt-image-2",
    family: "gpt-image-2.x",
    inputFidelitySupport: "none",
    supportsArbitraryDiv16Size: true,
    textInputUsdPer1M: 2.5,
    imageInputUsdPer1M: 4.0,
    imageOutputUsdPer1M: 15.0,
    estimatedMaxUsdByQuality: { low: 0.025, medium: 0.06, high: 0.16, auto: 0.16 },
  },
  "gpt-image-1.5": {
    modelId: "gpt-image-1.5",
    family: "gpt-image-1.x",
    inputFidelitySupport: "low_or_high",
    supportsArbitraryDiv16Size: false,
    textInputUsdPer1M: 5.0,
    imageInputUsdPer1M: 8.0,
    imageOutputUsdPer1M: 32.0,
    estimatedMaxUsdByQuality: { low: 0.09, medium: 0.14, high: 0.3, auto: 0.3 },
  },
  "gpt-image-1-mini": {
    modelId: "gpt-image-1-mini",
    family: "gpt-image-1.x",
    inputFidelitySupport: "low_only",
    supportsArbitraryDiv16Size: false,
    textInputUsdPer1M: 2.0,
    imageInputUsdPer1M: 2.5,
    imageOutputUsdPer1M: 8.0,
    estimatedMaxUsdByQuality: { low: 0.015, medium: 0.03, high: 0.08, auto: 0.08 },
  },
};

export const RETIRED_OR_UNSUPPORTED_OPENAI_MODELS = new Set([
  "dall-e-2",
  "dall-e-3",
  "gpt-image-1",
]);

const STANDARD_IMAGE_SIZES = new Set(["1024x1536", "1024x1024", "1536x1024", "auto"]);

export interface OpenAiImageEditUsageWire {
  total_tokens?: number;
  input_tokens?: number;
  output_tokens?: number;
  input_tokens_details?: {
    text_tokens?: number;
    image_tokens?: number;
  };
}

export interface OpenAiImageEditResponseWire {
  created?: number;
  background?: string;
  output_format?: string;
  size?: string;
  quality?: string;
  data?: Array<{
    b64_json?: string;
    url?: string;
    revised_prompt?: string;
  }>;
  usage?: OpenAiImageEditUsageWire;
}

interface OpenAiErrorEnvelopeWire {
  error?: {
    message?: string;
    type?: string;
    param?: string | null;
    code?: string | null;
  };
}

export interface OpenAiTryOnProviderConfig {
  apiKey: string;
  model: string;
  baseUrl?: string;
  organizationId?: string;
  projectId?: string;
  size?: string;
  quality?: OpenAiQualityTier;
  outputFormat?: OpenAiOutputFormat;
  inputFidelity?: OpenAiInputFidelityOption;
  allowCustomModel?: boolean;
  timeoutMs?: number;
  minInputDimensionPx?: number;
  /**
   * When true, marks outputs as `source: "FAKE"` and `remoteDeletionState: "NOT_UPLOADED"`
   * so offline end-to-end tests exercise 100% of `OpenAITryOnProvider` without claiming live AI output.
   */
  offlineMockMode?: boolean;
  fetchImpl?: typeof fetch;
}

export function redactSecretsFromDiagnostic(raw: string): string {
  return raw
    .replace(/sk-[A-Za-z0-9_-]{8,}/g, "[REDACTED_OPENAI_KEY]")
    .replace(/Bearer\s+[A-Za-z0-9._-]+/gi, "Bearer [REDACTED]")
    .replace(/data:image\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+/g, "[REDACTED_BASE64]")
    .slice(0, 280);
}

export function validateOpenAiModelSelection(
  rawModel: string | undefined,
  allowCustomModel = false,
):
  | { ok: true; modelId: string; spec: OpenAiModelSpec | null }
  | { ok: false; code: TryOnErrorCode; message: string } {
  const modelId = (rawModel ?? "").trim();
  if (!modelId) {
    return {
      ok: false,
      code: "UNSUPPORTED_CAPABILITY",
      message:
        "Chưa cấu hình OPENAI_IMAGE_MODEL ở phía máy chủ; vui lòng chỉ định model ID hợp lệ trước khi chạy.",
    };
  }
  if (RETIRED_OR_UNSUPPORTED_OPENAI_MODELS.has(modelId.toLowerCase())) {
    return {
      ok: false,
      code: "UNSUPPORTED_CAPABILITY",
      message: `Model '${modelId}' đã ngừng hoạt động hoặc không hỗ trợ chỉnh sửa nhiều ảnh tham chiếu cho VTO.`,
    };
  }
  const knownSpec = VERIFIED_OPENAI_IMAGE_MODELS[modelId] ?? null;
  if (!knownSpec && !allowCustomModel) {
    return {
      ok: false,
      code: "UNSUPPORTED_CAPABILITY",
      message: `Model '${modelId}' chưa nằm trong danh sách GPT Image đã xác minh. Đặt OPENAI_ALLOW_CUSTOM_MODEL=true nếu bạn đã kiểm tra quyền truy cập model này.`,
    };
  }
  return { ok: true, modelId, spec: knownSpec };
}

export function resolveEffectiveInputFidelity(
  spec: OpenAiModelSpec | null,
  configured: OpenAiInputFidelityOption = "auto",
): "high" | "low" | null {
  if (configured === "omit") {
    return null;
  }
  if (configured === "auto") {
    if (!spec) {
      // Safe default for unknown custom 2.x models: omit to avoid 400 unknown_parameter
      return null;
    }
    if (spec.inputFidelitySupport === "low_or_high") {
      return "high";
    }
    return null;
  }
  // Explicit "high" or "low" requested
  if (spec && spec.inputFidelitySupport === "none") {
    // gpt-image-2.x processes reference images at high fidelity natively and rejects input_fidelity parameter
    return null;
  }
  if (spec && spec.inputFidelitySupport === "low_only" && configured === "high") {
    throw new UpstreamDeterministicError(
      "UNSUPPORTED_CAPABILITY",
      `Model '${spec.modelId}' chỉ hỗ trợ input_fidelity='low', không hỗ trợ 'high'.`,
    );
  }
  return configured;
}

/**
 * Estimates conservative upper-bound USD cost for a single `POST /v1/images/edits` call (`n=1`).
 * Returns `null` if the model has no verified pricing table (requiring explicit human override).
 */
export function estimateOpenAiEditMaxUsd(params: {
  model: string;
  quality?: OpenAiQualityTier;
  allowCustomModel?: boolean;
}): number | null {
  const spec = VERIFIED_OPENAI_IMAGE_MODELS[params.model.trim()];
  if (!spec) {
    return null;
  }
  const quality = params.quality ?? "medium";
  return spec.estimatedMaxUsdByQuality[quality] ?? spec.estimatedMaxUsdByQuality.auto;
}

export function computeOpenAiUsageUsd(
  model: string,
  usage: OpenAiImageEditUsageWire | undefined,
  fallbackEstimate: number | null,
): number | null {
  const spec = VERIFIED_OPENAI_IMAGE_MODELS[model.trim()];
  if (!spec) {
    return fallbackEstimate;
  }
  if (!usage) {
    return fallbackEstimate;
  }
  const textIn = usage.input_tokens_details?.text_tokens ?? 0;
  const imgIn =
    usage.input_tokens_details?.image_tokens ??
    Math.max(0, (usage.input_tokens ?? 0) - textIn);
  const outTokens = usage.output_tokens ?? 0;
  if (textIn === 0 && imgIn === 0 && outTokens === 0) {
    return fallbackEstimate;
  }
  const usd =
    (textIn / 1_000_000) * spec.textInputUsdPer1M +
    (imgIn / 1_000_000) * spec.imageInputUsdPer1M +
    (outTokens / 1_000_000) * spec.imageOutputUsdPer1M;
  return Number(usd.toFixed(6));
}

function validateSizeOption(size: string, spec: OpenAiModelSpec | null): void {
  if (STANDARD_IMAGE_SIZES.has(size)) {
    return;
  }
  const customMatch = /^(\d{3,4})x(\d{3,4})$/.exec(size);
  if (customMatch && (spec === null || spec.supportsArbitraryDiv16Size)) {
    const w = Number(customMatch[1]);
    const h = Number(customMatch[2]);
    if (w >= 256 && h >= 256 && w <= 3840 && h <= 3840 && w % 16 === 0 && h % 16 === 0) {
      return;
    }
  }
  throw new UpstreamDeterministicError(
    "INVALID_REQUEST",
    `Kích thước đầu ra OPENAI_IMAGE_SIZE='${size}' không hợp lệ cho model hiện tại.`,
  );
}

function mapOutputMime(
  declaredFormat: string | undefined,
  fallbackFormat: OpenAiOutputFormat,
): SupportedTryOnMime {
  const norm = (declaredFormat ?? fallbackFormat).toLowerCase();
  if (norm === "jpeg" || norm === "jpg") return "image/jpeg";
  if (norm === "webp") return "image/webp";
  return "image/png";
}

function mapOpenAiHttpError(
  status: number,
  parsed: OpenAiErrorEnvelopeWire | null,
): UpstreamDeterministicError {
  const errCode = (parsed?.error?.code ?? "").toLowerCase();
  const errType = (parsed?.error?.type ?? "").toLowerCase();
  const rawMessage = redactSecretsFromDiagnostic(parsed?.error?.message ?? "");

  if (
    status === 402 ||
    errCode === "insufficient_quota" ||
    errCode === "billing_hard_limit_reached" ||
    errType === "insufficient_quota" ||
    /insufficient_quota|exceeded your current quota|billing hard limit/i.test(rawMessage)
  ) {
    return new UpstreamDeterministicError(
      "INSUFFICIENT_QUOTA",
      "Tài khoản OpenAI API không đủ hạn mức tín dụng (INSUFFICIENT_QUOTA). Hệ thống giữ nguyên provider đã chọn và không tự động chuyển sang dịch vụ khác.",
      false,
    );
  }

  if (status === 401 || errCode === "invalid_api_key") {
    return new UpstreamDeterministicError(
      "UNAUTHORIZED",
      "Xác thực OpenAI API thất bại (API key không hợp lệ hoặc đã thu hồi).",
      false,
    );
  }

  if (
    status === 403 ||
    errCode === "organization_not_verified" ||
    errType === "permission_error" ||
    /organization.*verif|must be verified/i.test(rawMessage)
  ) {
    return new UpstreamDeterministicError(
      "FORBIDDEN",
      "Tài khoản/Project OpenAI chưa có quyền truy cập model ảnh này hoặc cần hoàn tất API Organization Verification.",
      false,
    );
  }

  if (status === 429) {
    return new UpstreamDeterministicError(
      "RATE_LIMITED",
      "OpenAI Images API đang giới hạn tần suất yêu cầu (Rate Limit). Vui lòng thử lại sau ít phút.",
      true,
    );
  }

  if (status === 404 || errCode === "model_not_found") {
    return new UpstreamDeterministicError(
      "UNSUPPORTED_CAPABILITY",
      "Model OpenAI Images đã cấu hình hiện không khả dụng cho tài khoản này.",
      false,
    );
  }

  if (status === 413) {
    return new UpstreamDeterministicError(
      "PAYLOAD_TOO_LARGE",
      "Dung lượng ảnh gửi tới OpenAI vượt quá giới hạn cho phép.",
      false,
    );
  }

  if (status >= 400 && status < 500) {
    return new UpstreamDeterministicError(
      "INVALID_REQUEST",
      rawMessage
        ? `OpenAI từ chối yêu cầu chỉnh sửa ảnh: ${rawMessage}`
        : "OpenAI từ chối yêu cầu chỉnh sửa ảnh do tham số hoặc nội dung không hợp lệ.",
      false,
    );
  }

  // 5xx: Do NOT mark retryable=true for automatic client re-POST on paid image generation
  return new UpstreamDeterministicError(
    "UPSTREAM_ERROR",
    "Dịch vụ OpenAI Images API tạm thời gặp sự cố máy chủ.",
    false,
  );
}

/**
 * Real OpenAI Images Editing adapter (`POST /v1/images/edits`).
 * Sends person base photo (`image[0]`) + garment reference photo (`image[1]`) + versioned VTO prompt
 * via `multipart/form-data` with `n=1`, validates decoded image output, and maps usage/errors safely.
 */
export class OpenAITryOnProvider implements UpstreamVtoProvider {
  public readonly providerId = "openai";
  public readonly source: TryOnSource;
  public readonly supportsMultiGarment = false;
  public readonly maxGarmentsPerJob = 1; // Milestone 1 & 2: single garment per edit pass
  public readonly supportedCategories = new Set<Category>([
    "TOP",
    "BOTTOM",
    "DRESS",
    "OUTERWEAR",
  ]);

  public readonly modelId: string;
  public readonly modelSpec: OpenAiModelSpec | null;
  public submitCallCount = 0;
  public lastUsageReport: OpenAiUsageReport | null = null;

  private readonly apiKey: string;
  private readonly baseUrl: string;
  private readonly organizationId?: string;
  private readonly projectId?: string;
  private readonly size: string;
  private readonly quality: OpenAiQualityTier;
  private readonly outputFormat: OpenAiOutputFormat;
  private readonly inputFidelity: OpenAiInputFidelityOption;
  private readonly timeoutMs: number;
  private readonly minInputDimensionPx: number;
  private readonly remoteDeletionState: RemoteDeletionState;
  private readonly fetchFn: typeof fetch;

  constructor(config: OpenAiTryOnProviderConfig) {
    if (!config.apiKey || config.apiKey.trim().length === 0) {
      throw new Error("OPENAI_API_KEY is required to instantiate OpenAITryOnProvider.");
    }
    const modelCheck = validateOpenAiModelSelection(config.model, config.allowCustomModel ?? false);
    if (!modelCheck.ok) {
      throw new UpstreamDeterministicError(modelCheck.code, modelCheck.message, false);
    }

    const quality = config.quality ?? "medium";
    if (quality !== "low" && quality !== "medium" && quality !== "high" && quality !== "auto") {
      throw new UpstreamDeterministicError(
        "INVALID_REQUEST",
        `Giá trị OPENAI_IMAGE_QUALITY='${String(quality)}' không hợp lệ (chỉ chấp nhận low|medium|high|auto).`,
      );
    }

    const outputFormat = config.outputFormat ?? "png";
    if (outputFormat !== "png" && outputFormat !== "jpeg" && outputFormat !== "webp") {
      throw new UpstreamDeterministicError(
        "INVALID_REQUEST",
        `Giá trị OPENAI_OUTPUT_FORMAT='${String(outputFormat)}' không hợp lệ (chỉ chấp nhận png|jpeg|webp).`,
      );
    }

    const size = (config.size ?? "1024x1536").trim();
    validateSizeOption(size, modelCheck.spec);

    this.apiKey = config.apiKey.trim();
    this.modelId = modelCheck.modelId;
    this.modelSpec = modelCheck.spec;
    this.baseUrl = (config.baseUrl ?? "https://api.openai.com/v1").replace(/\/$/, "");
    this.organizationId = config.organizationId?.trim() || undefined;
    this.projectId = config.projectId?.trim() || undefined;
    this.size = size;
    this.quality = quality;
    this.outputFormat = outputFormat;
    this.inputFidelity = config.inputFidelity ?? "auto";
    this.timeoutMs = config.timeoutMs ?? 90_000;
    this.minInputDimensionPx = config.minInputDimensionPx ?? MIN_LIVE_VTO_DIMENSION_PX;
    this.source = config.offlineMockMode ? "FAKE" : "AI_LIVE";
    // Honesty rule (Section 11): OpenAI `/v1/images/edits` returns inline b64_json (no public URL),
    // and provides NO remote delete API; retention is governed by OpenAI API Organization Data Retention policy.
    this.remoteDeletionState = config.offlineMockMode ? "NOT_UPLOADED" : "UNSUPPORTED";
    this.fetchFn = config.fetchImpl ?? fetch;
  }

  public estimateMaxCostUsd(): number | null {
    return estimateOpenAiEditMaxUsd({
      model: this.modelId,
      quality: this.quality,
    });
  }

  private validateAndInspectInputs(input: UpstreamViewSubmitInput) {
    if (input.garments.length !== 1) {
      throw new UpstreamDeterministicError(
        "UNSUPPORTED_CAPABILITY",
        `OpenAI VTO Milestone hiện tại chỉ xử lý đúng 1 món trang phục mỗi lượt (nhận ${input.garments.length}).`,
      );
    }

    const primaryGarment = input.garments[0]!;
    if (!this.supportedCategories.has(primaryGarment.category)) {
      throw new UpstreamDeterministicError(
        "UNSUPPORTED_CAPABILITY",
        `Danh mục '${primaryGarment.category}' không nằm trong phạm vi hỗ trợ của OpenAI VTO.`,
      );
    }

    // 1. Decode & validate Person Image (Image 1) and Garment Image (Image 2)
    const rawPersonBytes = decodeStrictBase64(input.personImage.base64Data);
    const personValidated = validateTryOnImageBuffer(rawPersonBytes, {
      label: `person.${input.view}`,
      declaredMimeType: input.personImage.mimeType,
      strictMimeMatch: true,
      maxBytes: MAX_OPENAI_PERSON_BYTES,
      minDimensionPx: this.minInputDimensionPx,
      maxDimensionPx: MAX_IMAGE_DIMENSION_PX,
    });
    if (!personValidated.ok) {
      throw new UpstreamDeterministicError(personValidated.code, personValidated.message, false);
    }

    const rawGarmentBytes = decodeStrictBase64(primaryGarment.image.base64Data);
    const garmentValidated = validateTryOnImageBuffer(rawGarmentBytes, {
      label: `garment.${primaryGarment.itemId}.${input.view}`,
      declaredMimeType: primaryGarment.image.mimeType,
      strictMimeMatch: true,
      maxBytes: MAX_OPENAI_GARMENT_BYTES,
      minDimensionPx: this.minInputDimensionPx,
      maxDimensionPx: MAX_IMAGE_DIMENSION_PX,
    });
    if (!garmentValidated.ok) {
      throw new UpstreamDeterministicError(garmentValidated.code, garmentValidated.message, false);
    }

    return { primaryGarment, personValidated, garmentValidated };
  }

  public validateViewInput(input: UpstreamViewSubmitInput): void {
    this.validateAndInspectInputs(input);
  }

  async submitView(input: UpstreamViewSubmitInput): Promise<UpstreamViewSubmitOutput> {
    const { primaryGarment, personValidated, garmentValidated } =
      this.validateAndInspectInputs(input);

    // 2. Build versioned VTO prompt with zero PII
    const builtPrompt = buildOpenAiTryOnPrompt({
      view: input.view,
      category: primaryGarment.category,
      colors: primaryGarment.colors,
      personExifOrientation: personValidated.metadata.exifOrientation,
      garmentExifOrientation: garmentValidated.metadata.exifOrientation,
    });

    // 3. Construct official `multipart/form-data` payload for `POST /v1/images/edits`
    const formData = new FormData();
    const personExt = extensionForMime(personValidated.metadata.mimeType);
    const garmentExt = extensionForMime(garmentValidated.metadata.mimeType);

    const personCopy = new Uint8Array(personValidated.bytes.byteLength);
    personCopy.set(personValidated.bytes);
    const garmentCopy = new Uint8Array(garmentValidated.bytes.byteLength);
    garmentCopy.set(garmentValidated.bytes);

    formData.append(
      "image[]",
      new Blob([personCopy.buffer], { type: personValidated.metadata.mimeType }),
      `person_${input.view.toLowerCase()}.${personExt}`,
    );
    formData.append(
      "image[]",
      new Blob([garmentCopy.buffer], { type: garmentValidated.metadata.mimeType }),
      `garment_${input.view.toLowerCase()}.${garmentExt}`,
    );
    formData.append("model", this.modelId);
    formData.append("prompt", builtPrompt.prompt);
    formData.append("n", "1"); // Cost-guard hard invariant: single output image only
    formData.append("size", this.size);
    formData.append("quality", this.quality);
    formData.append("output_format", this.outputFormat);

    const effectiveFidelity = resolveEffectiveInputFidelity(this.modelSpec, this.inputFidelity);
    if (effectiveFidelity !== null) {
      formData.append("input_fidelity", effectiveFidelity);
    }

    const headers: Record<string, string> = {
      authorization: `Bearer ${this.apiKey}`,
    };
    if (this.organizationId) {
      headers["openai-organization"] = this.organizationId;
    }
    if (this.projectId) {
      headers["openai-project"] = this.projectId;
    }

    // 4. Execute single HTTP POST with NO automatic retry (prevents double billing on timeout/drop)
    // Keep timer + AbortSignal active until full response body read completes.
    this.submitCallCount += 1;
    const controller = new AbortController();
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    const timeoutPromise = new Promise<never>((_, reject) => {
      timeoutId = setTimeout(() => {
        controller.abort();
        reject(new Error("OpenAI timeout"));
      }, this.timeoutMs);
    });

    let response: Response;
    let parsedBody: OpenAiImageEditResponseWire | null = null;
    let errorRawText = "";
    try {
      response = await Promise.race([
        this.fetchFn(`${this.baseUrl}/images/edits`, {
          method: "POST",
          headers,
          body: formData,
          signal: controller.signal,
        }),
        timeoutPromise,
      ]);
      if (response.ok) {
        const jsonPromise = response.json() as Promise<OpenAiImageEditResponseWire>;
        jsonPromise.catch(() => {});
        parsedBody = await Promise.race([jsonPromise, timeoutPromise]);
      } else {
        const textPromise = response.text();
        textPromise.catch(() => {});
        errorRawText = await Promise.race([textPromise, timeoutPromise]);
      }
    } catch (err) {
      if (!controller.signal.aborted && err instanceof SyntaxError) {
        throw new UpstreamDeterministicError(
          "INVALID_AI_OUTPUT",
          "Phản hồi từ OpenAI Images API không phải JSON hợp lệ.",
          false,
        );
      }
      throw new UpstreamAmbiguousTimeoutError(
        "Mất kết nối hoặc quá thời gian chờ sau khi gửi yêu cầu tới OpenAI Images API; trạng thái xử lý chưa xác định nên hệ thống KHÔNG tự gửi lại để tránh tính phí hai lần.",
      );
    } finally {
      if (timeoutId !== undefined) {
        clearTimeout(timeoutId);
      }
    }

    const responseRequestId =
      response.headers.get("x-request-id")?.trim() ||
      `openai_${input.jobId}_${input.view.toLowerCase()}`;

    if (!response.ok) {
      let parsedError: OpenAiErrorEnvelopeWire | null = null;
      try {
        parsedError = JSON.parse(errorRawText) as OpenAiErrorEnvelopeWire;
      } catch {
        parsedError = null;
      }
      throw mapOpenAiHttpError(response.status, parsedError);
    }

    if (!parsedBody || typeof parsedBody !== "object") {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "Phản hồi từ OpenAI Images API không phải JSON hợp lệ.",
        false,
      );
    }

    // 5. Validate `data[0].b64_json` and decoded output image bytes (NEVER mark SUCCEEDED without valid image!)
    const firstItem = Array.isArray(parsedBody.data) ? parsedBody.data[0] : undefined;
    if (!firstItem) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "OpenAI trả về phản hồi rỗng (thiếu mảng data[] chứa ảnh kết quả).",
        false,
      );
    }
    if (firstItem.url && !firstItem.b64_json) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "OpenAI trả về URL thay vì dữ liệu ảnh nội tuyến b64_json; từ chối để bảo vệ quyền riêng tư.",
        false,
      );
    }
    if (typeof firstItem.b64_json !== "string" || firstItem.b64_json.trim().length === 0) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "Phản hồi từ OpenAI thiếu trường b64_json hợp lệ.",
        false,
      );
    }

    const cleanBase64 = firstItem.b64_json.replace(/\s+/g, "");
    const outputBytes = decodeStrictBase64(cleanBase64);
    if (!outputBytes || outputBytes.length === 0) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "Chuỗi b64_json từ OpenAI bị lỗi định dạng và không thể giải mã.",
        false,
      );
    }

    const outputMeta = inspectImageBytes(outputBytes);
    if (!outputMeta || outputMeta.effectiveWidth <= 0 || outputMeta.effectiveHeight <= 0) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "Dữ liệu byte ảnh đầu ra từ OpenAI không phải ảnh PNG/JPEG/WebP hợp lệ.",
        false,
      );
    }

    const declaredMime = mapOutputMime(parsedBody.output_format, this.outputFormat);
    // Trust actual decoded magic-byte format while verifying it is one of our supported types
    const finalMime: SupportedTryOnMime = outputMeta.mimeType ?? declaredMime;
    const contentHash = await sha256HexBytes(outputBytes);

    const fallbackEstimate = this.estimateMaxCostUsd();
    const computedCost = computeOpenAiUsageUsd(
      this.modelId,
      parsedBody.usage,
      fallbackEstimate,
    );

    const usageReport: OpenAiUsageReport = {
      modelId: this.modelId,
      promptVersion: OPENAI_VTO_PROMPT_VERSION,
      inputTokens: parsedBody.usage?.input_tokens,
      outputTokens: parsedBody.usage?.output_tokens,
      totalTokens: parsedBody.usage?.total_tokens,
      imageInputTokens: parsedBody.usage?.input_tokens_details?.image_tokens,
      textInputTokens: parsedBody.usage?.input_tokens_details?.text_tokens,
      estimatedCostUsd: computedCost,
    };
    this.lastUsageReport = usageReport;

    return {
      upstreamPredictionId: responseRequestId,
      initialState: "SUCCEEDED",
      immediateOutput: {
        mimeType: finalMime,
        base64Data: cleanBase64,
        contentHash,
        width: outputMeta.effectiveWidth,
        height: outputMeta.effectiveHeight,
      },
      remoteDeletionState: this.remoteDeletionState,
      usageReport,
    };
  }

  async pollView(upstreamPredictionId: string): Promise<UpstreamViewPollOutput> {
    // OpenAI `POST /v1/images/edits` completes synchronously during `submitView`.
    return {
      state: "FAILED",
      errorCode: "UPSTREAM_ERROR",
      errorMessage: `OpenAI edits đồng bộ không sử dụng polling riêng (${upstreamPredictionId}).`,
      retryable: false,
    };
  }

  async cancelView(): Promise<{ attempted: boolean; confirmed: boolean }> {
    // OpenAI synchronous `/v1/images/edits` has no server-side cancel endpoint once dispatched.
    return { attempted: false, confirmed: false };
  }
}
