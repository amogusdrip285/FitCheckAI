// OWNER: Track C (C-207 — Fake & FASHN upstream VTO provider adapters)

import type {
  Category,
  ImageView,
  TryOnErrorCode,
  UpstreamViewPollOutput,
  UpstreamViewSubmitInput,
  UpstreamViewSubmitOutput,
  UpstreamVtoProvider,
  ViewOutputPayload,
} from "./types";

export class UpstreamAmbiguousTimeoutError extends Error {
  public readonly retryable = false;
  constructor(message = "Upstream provider timed out after submission; status is ambiguous.") {
    super(message);
    this.name = "UpstreamAmbiguousTimeoutError";
  }
}

export class UpstreamDeterministicError extends Error {
  public readonly code: TryOnErrorCode;
  public readonly retryable: boolean;
  constructor(
    code: TryOnErrorCode,
    message: string,
    retryable = false,
  ) {
    super(message);
    this.name = "UpstreamDeterministicError";
    this.code = code;
    this.retryable = retryable;
  }
}

export type FakeViewScenario =
  | "SUCCEED_AFTER_ONE_POLL"
  | "SUCCEED_IMMEDIATELY"
  | "FAIL_ON_SUBMIT"
  | "FAIL_ON_POLL"
  | "AMBIGUOUS_SUBMIT_TIMEOUT"
  | "STAY_RUNNING";

export interface FakeUpstreamOptions {
  defaultFrontScenario?: FakeViewScenario;
  defaultBackScenario?: FakeViewScenario;
  supportsMultiGarment?: boolean;
  maxGarmentsPerJob?: number;
  supportedCategories?: ReadonlySet<Category>;
}

// 1x1 valid PNG in base64 used strictly as a DEV/FAKE placeholder byte buffer (tagged source="FAKE").
export const DEV_PLACEHOLDER_PNG_BASE64 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

interface FakePredictionEntry {
  view: ImageView;
  scenario: FakeViewScenario;
  pollCount: number;
  cancelled: boolean;
}

export class FakeUpstreamVtoProvider implements UpstreamVtoProvider {
  public readonly providerId = "fake-tryon-v2";
  public readonly source = "FAKE" as const;
  public readonly supportsMultiGarment: boolean;
  public readonly maxGarmentsPerJob: number;
  public readonly supportedCategories: ReadonlySet<Category>;

  public submitCallCount = 0;
  public cancelCallCount = 0;

  private readonly predictions = new Map<string, FakePredictionEntry>();
  private frontScenario: FakeViewScenario;
  private backScenario: FakeViewScenario;

  constructor(options?: FakeUpstreamOptions) {
    this.frontScenario = options?.defaultFrontScenario ?? "SUCCEED_AFTER_ONE_POLL";
    this.backScenario = options?.defaultBackScenario ?? "SUCCEED_AFTER_ONE_POLL";
    this.supportsMultiGarment = options?.supportsMultiGarment ?? true;
    this.maxGarmentsPerJob = options?.maxGarmentsPerJob ?? 3;
    this.supportedCategories =
      options?.supportedCategories ??
      new Set<Category>(["TOP", "BOTTOM", "DRESS", "OUTERWEAR"]);
  }

  public setScenarios(front: FakeViewScenario, back?: FakeViewScenario): void {
    this.frontScenario = front;
    if (back !== undefined) {
      this.backScenario = back;
    }
  }

  async submitView(input: UpstreamViewSubmitInput): Promise<UpstreamViewSubmitOutput> {
    this.submitCallCount += 1;
    const scenario = input.view === "FRONT" ? this.frontScenario : this.backScenario;

    if (scenario === "AMBIGUOUS_SUBMIT_TIMEOUT") {
      throw new UpstreamAmbiguousTimeoutError(
        `Fake ambiguous submit timeout for view ${input.view}`,
      );
    }

    if (scenario === "FAIL_ON_SUBMIT") {
      throw new UpstreamDeterministicError(
        "UPSTREAM_ERROR",
        `Fake upstream rejected view ${input.view}`,
        false,
      );
    }

    const predictionId = `fake_pred_${input.jobId}_${input.view.toLowerCase()}_${this.submitCallCount}`;
    this.predictions.set(predictionId, {
      view: input.view,
      scenario,
      pollCount: 0,
      cancelled: false,
    });

    if (scenario === "SUCCEED_IMMEDIATELY") {
      return {
        upstreamPredictionId: predictionId,
        initialState: "SUCCEEDED",
        immediateOutput: this.buildPlaceholderOutput(input.view),
        remoteDeletionState: "NOT_UPLOADED",
      };
    }

    return {
      upstreamPredictionId: predictionId,
      initialState: "RUNNING",
      remoteDeletionState: "NOT_UPLOADED",
    };
  }

  async pollView(upstreamPredictionId: string): Promise<UpstreamViewPollOutput> {
    const entry = this.predictions.get(upstreamPredictionId);
    if (!entry) {
      return {
        state: "FAILED",
        errorCode: "UPSTREAM_ERROR",
        errorMessage: "Không tìm thấy tiến trình xử lý giả lập.",
        retryable: false,
      };
    }

    if (entry.cancelled) {
      return {
        state: "FAILED",
        errorCode: "CANCELLED",
        errorMessage: "Tiến trình đã bị hủy.",
        retryable: false,
      };
    }

    entry.pollCount += 1;

    if (entry.scenario === "STAY_RUNNING") {
      return { state: "RUNNING" };
    }

    if (entry.scenario === "FAIL_ON_POLL") {
      return {
        state: "FAILED",
        errorCode: "INVALID_AI_OUTPUT",
        errorMessage: `Mô phỏng góc nhìn ${entry.view} thất bại ở bước hậu kiểm.`,
        retryable: false,
      };
    }

    return {
      state: "SUCCEEDED",
      output: this.buildPlaceholderOutput(entry.view),
    };
  }

  async cancelView(upstreamPredictionId: string): Promise<{ attempted: boolean; confirmed: boolean }> {
    this.cancelCallCount += 1;
    const entry = this.predictions.get(upstreamPredictionId);
    if (entry) {
      entry.cancelled = true;
      return { attempted: true, confirmed: true };
    }
    return { attempted: true, confirmed: false };
  }

  private buildPlaceholderOutput(view: ImageView): ViewOutputPayload {
    return {
      mimeType: "image/png",
      base64Data: DEV_PLACEHOLDER_PNG_BASE64,
      contentHash: `sha256:fake-dev-${view.toLowerCase()}`,
      width: 768,
      height: 1024,
    };
  }
}

export interface FashnUpstreamConfig {
  apiKey: string;
  modelName?: "tryon-v1.6" | "tryon-max";
  baseUrl?: string;
  submitTimeoutMs?: number;
  fetchImpl?: typeof fetch;
}

interface FashnRunResponse {
  id?: string;
  error?: { name?: string; message?: string } | string | null;
}

interface FashnStatusResponse {
  id?: string;
  status?: "starting" | "in_queue" | "processing" | "completed" | "failed" | "canceled";
  output?: string[];
  error?: { name?: string; message?: string } | string | null;
}

function mapCategoryToFashn(category: Category): "tops" | "bottoms" | "one-pieces" {
  switch (category) {
    case "TOP":
    case "OUTERWEAR":
      return "tops";
    case "BOTTOM":
      return "bottoms";
    case "DRESS":
      return "one-pieces";
    default:
      throw new UpstreamDeterministicError(
        "UNSUPPORTED_CAPABILITY",
        `Danh mục ${category} không được FASHN hỗ trợ.`,
      );
  }
}

function extractRawBase64(maybeDataUri: string): {
  mimeType: "image/jpeg" | "image/png";
  base64Data: string;
} {
  const match = /^data:(image\/(?:png|jpeg));base64,([A-Za-z0-9+/=\s]+)$/.exec(
    maybeDataUri.trim(),
  );
  if (match && match[1] && match[2]) {
    const mimeType = match[1] === "image/jpeg" ? "image/jpeg" : "image/png";
    return { mimeType, base64Data: match[2].replace(/\s+/g, "") };
  }
  // Reject unexpected remote URLs when return_base64=true was requested!
  if (/^https?:\/\//i.test(maybeDataUri.trim())) {
    throw new UpstreamDeterministicError(
      "INVALID_AI_OUTPUT",
      "Provider trả URL thay vì base64 đã yêu cầu; từ chối để bảo vệ quyền riêng tư.",
    );
  }
  return { mimeType: "image/png", base64Data: maybeDataUri.replace(/\s+/g, "") };
}

/**
 * Server-side adapter for FASHN AI (`tryon-v1.6` or `tryon-max`).
 * Enforces `return_base64: true` so outputs are NEVER stored on FASHN's 72-hour public CDN.
 */
export class FashnUpstreamProvider implements UpstreamVtoProvider {
  public readonly providerId: string;
  public readonly source = "AI_LIVE" as const;
  public readonly supportsMultiGarment: boolean;
  public readonly maxGarmentsPerJob: number;
  public readonly supportedCategories = new Set<Category>([
    "TOP",
    "BOTTOM",
    "DRESS",
    "OUTERWEAR",
  ]);

  private readonly apiKey: string;
  private readonly modelName: "tryon-v1.6" | "tryon-max";
  private readonly baseUrl: string;
  private readonly submitTimeoutMs: number;
  private readonly fetchFn: typeof fetch;

  constructor(config: FashnUpstreamConfig) {
    if (!config.apiKey || config.apiKey.trim().length === 0) {
      throw new Error("FASHN_API_KEY is required to instantiate FashnUpstreamProvider");
    }
    this.apiKey = config.apiKey.trim();
    this.modelName = config.modelName ?? "tryon-v1.6";
    this.providerId = `fashn-${this.modelName}`;
    this.supportsMultiGarment = this.modelName === "tryon-max";
    this.maxGarmentsPerJob = this.modelName === "tryon-max" ? 2 : 1;
    this.baseUrl = (config.baseUrl ?? "https://api.fashn.ai/v1").replace(/\/$/, "");
    this.submitTimeoutMs = config.submitTimeoutMs ?? 15_000;
    this.fetchFn = config.fetchImpl ?? fetch;
  }

  async submitView(input: UpstreamViewSubmitInput): Promise<UpstreamViewSubmitOutput> {
    if (input.garments.length === 0 || input.garments.length > this.maxGarmentsPerJob) {
      throw new UpstreamDeterministicError(
        "UNSUPPORTED_CAPABILITY",
        `Model ${this.modelName} chỉ hỗ trợ tối đa ${this.maxGarmentsPerJob} món đồ mỗi lượt.`,
      );
    }

    const personDataUri = `data:${input.personImage.mimeType};base64,${input.personImage.base64Data}`;
    const primaryGarment = input.garments[0]!;
    const inputsPayload: Record<string, unknown> =
      this.modelName === "tryon-v1.6"
        ? {
            model_image: personDataUri,
            garment_image: `data:${primaryGarment.image.mimeType};base64,${primaryGarment.image.base64Data}`,
            category: mapCategoryToFashn(primaryGarment.category),
            mode: "balanced",
            num_samples: 1,
            return_base64: true,
          }
        : {
            model_image: personDataUri,
            garments: input.garments.map(
              (g) => `data:${g.image.mimeType};base64,${g.image.base64Data}`,
            ),
            return_base64: true,
          };

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.submitTimeoutMs);

    let response: Response;
    try {
      response = await this.fetchFn(`${this.baseUrl}/run`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          authorization: `Bearer ${this.apiKey}`,
        },
        body: JSON.stringify({
          model_name: this.modelName,
          inputs: inputsPayload,
        }),
        signal: controller.signal,
      });
    } catch {
      // Once POST was dispatched, network error or abort means upstream MAY have accepted & billed it.
      throw new UpstreamAmbiguousTimeoutError(
        "Kết nối tới dịch vụ tạo ảnh bị gián đoạn khi gửi yêu cầu; không tự động gửi lại để tránh tính phí trùng.",
      );
    } finally {
      clearTimeout(timer);
    }

    if (response.status === 429) {
      throw new UpstreamDeterministicError(
        "RATE_LIMITED",
        "Dịch vụ thử đồ đang giới hạn tần suất.",
        true,
      );
    }

    if (!response.ok) {
      throw new UpstreamDeterministicError(
        response.status >= 500 ? "UPSTREAM_ERROR" : "INVALID_REQUEST",
        "Dịch vụ thử đồ từ chối yêu cầu xử lý ảnh.",
        response.status >= 500,
      );
    }

    const parsed = (await response.json()) as FashnRunResponse;
    if (!parsed.id) {
      throw new UpstreamDeterministicError(
        "INVALID_AI_OUTPUT",
        "Phản hồi từ dịch vụ thử đồ thiếu mã định danh tiến trình.",
      );
    }

    return {
      upstreamPredictionId: parsed.id,
      initialState: "QUEUED",
      remoteDeletionState: "BASE64_EPHEMERAL_60M",
    };
  }

  async pollView(upstreamPredictionId: string): Promise<UpstreamViewPollOutput> {
    let response: Response;
    try {
      response = await this.fetchFn(
        `${this.baseUrl}/status/${encodeURIComponent(upstreamPredictionId)}`,
        {
          method: "GET",
          headers: {
            authorization: `Bearer ${this.apiKey}`,
          },
        },
      );
    } catch {
      return {
        state: "RUNNING",
      };
    }

    if (!response.ok) {
      return {
        state: "FAILED",
        errorCode: "UPSTREAM_ERROR",
        errorMessage: "Không thể kiểm tra trạng thái tiến trình thử đồ.",
        retryable: response.status >= 500,
      };
    }

    const body = (await response.json()) as FashnStatusResponse;
    switch (body.status) {
      case "starting":
      case "in_queue":
        return { state: "QUEUED" };
      case "processing":
        return { state: "RUNNING" };
      case "completed": {
        const rawFirst = body.output?.[0];
        if (!rawFirst) {
          return {
            state: "FAILED",
            errorCode: "INVALID_AI_OUTPUT",
            errorMessage: "Tiến trình hoàn tất nhưng không có dữ liệu ảnh hợp lệ.",
            retryable: false,
          };
        }
        try {
          const decoded = extractRawBase64(rawFirst);
          return {
            state: "SUCCEEDED",
            output: {
              mimeType: decoded.mimeType,
              base64Data: decoded.base64Data,
              contentHash: `fashn:${upstreamPredictionId}`,
              width: 864,
              height: 1296,
            },
          };
        } catch (err) {
          return {
            state: "FAILED",
            errorCode: "INVALID_AI_OUTPUT",
            errorMessage:
              err instanceof Error ? err.message : "Dữ liệu ảnh trả về không hợp lệ.",
            retryable: false,
          };
        }
      }
      case "canceled":
        return {
          state: "FAILED",
          errorCode: "CANCELLED",
          errorMessage: "Tiến trình thử đồ đã bị hủy.",
          retryable: false,
        };
      case "failed":
      default:
        return {
          state: "FAILED",
          errorCode: "UPSTREAM_ERROR",
          errorMessage: "Mô hình thử đồ không thể xử lý bộ ảnh này.",
          retryable: false,
        };
    }
  }

  async cancelView(
    upstreamPredictionId: string,
  ): Promise<{ attempted: boolean; confirmed: boolean }> {
    try {
      const response = await this.fetchFn(
        `${this.baseUrl}/cancel/${encodeURIComponent(upstreamPredictionId)}`,
        {
          method: "POST",
          headers: {
            authorization: `Bearer ${this.apiKey}`,
          },
        },
      );
      return { attempted: true, confirmed: response.ok };
    } catch {
      return { attempted: true, confirmed: false };
    }
  }
}
