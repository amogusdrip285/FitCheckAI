// OWNER: Track C (Experimental FOSTA Luna `gpt-6-luna` Responses API Provider Adapter)
// Uses `POST https://codex.fosta.app/v1/responses` with forced `image_generation` tool.
// Strictly preserves OpenAITryOnProvider intact: zero automatic retries, zero fallback to OpenAI,
// server-side key only, and zero secret/body leakage in error diagnostics.

import {
  decodeStrictBase64,
  encodeBase64,
  inspectImageBytes,
  MAX_IMAGE_DIMENSION_PX,
  MIN_LIVE_VTO_DIMENSION_PX,
  sha256HexBytes,
  validateTryOnImageBuffer,
} from "./image_pipeline";
import {
  buildFostaTryOnPrompt,
  FOSTA_VTO_PROMPT_VERSION,
  mapCategoryToFostaGarmentKind,
} from "./fosta_prompt";
import {
  UpstreamAmbiguousTimeoutError,
  UpstreamDeterministicError,
} from "./providers";
import type {
  Category,
  FostaGarmentKind,
  ImageView,
  RemoteDeletionState,
  SupportedTryOnMime,
  TryOnErrorCode,
  TryOnPresentationMode,
  TryOnSource,
  UpstreamViewPollOutput,
  UpstreamViewSubmitInput,
  UpstreamViewSubmitOutput,
  UpstreamVtoProvider,
} from "./types";

export const DEFAULT_FOSTA_ENDPOINT = "https://codex.fosta.app/v1/responses";
export const DEFAULT_FOSTA_MODEL = "gpt-6-luna";
export const DEFAULT_FOSTA_TIMEOUT_MS = 240_000;
export const MIN_FOSTA_TIMEOUT_MS = 1_000;
export const MAX_FOSTA_TIMEOUT_MS = 300_000;

export const MAX_FOSTA_SINGLE_IMAGE_BYTES = 12 * 1024 * 1024; // 12 MiB
export const MAX_FOSTA_TOTAL_IMAGE_BYTES = 24 * 1024 * 1024; // 24 MiB
export const MAX_FOSTA_OUTPUT_BYTES = 20 * 1024 * 1024; // 20 MiB

const STRICT_BASE64_REGEX = /^[A-Za-z0-9+/]+={0,2}$/;

export interface FostaContentTextItem {
  type: "input_text";
  text: string;
}

export interface FostaContentImageItem {
  type: "input_image";
  image_url: string;
}

export type FostaInputContentItem = FostaContentTextItem | FostaContentImageItem;

export interface FostaResponsesRequestWire {
  model: string;
  input: Array<{
    role: "user";
    content: FostaInputContentItem[];
  }>;
  tools: Array<{ type: "image_generation" }>;
  tool_choice: { type: "image_generation" };
}

export interface FostaResponsesOutputItemWire {
  type?: string;
  id?: string;
  status?: string;
  result?: string;
}

export interface FostaResponsesWire {
  id?: string;
  status?: string;
  error?: unknown;
  output?: FostaResponsesOutputItemWire[];
}

export interface BuildFostaPayloadInput {
  mode?: TryOnPresentationMode;
  view?: ImageView;
  model?: string;
  personBytes: Uint8Array;
  topBytes?: Uint8Array | null;
  bottomBytes?: Uint8Array | null;
}

/**
 * Detects JPEG, PNG, or WebP magic bytes without Node-only Buffer dependencies.
 */
export function detectFostaImageMime(bytes: Uint8Array): SupportedTryOnMime | null {
  if (!(bytes instanceof Uint8Array) || bytes.byteLength < 16) {
    return null;
  }
  // JPEG: FF D8 FF
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return "image/jpeg";
  }
  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47 &&
    bytes[4] === 0x0d &&
    bytes[5] === 0x0a &&
    bytes[6] === 0x1a &&
    bytes[7] === 0x0a
  ) {
    return "image/png";
  }
  // WebP: "RIFF" .... "WEBP"
  if (
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50
  ) {
    return "image/webp";
  }
  return null;
}

function assertValidImageSlice(bytes: Uint8Array | undefined | null, label: string): SupportedTryOnMime {
  if (!(bytes instanceof Uint8Array)) {
    throw new UpstreamDeterministicError(
      "INVALID_REQUEST",
      `${label} image bytes are missing.`,
    );
  }
  const mime = detectFostaImageMime(bytes);
  if (
    !mime ||
    bytes.byteLength < 16 ||
    bytes.byteLength > MAX_FOSTA_SINGLE_IMAGE_BYTES
  ) {
    throw new UpstreamDeterministicError(
      bytes.byteLength > MAX_FOSTA_SINGLE_IMAGE_BYTES
        ? "PAYLOAD_TOO_LARGE"
        : "INVALID_REQUEST",
      `${label} must contain 16 B to 12 MiB of valid JPEG, PNG, or WebP data.`,
    );
  }
  return mime;
}

/**
 * Builds the exact FOSTA `/v1/responses` JSON payload in deterministic order:
 * 1. `input_text` (`FITCHECK_VTO_PROMPT_VERSION=v2.0 ...`)
 * 2. `input_image` #1: `PERSON`
 * 3. `input_image` #2: `TOP` (if selected) else `BOTTOM`
 * 4. `input_image` #3: `BOTTOM` (when both `TOP` and `BOTTOM` are selected)
 */
export function buildFostaResponsesPayload({
  mode = "ORIGINAL_PHOTO",
  view = "FRONT",
  model = DEFAULT_FOSTA_MODEL,
  personBytes,
  topBytes,
  bottomBytes,
}: BuildFostaPayloadInput): FostaResponsesRequestWire {
  if (mode !== "ORIGINAL_PHOTO" && mode !== "STUDIO_WHITE") {
    throw new UpstreamDeterministicError(
      "INVALID_REQUEST",
      `Unsupported FOSTA presentation mode: '${String(mode)}'.`,
    );
  }
  if (view !== "FRONT" && view !== "BACK") {
    throw new UpstreamDeterministicError(
      "INVALID_REQUEST",
      `Unsupported FOSTA view: '${String(view)}'.`,
    );
  }

  const orderedGarments: Array<{ kind: FostaGarmentKind; bytes: Uint8Array; mime: SupportedTryOnMime }> = [];
  if (topBytes !== undefined && topBytes !== null) {
    orderedGarments.push({
      kind: "TOP",
      bytes: topBytes,
      mime: assertValidImageSlice(topBytes, "TOP"),
    });
  }
  if (bottomBytes !== undefined && bottomBytes !== null) {
    orderedGarments.push({
      kind: "BOTTOM",
      bytes: bottomBytes,
      mime: assertValidImageSlice(bottomBytes, "BOTTOM"),
    });
  }
  if (orderedGarments.length === 0) {
    throw new UpstreamDeterministicError(
      "NEEDS_INPUT",
      "At least one garment (TOP or BOTTOM) is required for FOSTA Virtual Try-On.",
    );
  }

  const personMime = assertValidImageSlice(personBytes, "person");
  const totalImageBytes =
    personBytes.byteLength +
    orderedGarments.reduce((acc, g) => acc + g.bytes.byteLength, 0);
  if (totalImageBytes > MAX_FOSTA_TOTAL_IMAGE_BYTES) {
    throw new UpstreamDeterministicError(
      "PAYLOAD_TOO_LARGE",
      "Total FOSTA reference image bytes exceed 24 MiB.",
    );
  }

  const prompt = buildFostaTryOnPrompt({
    mode,
    view,
    garmentKinds: orderedGarments.map((g) => g.kind),
  });

  const toDataUrl = (bytes: Uint8Array, mime: SupportedTryOnMime): string =>
    `data:${mime};base64,${encodeBase64(bytes)}`;

  const content: FostaInputContentItem[] = [
    { type: "input_text", text: prompt },
    { type: "input_image", image_url: toDataUrl(personBytes, personMime) },
    ...orderedGarments.map(
      (g): FostaContentImageItem => ({
        type: "input_image",
        image_url: toDataUrl(g.bytes, g.mime),
      }),
    ),
  ];

  return {
    model: model.trim() || DEFAULT_FOSTA_MODEL,
    input: [{ role: "user", content }],
    tools: [{ type: "image_generation" }],
    tool_choice: { type: "image_generation" },
  };
}

function mapFostaHttpStatusToError(status: number): UpstreamDeterministicError {
  // Never interpolate upstream response body text so third-party errors cannot leak keys or echoed inputs.
  if (status === 401) {
    return new UpstreamDeterministicError(
      "UNAUTHORIZED",
      "FOSTA từ chối xác thực khóa API (HTTP 401 UNAUTHORIZED). Kiểm tra lại FOSTA_API_KEY ở máy chủ.",
      false,
    );
  }
  if (status === 402) {
    return new UpstreamDeterministicError(
      "INSUFFICIENT_QUOTA",
      "Tài khoản FOSTA không đủ hạn mức/quota (HTTP 402 INSUFFICIENT_QUOTA). Hệ thống giữ nguyên FOSTA và KHÔNG tự chuyển sang OpenAI.",
      false,
    );
  }
  if (status === 403) {
    return new UpstreamDeterministicError(
      "FORBIDDEN",
      "Yêu cầu tới FOSTA bị từ chối quyền truy cập (HTTP 403 FORBIDDEN). Hệ thống KHÔNG tự chuyển sang OpenAI.",
      false,
    );
  }
  if (status === 429) {
    return new UpstreamDeterministicError(
      "RATE_LIMITED",
      "FOSTA giới hạn tần suất yêu cầu (HTTP 429 RATE_LIMITED). Không tự động gửi lại để bảo vệ quota.",
      false,
    );
  }
  if (status >= 500) {
    return new UpstreamDeterministicError(
      "UPSTREAM_ERROR",
      `Máy chủ FOSTA gặp sự cố phía upstream (HTTP ${status}). Hệ thống KHÔNG tự động gọi lại hoặc đổi provider.`,
      false,
    );
  }
  const code: TryOnErrorCode = "INVALID_REQUEST";
  return new UpstreamDeterministicError(
    code,
    `FOSTA từ chối định dạng yêu cầu (HTTP ${status}).`,
    false,
  );
}

export interface FostaTryOnProviderConfig {
  apiKey: string;
  endpoint?: string;
  model?: string;
  timeoutMs?: number;
  minInputDimensionPx?: number;
  /** Explicit server-side live gate; required to be true unless `offlineMockMode` is true. */
  approvedLive?: boolean;
  /** Explicit server-side quota approval; required to be true unless `offlineMockMode` is true. */
  approvedQuotaUse?: boolean;
  /**
   * When true, runs against local FOSTA Responses mock with `source: "FAKE"` and `remoteDeletionState: "NOT_UPLOADED"`.
   */
  offlineMockMode?: boolean;
  fetchImpl?: typeof fetch;
}

export class FostaTryOnProvider implements UpstreamVtoProvider {
  public readonly providerId = "fosta";
  public readonly source: TryOnSource;
  public readonly supportsMultiGarment = true;
  public readonly maxGarmentsPerJob = 2; // Max 1 TOP + 1 BOTTOM simultaneously
  public readonly supportedCategories = new Set<Category>(["TOP", "BOTTOM"]);

  public readonly modelId: string;
  public readonly endpoint: string;
  public submitCallCount = 0;

  private readonly apiKey: string;
  private readonly timeoutMs: number;
  private readonly minInputDimensionPx: number;
  private readonly approvedLive: boolean;
  private readonly approvedQuotaUse: boolean;
  private readonly offlineMockMode: boolean;
  private readonly remoteDeletionState: RemoteDeletionState;
  private readonly fetchFn: typeof fetch;

  constructor(config: FostaTryOnProviderConfig) {
    const offlineMock = config.offlineMockMode === true;
    const trimmedKey = (config.apiKey ?? "").trim();

    if (!offlineMock) {
      if (!config.approvedLive || !config.approvedQuotaUse) {
        throw new UpstreamDeterministicError(
          "FORBIDDEN",
          "Cổng FOSTA LIVE và phê duyệt quota (FOSTA_APPROVE_QUOTA=true) bắt buộc phải bật rõ ràng trước khi tạo kết nối LIVE.",
          false,
        );
      }
      if (!trimmedKey) {
        throw new UpstreamDeterministicError(
          "UNAUTHORIZED",
          "Chưa cấu hình FOSTA_API_KEY ở phía máy chủ.",
          false,
        );
      }
    } else if (!trimmedKey) {
      throw new UpstreamDeterministicError(
        "UNAUTHORIZED",
        "Thiếu token cho phiên kiểm thử FOSTA mock.",
        false,
      );
    }

    const timeoutMs = config.timeoutMs ?? DEFAULT_FOSTA_TIMEOUT_MS;
    if (
      !Number.isInteger(timeoutMs) ||
      timeoutMs < MIN_FOSTA_TIMEOUT_MS ||
      timeoutMs > MAX_FOSTA_TIMEOUT_MS
    ) {
      throw new UpstreamDeterministicError(
        "INVALID_REQUEST",
        `FOSTA_TIMEOUT_MS phải nằm trong khoảng ${MIN_FOSTA_TIMEOUT_MS}..${MAX_FOSTA_TIMEOUT_MS} ms (nhận ${String(timeoutMs)}).`,
        false,
      );
    }

    this.apiKey = trimmedKey;
    this.endpoint = (config.endpoint ?? DEFAULT_FOSTA_ENDPOINT).trim() || DEFAULT_FOSTA_ENDPOINT;
    this.modelId = (config.model ?? DEFAULT_FOSTA_MODEL).trim() || DEFAULT_FOSTA_MODEL;
    this.timeoutMs = timeoutMs;
    this.minInputDimensionPx = config.minInputDimensionPx ?? MIN_LIVE_VTO_DIMENSION_PX;
    this.approvedLive = offlineMock ? false : Boolean(config.approvedLive);
    this.approvedQuotaUse = offlineMock ? false : Boolean(config.approvedQuotaUse);
    this.offlineMockMode = offlineMock;
    this.source = offlineMock ? "FAKE" : "AI_LIVE";
    // Third-party proxy retention is unverified and has no remote delete API
    this.remoteDeletionState = offlineMock ? "NOT_UPLOADED" : "UNSUPPORTED";
    this.fetchFn = config.fetchImpl ?? fetch;
  }

  async submitView(input: UpstreamViewSubmitInput): Promise<UpstreamViewSubmitOutput> {
    if (!this.offlineMockMode && (!this.approvedLive || !this.approvedQuotaUse)) {
      throw new UpstreamDeterministicError(
        "FORBIDDEN",
        "FOSTA LIVE đang bị khóa hoặc chưa phê duyệt quota.",
        false,
      );
    }

    if (input.garments.length === 0) {
      throw new UpstreamDeterministicError(
        "NEEDS_INPUT",
        "Cần chọn ít nhất 1 món trang phục (Áo hoặc Quần) để thử đồ với FOSTA.",
        false,
      );
    }
    if (input.garments.length > this.maxGarmentsPerJob) {
      throw new UpstreamDeterministicError(
        "UNSUPPORTED_CAPABILITY",
        `FOSTA Luna chỉ nhận tối đa ${this.maxGarmentsPerJob} món trang phục (1 Áo + 1 Quần) trong cùng một lượt.`,
        false,
      );
    }

    const mode: TryOnPresentationMode = input.presentationMode ?? "ORIGINAL_PHOTO";
    if (mode !== "ORIGINAL_PHOTO" && mode !== "STUDIO_WHITE") {
      throw new UpstreamDeterministicError(
        "INVALID_REQUEST",
        `Chế độ hiển thị '${String(mode)}' không được FOSTA hỗ trợ.`,
        false,
      );
    }

    // Validate person image buffer & orientation/dimensions
    const personBytes = decodeStrictBase64(input.personImage.base64Data);
    if (!personBytes) {
      throw new UpstreamDeterministicError(
        "INVALID_REQUEST",
        `Ảnh người dùng (${input.view}) không phải chuỗi base64 hợp lệ.`,
        false,
      );
    }
    const personValidated = validateTryOnImageBuffer(personBytes, {
      label: `person.${input.view}`,
      declaredMimeType: input.personImage.mimeType,
      strictMimeMatch: true,
      maxBytes: MAX_FOSTA_SINGLE_IMAGE_BYTES,
      minDimensionPx: this.minInputDimensionPx,
      maxDimensionPx: MAX_IMAGE_DIMENSION_PX,
    });
    if (!personValidated.ok) {
      throw new UpstreamDeterministicError(
        personValidated.code,
        personValidated.message,
        false,
      );
    }

    // Map & validate TOP and/or BOTTOM garments; reject duplicate TOP+TOP or BOTTOM+BOTTOM
    let topBytes: Uint8Array | null = null;
    let bottomBytes: Uint8Array | null = null;

    for (const garment of input.garments) {
      const kind = mapCategoryToFostaGarmentKind(garment.category);
      if (!kind) {
        throw new UpstreamDeterministicError(
          "UNSUPPORTED_CAPABILITY",
          `Danh mục '${garment.category}' chưa được FOSTA Luna hỗ trợ (chỉ hỗ trợ TOP và BOTTOM).`,
          false,
        );
      }
      if (kind === "TOP" && topBytes !== null) {
        throw new UpstreamDeterministicError(
          "INVALID_REQUEST",
          "Không được gửi hai món Áo (TOP) trong cùng một lượt thử FOSTA; chỉ chọn tối đa 1 TOP và 1 BOTTOM.",
          false,
        );
      }
      if (kind === "BOTTOM" && bottomBytes !== null) {
        throw new UpstreamDeterministicError(
          "INVALID_REQUEST",
          "Không được gửi hai món Quần (BOTTOM) trong cùng một lượt thử FOSTA; chỉ chọn tối đa 1 TOP và 1 BOTTOM.",
          false,
        );
      }

      const gBytes = decodeStrictBase64(garment.image.base64Data);
      if (!gBytes) {
        throw new UpstreamDeterministicError(
          "INVALID_REQUEST",
          `Ảnh trang phục '${garment.itemId}' (${input.view}) không phải base64 hợp lệ.`,
          false,
        );
      }
      const gValidated = validateTryOnImageBuffer(gBytes, {
        label: `garment.${garment.itemId}.${input.view}`,
        declaredMimeType: garment.image.mimeType,
        strictMimeMatch: true,
        maxBytes: MAX_FOSTA_SINGLE_IMAGE_BYTES,
        minDimensionPx: this.minInputDimensionPx,
        maxDimensionPx: MAX_IMAGE_DIMENSION_PX,
      });
      if (!gValidated.ok) {
        throw new UpstreamDeterministicError(
          gValidated.code,
          gValidated.message,
          false,
        );
      }

      if (kind === "TOP") {
        topBytes = gBytes;
      } else {
        bottomBytes = gBytes;
      }
    }

    const payload = buildFostaResponsesPayload({
      mode,
      view: input.view,
      model: this.modelId,
      personBytes,
      topBytes,
      bottomBytes,
    });

    // Execute exactly ONE POST request — zero retries, zero fallback to OpenAI!
    this.submitCallCount += 1;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);

    let response: Response;
    try {
      response = await this.fetchFn(this.endpoint, {
        method: "POST",
        headers: {
          authorization: `Bearer ${this.apiKey}`,
          "content-type": "application/json",
        },
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
    } catch {
      throw new UpstreamAmbiguousTimeoutError(
        "Mất kết nối hoặc quá thời gian chờ khi gọi FOSTA Responses API; yêu cầu có thể đã tới máy chủ nên hệ thống KHÔNG tự động gửi lại để bảo vệ quota.",
      );
    } finally {
      clearTimeout(timer);
    }

    if (!response.ok) {
      throw mapFostaHttpStatusToError(response.status);
    }

    let data: FostaResponsesWire;
    try {
      data = (await response.json()) as FostaResponsesWire;
    } catch {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "FOSTA trả về dữ liệu không phải JSON hợp lệ.",
        false,
      );
    }

    if (data?.error) {
      throw new UpstreamDeterministicError(
        "UPSTREAM_ERROR",
        "FOSTA trả về lỗi API trong phản hồi Responses.",
        false,
      );
    }

    if (data?.status && data.status !== "completed") {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        `Phản hồi FOSTA chưa hoàn tất (status='${String(data.status)}').`,
        false,
      );
    }

    const outputList = Array.isArray(data?.output) ? data.output : [];
    const imageCall = outputList.find(
      (item) =>
        item?.type === "image_generation_call" && typeof item.result === "string",
    );
    if (!imageCall || typeof imageCall.result !== "string") {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "FOSTA không trả về kết quả image_generation_call hợp lệ.",
        false,
      );
    }

    const rawResultB64 = imageCall.result.trim();
    const maxBase64Chars = Math.ceil((MAX_FOSTA_OUTPUT_BYTES * 4) / 3) + 16;
    if (
      rawResultB64.length === 0 ||
      rawResultB64.length > maxBase64Chars ||
      !STRICT_BASE64_REGEX.test(rawResultB64)
    ) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "Chuỗi base64 ảnh đầu ra từ FOSTA bị hỏng hoặc vượt giới hạn 20 MiB.",
        false,
      );
    }

    const outputBytes = decodeStrictBase64(rawResultB64);
    if (
      !outputBytes ||
      outputBytes.byteLength < 16 ||
      outputBytes.byteLength > MAX_FOSTA_OUTPUT_BYTES
    ) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "Không thể giải mã dữ liệu ảnh đầu ra từ FOSTA.",
        false,
      );
    }

    const inspectedOut = inspectImageBytes(outputBytes);
    const fallbackMime = detectFostaImageMime(outputBytes);
    if (!inspectedOut && !fallbackMime) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "Ảnh đầu ra từ FOSTA không khớp định dạng JPEG, PNG hoặc WebP hợp lệ.",
        false,
      );
    }

    const outMime = inspectedOut?.mimeType ?? fallbackMime!;
    const outWidth = inspectedOut?.effectiveWidth ?? 1024;
    const outHeight = inspectedOut?.effectiveHeight ?? 1536;
    const contentHash = await sha256HexBytes(outputBytes);
    const upstreamId =
      typeof data.id === "string" && data.id.trim().length > 0
        ? `fosta_${data.id.trim()}_${input.view.toLowerCase()}`
        : `fosta_${input.jobId}_${input.view.toLowerCase()}`;

    return {
      upstreamPredictionId: upstreamId,
      initialState: "SUCCEEDED",
      immediateOutput: {
        mimeType: outMime,
        base64Data: encodeBase64(outputBytes),
        contentHash,
        width: outWidth,
        height: outHeight,
      },
      remoteDeletionState: this.remoteDeletionState,
      usageReport: {
        modelId: this.modelId,
        promptVersion: FOSTA_VTO_PROMPT_VERSION,
        estimatedCostUsd: null,
      },
    };
  }

  async pollView(
    upstreamPredictionId: string,
    _requestId: string,
  ): Promise<UpstreamViewPollOutput> {
    // FOSTA `/v1/responses` completes synchronously in `submitView`.
    return {
      state: "FAILED",
      errorCode: "UPSTREAM_ERROR",
      errorMessage: `Tiến trình đồng bộ '${upstreamPredictionId}' không hỗ trợ poll riêng lẻ.`,
      retryable: false,
    };
  }

  async cancelView(
    _upstreamPredictionId: string,
  ): Promise<{ attempted: boolean; confirmed: boolean }> {
    return { attempted: false, confirmed: false };
  }
}
