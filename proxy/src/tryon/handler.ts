// OWNER: Track C (C-207 — /v2/tryon/jobs HTTP router, validation, idempotency & lifecycle)
// Track B mounts this router in proxy/src/index.ts once B-001 & C-205 GO are ready.

import {
  decodeStrictBase64,
  inspectImageBytes,
  MAX_DECODED_PIXEL_COUNT,
  MAX_IMAGE_DIMENSION_PX,
} from "./image_pipeline";
import {
  createFostaResponsesMock,
  type FostaMockScenario,
} from "./fosta_mock";
import { FOSTA_EXPERIMENTAL_CAPABILITIES } from "./fosta_prompt";
import {
  DEFAULT_FOSTA_MODEL,
  DEFAULT_FOSTA_TIMEOUT_MS,
  FostaTryOnProvider,
} from "./fosta_provider";
import {
  createOpenAiImageEditsMock,
  type OpenAiMockScenario,
} from "./openai_mock";
import {
  estimateOpenAiEditMaxUsd,
  OpenAITryOnProvider,
  type OpenAiInputFidelityOption,
  type OpenAiOutputFormat,
  type OpenAiQualityTier,
  validateOpenAiModelSelection,
} from "./openai_provider";
import {
  FakeUpstreamVtoProvider,
  FashnUpstreamProvider,
  UpstreamAmbiguousTimeoutError,
  UpstreamDeterministicError,
} from "./providers";
import { InMemoryTryOnJobStore } from "./store";
import type {
  CancellationReceiptWire,
  Category,
  DeletionReceiptWire,
  DurableTryOnJobStore,
  FeasibilityDecision,
  ImageView,
  RemoteDeletionState,
  SafeTryOnLogger,
  StoredChildViewJob,
  StoredTryOnJobRecord,
  TryOnEnvConfig,
  TryOnErrorCode,
  TryOnErrorEnvelope,
  TryOnJobStatus,
  TryOnJobSubmitResponse,
  TryOnPresentationMode,
  TryOnResultWire,
  TryOnRouteContext,
  TryOnSubmitWireRequest,
  TryOnTypedError,
  UpstreamVtoProvider,
  ViewResultWire,
  WireImageInput,
} from "./types";

export const MAX_WIRE_BYTES = 12 * 1024 * 1024; // 12 MiB
export const MAX_PERSON_IMAGE_BYTES = 2 * 1024 * 1024; // 2 MiB per view
export const MAX_GARMENT_IMAGE_BYTES = 1 * 1024 * 1024; // 1 MiB per view
export const MAX_TOTAL_DECODED_BYTES = 8 * 1024 * 1024; // 8 MiB decoded sum
export const FRONT_DEADLINE_MS = 120_000; // 120s technical deadline (CT-07)
export const DUAL_DEADLINE_MS = 180_000; // 180s technical deadline (CT-07)
export const DEFAULT_OUTPUT_TTL_MS = 60 * 60 * 1000; // 60 minutes ephemeral TTL
export const DEFAULT_BUDGET_CAP_UNITS = 25;

const HTTP_STATUS_BY_ERROR: Record<TryOnErrorCode, number> = {
  INVALID_REQUEST: 400,
  NEEDS_INPUT: 400,
  UNSUPPORTED_CAPABILITY: 400,
  STALE_INPUT: 409,
  CONFLICT: 409,
  UNAUTHORIZED: 401,
  CONSENT_REQUIRED: 403,
  FORBIDDEN: 403,
  PAYLOAD_TOO_LARGE: 413,
  RATE_LIMITED: 429,
  INSUFFICIENT_QUOTA: 402,
  INVALID_AI_OUTPUT: 502,
  NETWORK: 502,
  UPSTREAM_ERROR: 502,
  IO: 500,
  BUDGET_EXCEEDED: 503,
  UPSTREAM_TIMEOUT: 504,
  CANCELLED: 409,
  EXPIRED: 410,
};

const FORBIDDEN_WIRE_KEYS = new Set([
  "localRelativePath",
  "imagePath",
  "filePath",
  "absolutePath",
  "url",
  "downloadUrl",
  "signedUrl",
  "sourceUri",
  "uri",
]);

const SUSPICIOUS_REF_PATTERN =
  /^(?:https?:\/\/|file:\/\/|content:\/\/|s3:\/\/|gs:\/\/|\/home\/|\/data\/|\/storage\/|\.\.\/|[A-Za-z]:\\)/i;

const STRICT_BASE64_PATTERN = /^[A-Za-z0-9+/]+={0,2}$/;

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function makeTypedError(
  code: TryOnErrorCode,
  message: string,
  requestId: string,
  retryable = false,
  statusOverride?: number,
): Response {
  const envelope: TryOnErrorEnvelope = {
    error: {
      code,
      message,
      requestId,
      retryable,
    },
  };
  return jsonResponse(envelope, statusOverride ?? HTTP_STATUS_BY_ERROR[code]);
}

function defaultSafeLogger(entry: Parameters<SafeTryOnLogger>[0]): void {
  // Structured, redacted operational log — NEVER includes base64, URLs, paths, or profile values.
  console.info(JSON.stringify(entry));
}

function estimateDecodedBase64Bytes(raw: string): number | null {
  const trimmed = raw.trim();
  if (trimmed.length === 0 || trimmed.length % 4 !== 0) {
    return null;
  }
  if (!STRICT_BASE64_PATTERN.test(trimmed)) {
    return null;
  }
  const padding = trimmed.endsWith("==") ? 2 : trimmed.endsWith("=") ? 1 : 0;
  return (trimmed.length * 3) / 4 - padding;
}

function containsForbiddenKeysOrUrls(value: unknown, depth = 0): string | null {
  if (depth > 8 || value === null || value === undefined) {
    return null;
  }
  if (typeof value === "string") {
    if (SUSPICIOUS_REF_PATTERN.test(value.trim())) {
      return "Giá trị chuỗi chứa đường dẫn nội bộ hoặc URL không được phép.";
    }
    return null;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      const err = containsForbiddenKeysOrUrls(item, depth + 1);
      if (err) return err;
    }
    return null;
  }
  if (typeof value === "object") {
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (FORBIDDEN_WIRE_KEYS.has(k)) {
        return `Trường '${k}' bị cấm truyền qua mạng theo hợp đồng bảo mật CT-06/CT-07.`;
      }
      // Skip scanning huge base64Data string via URL regex once key is verified separately
      if (k === "base64Data") {
        continue;
      }
      const err = containsForbiddenKeysOrUrls(v, depth + 1);
      if (err) return err;
    }
  }
  return null;
}

async function sha256Hex(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function validateWireImage(
  img: unknown,
  expectedView: ImageView,
  maxDecodedBytes: number,
  label: string,
): { ok: true; image: WireImageInput; decodedBytes: number } | { ok: false; code: TryOnErrorCode; message: string } {
  if (!img || typeof img !== "object") {
    return { ok: false, code: "NEEDS_INPUT", message: `Thiếu dữ liệu ảnh bắt buộc cho ${label}.` };
  }
  const candidate = img as Partial<WireImageInput>;
  if (!candidate.mediaId || typeof candidate.mediaId !== "string") {
    return { ok: false, code: "INVALID_REQUEST", message: `Ảnh ${label} thiếu mediaId hợp lệ.` };
  }
  if (candidate.view !== expectedView) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Ảnh ${label} khai báo góc nhìn '${String(candidate.view)}' không khớp '${expectedView}'.`,
    };
  }
  if (
    candidate.mimeType !== "image/jpeg" &&
    candidate.mimeType !== "image/png" &&
    candidate.mimeType !== "image/webp"
  ) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Định dạng ảnh ${label} phải là image/jpeg, image/png hoặc image/webp.`,
    };
  }
  if (
    typeof candidate.width !== "number" ||
    typeof candidate.height !== "number" ||
    candidate.width <= 0 ||
    candidate.height <= 0 ||
    typeof candidate.revision !== "number" ||
    candidate.revision < 1 ||
    !candidate.contentHash ||
    typeof candidate.contentHash !== "string"
  ) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Siêu dữ liệu kích thước/revision/hash của ảnh ${label} không hợp lệ.`,
    };
  }
  if (typeof candidate.base64Data !== "string") {
    return { ok: false, code: "INVALID_REQUEST", message: `Ảnh ${label} thiếu nội dung base64.` };
  }
  if (/^data:|^https?:\/\//i.test(candidate.base64Data.trim())) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Ảnh ${label} không được chứa URL hoặc tiền tố data URI chưa chuẩn hóa.`,
    };
  }
  const decodedBytes = estimateDecodedBase64Bytes(candidate.base64Data);
  if (decodedBytes === null || decodedBytes <= 0) {
    return { ok: false, code: "INVALID_REQUEST", message: `Dữ liệu base64 của ${label} bị hỏng.` };
  }
  if (decodedBytes > maxDecodedBytes) {
    return {
      ok: false,
      code: "PAYLOAD_TOO_LARGE",
      message: `Dung lượng ảnh ${label} vượt giới hạn cho phép (${maxDecodedBytes} bytes).`,
    };
  }
  const binaryBytes = decodeStrictBase64(candidate.base64Data);
  const inspected = binaryBytes ? inspectImageBytes(binaryBytes) : null;
  if (!binaryBytes || inspected === null) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Dữ liệu ảnh ${label} bị hỏng hoặc không khớp magic bytes của JPEG/PNG/WebP.`,
    };
  }
  if (
    inspected.effectiveWidth > MAX_IMAGE_DIMENSION_PX ||
    inspected.effectiveHeight > MAX_IMAGE_DIMENSION_PX
  ) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Kích thước ảnh ${label} (${inspected.effectiveWidth}x${inspected.effectiveHeight}px) vượt giới hạn tối đa (${MAX_IMAGE_DIMENSION_PX}px).`,
    };
  }
  if (inspected.pixelCount > MAX_DECODED_PIXEL_COUNT) {
    return {
      ok: false,
      code: "PAYLOAD_TOO_LARGE",
      message: `Độ phân giải ảnh ${label} vượt trần bộ nhớ giải mã an toàn.`,
    };
  }

  return {
    ok: true,
    image: candidate as WireImageInput,
    decodedBytes,
  };
}

function computeAggregateJobStatus(
  requestedViews: ImageView[],
  views: Partial<Record<ImageView, StoredChildViewJob>>,
  currentStatus: TryOnJobStatus,
): TryOnJobStatus {
  if (
    currentStatus === "CANCELLED" ||
    currentStatus === "DELETED" ||
    currentStatus === "UNKNOWN_SUBMISSION"
  ) {
    return currentStatus;
  }

  let succeeded = 0;
  let active = 0;
  let failedOrExpired = 0;

  for (const v of requestedViews) {
    const child = views[v];
    if (!child) continue;
    if (child.state === "SUCCEEDED") {
      succeeded += 1;
    } else if (child.state === "QUEUED" || child.state === "RUNNING") {
      active += 1;
    } else if (
      child.state === "FAILED" ||
      child.state === "EXPIRED" ||
      child.state === "CANCELLED"
    ) {
      failedOrExpired += 1;
    }
  }

  if (active > 0) {
    const allQueued = requestedViews.every((v) => views[v]?.state === "QUEUED");
    return allQueued ? "QUEUED" : "RUNNING";
  }

  if (succeeded === requestedViews.length && succeeded > 0) {
    return "SUCCEEDED";
  }

  if (succeeded > 0 && failedOrExpired > 0) {
    // CT-06: Partial front success / back fail MUST be PARTIAL, NEVER SUCCEEDED.
    return "PARTIAL";
  }

  return currentStatus === "TIMED_OUT" ? "TIMED_OUT" : "FAILED";
}

function toWireResult(record: StoredTryOnJobRecord): TryOnResultWire {
  const wireViews: Partial<Record<ImageView, ViewResultWire>> = {};
  for (const view of record.requestedViews) {
    const child = record.views[view];
    if (child) {
      wireViews[view] = {
        view: child.view,
        state: child.state,
        output: record.status === "DELETED" ? null : child.output,
        error: child.error,
        qualityReview: child.qualityReview,
      };
    }
  }

  return {
    jobId: record.jobId,
    requestId: record.requestId,
    outfitId: record.outfitId,
    inputGeneration: record.inputGeneration,
    inputRevisions: { ...record.inputRevisions },
    status: record.status,
    views: wireViews,
    appliedItemIds: [...record.appliedItemIds],
    unappliedItemIds: [...record.unappliedItemIds],
    presentationMode: record.presentationMode ?? "ORIGINAL_PHOTO",
    ...(record.providerId ? { providerId: record.providerId } : {}),
    providerConfigVersion: record.providerConfigVersion,
    createdAt: record.createdAt,
    expiresAt: record.expiresAt,
    source: record.source,
    warnings: [...record.warnings],
    remoteDeletionState: record.remoteDeletionState,
  };
}

export interface TryOnRouterDependencies {
  store?: DurableTryOnJobStore;
  provider?: UpstreamVtoProvider;
  now?: () => number;
  idGenerator?: () => string;
  logger?: SafeTryOnLogger;
}

export interface TryOnRouter {
  handle(
    request: Request,
    env: TryOnEnvConfig,
    context?: TryOnRouteContext,
  ): Promise<Response>;
}

export function createTryOnRouter(deps: TryOnRouterDependencies = {}): TryOnRouter {
  const store = deps.store ?? new InMemoryTryOnJobStore();
  const now = deps.now ?? (() => Date.now());
  const nextId = deps.idGenerator ?? (() => crypto.randomUUID());
  const log = deps.logger ?? defaultSafeLogger;
  const inFlightSubmissions = new Map<string, Promise<StoredTryOnJobRecord>>();

  interface ResolvedProviderContext {
    ok: true;
    provider: UpstreamVtoProvider;
    decision: FeasibilityDecision;
    configVersion: string;
    executionMode: "FAKE" | "OFFLINE_MOCK_OPENAI" | "OFFLINE_MOCK_FOSTA" | "AI_LIVE";
    modelId?: string;
    timeoutMs?: number;
    liveCostGuard?: {
      maxRequests: number;
      maxEstimatedUsd: number;
      estimatedPerCallUsd: number;
      unpricedQuotaMode?: boolean;
    };
  }

  function resolveProvider(env: TryOnEnvConfig):
    | ResolvedProviderContext
    | { ok: false; code: TryOnErrorCode; message: string; status: number } {
    if (env.VTO_KILL_SWITCH === "true" || env.VTO_MODE === "DISABLED") {
      return {
        ok: false,
        code: "UNSUPPORTED_CAPABILITY",
        message: "Tính năng thử đồ ảo hiện đang tạm tắt bởi hệ thống điều phối.",
        status: 503,
      };
    }

    const configVersion = env.VTO_PROVIDER_CONFIG_VERSION ?? "v2-poc-1";
    const explicitProvider = env.TRYON_PROVIDER?.trim().toLowerCase();

    // --- Official OpenAI Images Edit Pipeline (`TRYON_PROVIDER=openai`) ---
    if (explicitProvider === "openai") {
      const liveEnabled = env.TRYON_LIVE_ENABLED === "true";
      const offlineMockEnabled = env.TRYON_OFFLINE_MOCK_OPENAI === "true";
      const allowCustomModel = env.OPENAI_ALLOW_CUSTOM_MODEL === "true";
      const quality = (env.OPENAI_IMAGE_QUALITY?.trim().toLowerCase() || "medium") as OpenAiQualityTier;
      const outputFormat = (env.OPENAI_OUTPUT_FORMAT?.trim().toLowerCase() || "png") as OpenAiOutputFormat;
      const inputFidelity = (env.OPENAI_INPUT_FIDELITY?.trim().toLowerCase() || "auto") as OpenAiInputFidelityOption;
      const size = env.OPENAI_IMAGE_SIZE?.trim() || "1024x1536";
      const timeoutMs = Number(env.OPENAI_TIMEOUT_MS ?? "90000");

      if (liveEnabled && offlineMockEnabled) {
        return {
          ok: false,
          code: "INVALID_REQUEST",
          message:
            "Không được bật đồng thời TRYON_LIVE_ENABLED=true và TRYON_OFFLINE_MOCK_OPENAI=true.",
          status: 500,
        };
      }

      // Default safe state: LIVE is OFF (`TRYON_LIVE_ENABLED !== "true"`)
      if (!liveEnabled) {
        if (!offlineMockEnabled) {
          return {
            ok: false,
            code: "FORBIDDEN",
            message:
              "Cổng OpenAI LIVE mặc định đang OFF (TRYON_LIVE_ENABLED=false). Không thực hiện cuộc gọi trả phí khi chưa được bật và phê duyệt ngân sách.",
            status: 503,
          };
        }

        // Explicit Offline End-to-End OpenAI Schema Mock Mode (`TRYON_OFFLINE_MOCK_OPENAI=true`)
        const mockModelRaw = env.OPENAI_IMAGE_MODEL?.trim() || "gpt-image-2";
        const mockModelCheck = validateOpenAiModelSelection(mockModelRaw, allowCustomModel);
        if (!mockModelCheck.ok) {
          return {
            ok: false,
            code: mockModelCheck.code,
            message: mockModelCheck.message,
            status: HTTP_STATUS_BY_ERROR[mockModelCheck.code],
          };
        }
        const mockDecision = env.VTO_FEASIBILITY_DECISION ?? "GO_FRONT_ONLY";
        if (mockDecision === "NO_GO" || mockDecision === "UNKNOWN") {
          return {
            ok: false,
            code: "UNSUPPORTED_CAPABILITY",
            message: `Tính năng thử đồ không khả dụng ở trạng thái cổng '${mockDecision}'.`,
            status: 400,
          };
        }

        const scenario = (env.TRYON_MOCK_SCENARIO?.trim() || "success") as OpenAiMockScenario;
        const mockController = createOpenAiImageEditsMock(scenario);
        try {
          const mockOpenAiProvider =
            deps.provider ??
            new OpenAITryOnProvider({
              apiKey: "offline-mock-token-not-a-real-key",
              model: mockModelCheck.modelId,
              baseUrl: "https://mock.openai.local/v1",
              size,
              quality,
              outputFormat,
              inputFidelity,
              allowCustomModel,
              timeoutMs,
              offlineMockMode: true,
              fetchImpl: mockController.fetchImpl,
            });
          return {
            ok: true,
            provider: mockOpenAiProvider,
            decision: mockDecision,
            configVersion,
            executionMode: "OFFLINE_MOCK_OPENAI",
            modelId: mockModelCheck.modelId,
          };
        } catch (err) {
          const code = err instanceof UpstreamDeterministicError ? err.code : "INVALID_REQUEST";
          const msg = err instanceof Error ? err.message : "Cấu hình OpenAI mock không hợp lệ.";
          return { ok: false, code, message: msg, status: HTTP_STATUS_BY_ERROR[code] };
        }
      }

      // --- LIVE OpenAI Execution Path (`TRYON_LIVE_ENABLED === "true"`) ---
      const decision = env.VTO_FEASIBILITY_DECISION ?? "GO_FRONT_ONLY";
      if (decision !== "GO_FRONT_ONLY" && decision !== "GO_DUAL") {
        return {
          ok: false,
          code: "UNSUPPORTED_CAPABILITY",
          message: `Chế độ OpenAI LIVE chưa được mở ở trạng thái cổng '${decision}'.`,
          status: 503,
        };
      }
      if (!store.isDurable) {
        return {
          ok: false,
          code: "UNSUPPORTED_CAPABILITY",
          message:
            "Chế độ OpenAI LIVE yêu cầu kho trạng thái bền vững (Durable Store) để bảo vệ idempotency và tránh tính phí hai lần.",
          status: 503,
        };
      }
      if (!env.OPENAI_API_KEY || env.OPENAI_API_KEY.trim().length === 0) {
        return {
          ok: false,
          code: "UNAUTHORIZED",
          message: "Chưa cấu hình OPENAI_API_KEY ở phía máy chủ; hệ thống từ chối gọi OpenAI.",
          status: 503,
        };
      }
      if (env.TRYON_BUDGET_CONFIRMED !== "true") {
        return {
          ok: false,
          code: "FORBIDDEN",
          message:
            "Chưa xác nhận phê duyệt ngân sách LIVE (cần đặt TRYON_BUDGET_CONFIRMED=true sau khi kiểm tra API credits).",
          status: 403,
        };
      }

      const modelCheck = validateOpenAiModelSelection(env.OPENAI_IMAGE_MODEL, allowCustomModel);
      if (!modelCheck.ok) {
        return {
          ok: false,
          code: modelCheck.code,
          message: modelCheck.message,
          status: HTTP_STATUS_BY_ERROR[modelCheck.code],
        };
      }

      const maxRequests = Number(env.TRYON_LIVE_MAX_REQUESTS ?? "1");
      if (!Number.isInteger(maxRequests) || maxRequests < 1) {
        return {
          ok: false,
          code: "BUDGET_EXCEEDED",
          message: "Giới hạn TRYON_LIVE_MAX_REQUESTS phải là số nguyên dương (mặc định 1).",
          status: 503,
        };
      }

      const maxEstimatedUsd = Number(env.TRYON_LIVE_MAX_ESTIMATED_USD ?? "0");
      if (!Number.isFinite(maxEstimatedUsd) || maxEstimatedUsd <= 0) {
        return {
          ok: false,
          code: "BUDGET_EXCEEDED",
          message:
            "Chưa cấp hạn mức chi phí tối đa hợp lệ (TRYON_LIVE_MAX_ESTIMATED_USD phải > 0).",
          status: 503,
        };
      }

      const estimatedPerCallUsd = estimateOpenAiEditMaxUsd({
        model: modelCheck.modelId,
        quality,
      });
      if (estimatedPerCallUsd === null && env.TRYON_ALLOW_UNPRICED_MODEL !== "true") {
        return {
          ok: false,
          code: "FORBIDDEN",
          message:
            "Không thể xác định chi phí ước tính tự động cho model tùy chỉnh này; hệ thống không giả định miễn phí. Cần đặt TRYON_ALLOW_UNPRICED_MODEL=true để xác nhận thủ công.",
          status: 403,
        };
      }

      try {
        const liveOpenAiProvider =
          deps.provider ??
          new OpenAITryOnProvider({
            apiKey: env.OPENAI_API_KEY,
            model: modelCheck.modelId,
            baseUrl: env.OPENAI_API_BASE_URL,
            organizationId: env.OPENAI_ORG_ID,
            projectId: env.OPENAI_PROJECT_ID,
            size,
            quality,
            outputFormat,
            inputFidelity,
            allowCustomModel,
            timeoutMs,
            offlineMockMode: false,
          });
        return {
          ok: true,
          provider: liveOpenAiProvider,
          decision,
          configVersion,
          executionMode: "AI_LIVE",
          modelId: modelCheck.modelId,
          liveCostGuard: {
            maxRequests,
            maxEstimatedUsd,
            // If custom unpriced model was explicitly authorized, reserve full approved budget per call as conservative cap
            estimatedPerCallUsd: estimatedPerCallUsd ?? maxEstimatedUsd,
          },
        };
      } catch (err) {
        const code = err instanceof UpstreamDeterministicError ? err.code : "INVALID_REQUEST";
        const msg = err instanceof Error ? err.message : "Cấu hình OpenAI LIVE không hợp lệ.";
        return { ok: false, code, message: msg, status: HTTP_STATUS_BY_ERROR[code] };
      }
    }

    // --- Experimental FOSTA Luna Responses Pipeline (`TRYON_PROVIDER=fosta`) ---
    if (explicitProvider === "fosta") {
      const liveEnabled = env.TRYON_LIVE_ENABLED === "true";
      const offlineMockEnabled = env.TRYON_OFFLINE_MOCK_FOSTA === "true";
      const fostaModel = env.FOSTA_MODEL?.trim() || DEFAULT_FOSTA_MODEL;
      const fostaTimeoutMs = Number(env.FOSTA_TIMEOUT_MS ?? String(DEFAULT_FOSTA_TIMEOUT_MS));

      if (liveEnabled && offlineMockEnabled) {
        return {
          ok: false,
          code: "INVALID_REQUEST",
          message:
            "Không được bật đồng thời TRYON_LIVE_ENABLED=true và TRYON_OFFLINE_MOCK_FOSTA=true.",
          status: 500,
        };
      }

      // Default safe state: FOSTA LIVE is OFF (`TRYON_LIVE_ENABLED !== "true"`) — zero quota consumed
      if (!liveEnabled) {
        if (!offlineMockEnabled) {
          return {
            ok: false,
            code: "FORBIDDEN",
            message:
              "Cổng FOSTA LIVE mặc định đang OFF (TRYON_LIVE_ENABLED=false). Không gọi tới FOSTA hoặc tự chuyển sang OpenAI khi chưa mở cổng và xác nhận FOSTA_APPROVE_QUOTA=true.",
            status: 503,
          };
        }

        const mockDecision = env.VTO_FEASIBILITY_DECISION ?? "GO_DUAL";
        if (mockDecision === "NO_GO" || mockDecision === "UNKNOWN") {
          return {
            ok: false,
            code: "UNSUPPORTED_CAPABILITY",
            message: `Tính năng thử đồ FOSTA không khả dụng ở trạng thái cổng '${mockDecision}'.`,
            status: 400,
          };
        }

        const scenario = (env.TRYON_FOSTA_MOCK_SCENARIO?.trim() || "success") as FostaMockScenario;
        const mockController = createFostaResponsesMock(scenario);
        try {
          const mockFostaProvider =
            deps.provider ??
            new FostaTryOnProvider({
              apiKey: "offline-mock-fosta-token-not-a-real-key",
              endpoint: "https://mock.fosta.local/v1/responses",
              model: fostaModel,
              timeoutMs: fostaTimeoutMs,
              offlineMockMode: true,
              fetchImpl: mockController.fetchImpl,
            });
          return {
            ok: true,
            provider: mockFostaProvider,
            decision: mockDecision,
            configVersion,
            executionMode: "OFFLINE_MOCK_FOSTA",
            modelId: fostaModel,
            timeoutMs: fostaTimeoutMs,
          };
        } catch (err) {
          const code = err instanceof UpstreamDeterministicError ? err.code : "INVALID_REQUEST";
          const msg = err instanceof Error ? err.message : "Cấu hình FOSTA mock không hợp lệ.";
          return { ok: false, code, message: msg, status: HTTP_STATUS_BY_ERROR[code] };
        }
      }

      // --- LIVE FOSTA Execution Path (`TRYON_LIVE_ENABLED === "true"`) ---
      const decision = env.VTO_FEASIBILITY_DECISION ?? "GO_DUAL";
      if (decision !== "GO_FRONT_ONLY" && decision !== "GO_DUAL") {
        return {
          ok: false,
          code: "UNSUPPORTED_CAPABILITY",
          message: `Chế độ FOSTA LIVE chưa được mở ở trạng thái cổng '${decision}'.`,
          status: 503,
        };
      }
      if (!store.isDurable) {
        return {
          ok: false,
          code: "UNSUPPORTED_CAPABILITY",
          message:
            "Chế độ FOSTA LIVE yêu cầu kho trạng thái bền vững (Durable Store) để bảo vệ idempotency và tránh tốn quota hai lần.",
          status: 503,
        };
      }
      if (!env.FOSTA_API_KEY || env.FOSTA_API_KEY.trim().length === 0) {
        return {
          ok: false,
          code: "UNAUTHORIZED",
          message:
            "Chưa cấu hình FOSTA_API_KEY ở phía máy chủ; hệ thống từ chối gọi FOSTA và KHÔNG tự chuyển sang OpenAI.",
          status: 503,
        };
      }
      if (env.FOSTA_APPROVE_QUOTA !== "true") {
        return {
          ok: false,
          code: "FORBIDDEN",
          message:
            "Chưa phê duyệt sử dụng hạn mức FOSTA (cần đặt FOSTA_APPROVE_QUOTA=true sau khi xác nhận thử nghiệm).",
          status: 403,
        };
      }

      const maxRequests = Number(env.TRYON_LIVE_MAX_REQUESTS ?? "1");
      if (!Number.isInteger(maxRequests) || maxRequests < 1) {
        return {
          ok: false,
          code: "BUDGET_EXCEEDED",
          message: "Giới hạn TRYON_LIVE_MAX_REQUESTS cho FOSTA phải là số nguyên dương (mặc định 1).",
          status: 503,
        };
      }

      try {
        const liveFostaProvider =
          deps.provider ??
          new FostaTryOnProvider({
            apiKey: env.FOSTA_API_KEY,
            endpoint: env.FOSTA_ENDPOINT,
            model: fostaModel,
            timeoutMs: fostaTimeoutMs,
            approvedLive: true,
            approvedQuotaUse: true,
            offlineMockMode: false,
          });
        return {
          ok: true,
          provider: liveFostaProvider,
          decision,
          configVersion,
          executionMode: "AI_LIVE",
          modelId: fostaModel,
          timeoutMs: fostaTimeoutMs,
          liveCostGuard: {
            maxRequests,
            maxEstimatedUsd: Number.MAX_SAFE_INTEGER,
            estimatedPerCallUsd: 0,
            unpricedQuotaMode: true,
          },
        };
      } catch (err) {
        const code = err instanceof UpstreamDeterministicError ? err.code : "INVALID_REQUEST";
        const msg = err instanceof Error ? err.message : "Cấu hình FOSTA LIVE không hợp lệ.";
        return { ok: false, code, message: msg, status: HTTP_STATUS_BY_ERROR[code] };
      }
    }

    // --- Legacy / Explicit FASHN or FAKE Provider Resolution ---
    const mode =
      explicitProvider === "fake"
        ? "FAKE"
        : explicitProvider === "fashn"
          ? "AI_LIVE"
          : (env.VTO_MODE ?? "FAKE");

    if (mode === "AI_LIVE") {
      const decision = env.VTO_FEASIBILITY_DECISION ?? "UNKNOWN";
      if (decision !== "GO_FRONT_ONLY" && decision !== "GO_DUAL") {
        return {
          ok: false,
          code: "UNSUPPORTED_CAPABILITY",
          message: `Chế độ live VTO chưa được mở do kết quả thẩm định khả thi hiện tại là '${decision}'.`,
          status: 503,
        };
      }
      if (!store.isDurable) {
        return {
          ok: false,
          code: "UNSUPPORTED_CAPABILITY",
          message:
            "Chế độ live VTO yêu cầu kho lưu trữ trạng thái bền vững (Durable Store); không sử dụng bộ nhớ tạm của Worker.",
          status: 503,
        };
      }
      if (deps.provider) {
        return { ok: true, provider: deps.provider, decision, configVersion, executionMode: "AI_LIVE" };
      }
      if (!env.FASHN_API_KEY) {
        return {
          ok: false,
          code: "UNSUPPORTED_CAPABILITY",
          message: "Chưa cấu hình khóa kết nối dịch vụ thử đồ ở phía máy chủ.",
          status: 503,
        };
      }
      return {
        ok: true,
        provider: new FashnUpstreamProvider({ apiKey: env.FASHN_API_KEY }),
        decision,
        configVersion,
        executionMode: "AI_LIVE",
      };
    }

    // FAKE mode (offline unit/contract testing)
    const fakeDecision = env.VTO_FEASIBILITY_DECISION ?? "GO_DUAL";
    if (fakeDecision === "NO_GO" || fakeDecision === "UNKNOWN") {
      return {
        ok: false,
        code: "UNSUPPORTED_CAPABILITY",
        message: `Tính năng thử đồ không khả dụng ở trạng thái cổng '${fakeDecision}'.`,
        status: 400,
      };
    }
    return {
      ok: true,
      provider: deps.provider ?? new FakeUpstreamVtoProvider(),
      decision: fakeDecision,
      configVersion,
      executionMode: "FAKE",
    };
  }

  async function handleSubmit(
    request: Request,
    env: TryOnEnvConfig,
    sessionId: string,
    startedAt: number,
  ): Promise<Response> {
    const fallbackReqId = `req_${nextId()}`;
    const rawBuffer = await request.arrayBuffer();
    if (rawBuffer.byteLength > MAX_WIRE_BYTES) {
      log({
        event: "tryon.submit.rejected",
        service: "tryon-v2",
        requestId: fallbackReqId,
        errorCode: "PAYLOAD_TOO_LARGE",
        latencyMs: now() - startedAt,
      });
      return makeTypedError(
        "PAYLOAD_TOO_LARGE",
        "Tổng dung lượng yêu cầu vượt quá giới hạn 12 MiB.",
        fallbackReqId,
      );
    }

    let parsedUnknown: unknown;
    try {
      parsedUnknown = JSON.parse(new TextDecoder().decode(rawBuffer));
    } catch {
      return makeTypedError(
        "INVALID_REQUEST",
        "Nội dung JSON không hợp lệ.",
        fallbackReqId,
      );
    }

    if (!parsedUnknown || typeof parsedUnknown !== "object") {
      return makeTypedError("INVALID_REQUEST", "Body phải là JSON object.", fallbackReqId);
    }

    const SAFE_REQ_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
    const candidateReqId =
      typeof (parsedUnknown as { requestId?: unknown }).requestId === "string"
        ? (parsedUnknown as { requestId: string }).requestId.trim()
        : "";
    const rawReqId = SAFE_REQ_ID_PATTERN.test(candidateReqId)
      ? candidateReqId
      : fallbackReqId;

    const securityViolation = containsForbiddenKeysOrUrls(parsedUnknown);
    if (securityViolation) {
      log({
        event: "tryon.submit.rejected",
        service: "tryon-v2",
        requestId: rawReqId,
        errorCode: "INVALID_REQUEST",
        latencyMs: now() - startedAt,
      });
      return makeTypedError("INVALID_REQUEST", securityViolation, rawReqId);
    }

    const resolved = resolveProvider(env);
    if (!resolved.ok) {
      log({
        event: "tryon.submit.rejected",
        service: "tryon-v2",
        requestId: rawReqId,
        errorCode: resolved.code,
        latencyMs: now() - startedAt,
      });
      return makeTypedError(resolved.code, resolved.message, rawReqId, false, resolved.status);
    }
    const { provider, decision, configVersion } = resolved;

    const body = parsedUnknown as Partial<TryOnSubmitWireRequest>;
    if (
      body.schemaVersion !== 2 ||
      !body.idempotencyKey ||
      typeof body.idempotencyKey !== "string" ||
      typeof body.inputGeneration !== "number" ||
      body.inputGeneration < 1 ||
      typeof body.profileRevision !== "number" ||
      body.profileRevision < 1 ||
      !body.outfitSnapshot ||
      typeof body.outfitSnapshot.outfitId !== "string" ||
      !Array.isArray(body.outfitSnapshot.items) ||
      body.outfitSnapshot.items.length === 0
    ) {
      return makeTypedError(
        "INVALID_REQUEST",
        "Thiếu thông tin bắt buộc theo schemaVersion=2 của CT-06.",
        rawReqId,
      );
    }

    if (body.providerConfigVersion !== configVersion) {
      return makeTypedError(
        "STALE_INPUT",
        "Phiên bản cấu hình dịch vụ thử đồ đã thay đổi; vui lòng xác nhận lại quyền đồng ý.",
        rawReqId,
      );
    }

    const rawPresentationMode = body.presentationMode;
    if (
      rawPresentationMode !== undefined &&
      rawPresentationMode !== null &&
      rawPresentationMode !== "ORIGINAL_PHOTO" &&
      rawPresentationMode !== "STUDIO_WHITE"
    ) {
      return makeTypedError(
        "INVALID_REQUEST",
        "Chế độ hiển thị presentationMode không hợp lệ (chỉ hỗ trợ ORIGINAL_PHOTO hoặc STUDIO_WHITE).",
        rawReqId,
      );
    }
    const presentationMode: TryOnPresentationMode = rawPresentationMode ?? "ORIGINAL_PHOTO";
    if (presentationMode === "STUDIO_WHITE" && provider.providerId === "openai") {
      return makeTypedError(
        "UNSUPPORTED_CAPABILITY",
        "Chế độ nền trắng STUDIO_WHITE hiện chỉ hỗ trợ trên FostaTryOnProvider (gpt-6-luna).",
        rawReqId,
      );
    }

    const requestedViews = Array.isArray(body.requestedViews)
      ? Array.from(new Set(body.requestedViews))
      : [];
    if (
      requestedViews.length === 0 ||
      requestedViews.some((v) => v !== "FRONT" && v !== "BACK")
    ) {
      return makeTypedError(
        "INVALID_REQUEST",
        "Cần chọn ít nhất một góc nhìn hợp lệ (FRONT hoặc BACK).",
        rawReqId,
      );
    }

    if (decision === "GO_FRONT_ONLY" && requestedViews.includes("BACK")) {
      return makeTypedError(
        "UNSUPPORTED_CAPABILITY",
        "Chế độ hiện tại chỉ hỗ trợ mô phỏng mặt trước (FRONT_ONLY); không hỗ trợ mặt sau.",
        rawReqId,
      );
    }

    const selectedItemIds = Array.isArray(body.selectedItemIds)
      ? Array.from(new Set(body.selectedItemIds))
      : [];
    if (selectedItemIds.length === 0) {
      return makeTypedError(
        "NEEDS_INPUT",
        "Cần chọn ít nhất một món trang phục trong outfit để thử đồ.",
        rawReqId,
      );
    }

    if (selectedItemIds.length > provider.maxGarmentsPerJob) {
      return makeTypedError(
        "UNSUPPORTED_CAPABILITY",
        `Dịch vụ hiện chỉ hỗ trợ tối đa ${provider.maxGarmentsPerJob} món trang phục mỗi lượt thử.`,
        rawReqId,
      );
    }

    const outfitItemsByIdPreview = new Map(
      body.outfitSnapshot.items.map((item) => [item.itemId, item]),
    );
    let selectedTopCount = 0;
    let selectedBottomCount = 0;
    for (const itemId of selectedItemIds) {
      const summary = outfitItemsByIdPreview.get(itemId);
      if (!summary) {
        return makeTypedError(
          "INVALID_REQUEST",
          `Món đồ '${itemId}' không thuộc outfit đã chọn.`,
          rawReqId,
        );
      }
      if (!provider.supportedCategories.has(summary.category)) {
        return makeTypedError(
          "UNSUPPORTED_CAPABILITY",
          `Danh mục '${summary.category}' chưa được mô hình thử đồ hỗ trợ.`,
          rawReqId,
        );
      }
      if (summary.category === "TOP") selectedTopCount += 1;
      if (summary.category === "BOTTOM") selectedBottomCount += 1;
    }

    if (provider.providerId === "fosta" && (selectedTopCount > 1 || selectedBottomCount > 1)) {
      return makeTypedError(
        "INVALID_REQUEST",
        "Trong một lượt thử FOSTA chỉ được chọn tối đa 1 Áo (TOP) và 1 Quần (BOTTOM).",
        rawReqId,
      );
    }

    // Validate per-request ConsentGrant + strict providerId binding (CT-03, CT-06, C-210, Section 11)
    const consent = body.consentGrant;
    if (
      !consent ||
      consent.confirmedByUser !== true ||
      consent.purpose !== "TRYON" ||
      consent.revokedAtEpochMillis != null ||
      consent.providerId !== provider.providerId ||
      consent.providerConfigVersion !== configVersion ||
      consent.requestGeneration !== body.inputGeneration ||
      !Array.isArray(consent.allowedViews) ||
      !Array.isArray(consent.selectedMediaIds)
    ) {
      log({
        event: "tryon.submit.rejected",
        service: "tryon-v2",
        requestId: rawReqId,
        providerId: provider.providerId,
        mode: resolved.executionMode,
        errorCode: "CONSENT_REQUIRED",
        latencyMs: now() - startedAt,
      });
      return makeTypedError(
        "CONSENT_REQUIRED",
        `Cần có xác nhận đồng ý hợp lệ gắn đúng với nhà cung cấp '${provider.providerId}' và phiên bản cấu hình hiện tại trước khi gửi ảnh.`,
        rawReqId,
      );
    }

    const allowedViewSet = new Set(consent.allowedViews);
    for (const v of requestedViews) {
      if (!allowedViewSet.has(v)) {
        return makeTypedError(
          "CONSENT_REQUIRED",
          `Người dùng chưa cấp quyền xử lý cho góc nhìn '${v}'.`,
          rawReqId,
        );
      }
    }

    // Validate required person & garment images per requested view + decoded byte caps
    const consentedMediaIds = new Set(consent.selectedMediaIds);
    let totalDecodedBytes = 0;
    const validatedPerson: Partial<Record<ImageView, WireImageInput>> = {};
    const validatedGarments: Record<string, Partial<Record<ImageView, WireImageInput>>> = {};

    for (const view of requestedViews) {
      const rawPersonImg =
        view === "FRONT" ? body.personImages?.front : body.personImages?.back;
      const personCheck = validateWireImage(
        rawPersonImg,
        view,
        MAX_PERSON_IMAGE_BYTES,
        `person.${view}`,
      );
      if (!personCheck.ok) {
        return makeTypedError(personCheck.code, personCheck.message, rawReqId);
      }
      if (!consentedMediaIds.has(personCheck.image.mediaId)) {
        return makeTypedError(
          "CONSENT_REQUIRED",
          `Ảnh cá nhân '${personCheck.image.mediaId}' không nằm trong danh sách đã được đồng ý.`,
          rawReqId,
        );
      }
      validatedPerson[view] = personCheck.image;
      totalDecodedBytes += personCheck.decodedBytes;

      for (const itemId of selectedItemIds) {
        const pair = body.garmentImages?.[itemId];
        const rawGarmentImg = view === "FRONT" ? pair?.front : pair?.back;
        const garmentCheck = validateWireImage(
          rawGarmentImg,
          view,
          MAX_GARMENT_IMAGE_BYTES,
          `garment.${itemId}.${view}`,
        );
        if (!garmentCheck.ok) {
          return makeTypedError(garmentCheck.code, garmentCheck.message, rawReqId);
        }
        if (!consentedMediaIds.has(garmentCheck.image.mediaId)) {
          return makeTypedError(
            "CONSENT_REQUIRED",
            `Ảnh trang phục '${garmentCheck.image.mediaId}' không nằm trong danh sách đã được đồng ý.`,
            rawReqId,
          );
        }
        validatedGarments[itemId] = validatedGarments[itemId] ?? {};
        validatedGarments[itemId]![view] = garmentCheck.image;
        totalDecodedBytes += garmentCheck.decodedBytes;
      }
    }

    // Reject extra unrequested views/items smuggled in wire payload (payload minimization CT-06/C-210)
    if (!requestedViews.includes("FRONT") && body.personImages?.front != null) {
      return makeTypedError(
        "INVALID_REQUEST",
        "Không được gửi kèm ảnh mặt trước khi không yêu cầu xử lý FRONT.",
        rawReqId,
      );
    }
    if (!requestedViews.includes("BACK") && body.personImages?.back != null) {
      return makeTypedError(
        "INVALID_REQUEST",
        "Không được gửi kèm ảnh mặt sau khi không yêu cầu xử lý BACK.",
        rawReqId,
      );
    }
    const garmentPayloadKeys = Object.keys(body.garmentImages ?? {});
    for (const key of garmentPayloadKeys) {
      if (!selectedItemIds.includes(key)) {
        return makeTypedError(
          "INVALID_REQUEST",
          `Không được gửi ảnh của món đồ '${key}' không nằm trong selectedItemIds.`,
          rawReqId,
        );
      }
    }

    // CT-01 / CT-06: Front & Back must NOT be cloned duplicates when both are requested
    if (
      validatedPerson.FRONT &&
      validatedPerson.BACK &&
      validatedPerson.FRONT.contentHash === validatedPerson.BACK.contentHash
    ) {
      return makeTypedError(
        "INVALID_REQUEST",
        "Không được nhân bản ảnh mặt trước làm ảnh mặt sau của người dùng.",
        rawReqId,
      );
    }
    for (const itemId of selectedItemIds) {
      const gPair = validatedGarments[itemId];
      if (
        gPair?.FRONT &&
        gPair?.BACK &&
        gPair.FRONT.contentHash === gPair.BACK.contentHash
      ) {
        return makeTypedError(
          "INVALID_REQUEST",
          `Không được nhân bản ảnh mặt trước làm ảnh mặt sau cho món đồ '${itemId}'.`,
          rawReqId,
        );
      }
    }

    if (totalDecodedBytes > MAX_TOTAL_DECODED_BYTES) {
      return makeTypedError(
        "PAYLOAD_TOO_LARGE",
        "Tổng dung lượng giải mã của các ảnh vượt quá giới hạn 8 MiB.",
        rawReqId,
      );
    }

    const outfitItemsById = new Map(
      body.outfitSnapshot.items.map((item) => [item.itemId, item]),
    );

    // Run provider-level local input & image checks BEFORE reserving job/budget/live quota
    // so invalid inputs (e.g. dimension < minInputDimensionPx, MIME mismatch, byte cap)
    // never consume `usedLiveRequests` or trigger any upstream call.
    if (provider.validateViewInput) {
      for (const view of requestedViews) {
        const garmentsForView = selectedItemIds.map((itemId) => {
          const itemSummary = outfitItemsById.get(itemId)!;
          return {
            itemId,
            category: itemSummary.category as Category,
            colors: itemSummary.colors,
            image: validatedGarments[itemId]![view]!,
          };
        });
        try {
          provider.validateViewInput({
            jobId: "preflight_local_validation",
            requestId: rawReqId,
            view,
            presentationMode,
            personImage: validatedPerson[view]!,
            garments: garmentsForView,
          });
        } catch (err) {
          const code =
            err instanceof UpstreamDeterministicError ? err.code : "INVALID_REQUEST";
          const retryable =
            err instanceof UpstreamDeterministicError ? err.retryable : false;
          const message =
            err instanceof Error ? err.message : "Ảnh đầu vào không hợp lệ.";
          log({
            event: "tryon.submit.rejected",
            service: "tryon-v2",
            requestId: rawReqId,
            providerId: provider.providerId,
            modelId: resolved.modelId,
            mode: resolved.executionMode,
            errorCode: code,
            latencyMs: now() - startedAt,
          });
          return makeTypedError(code, message, rawReqId, retryable);
        }
      }
    }

    // Compute canonical payload hash for session + inputGeneration + idempotencyKey
    const canonicalSummary = JSON.stringify({
      outfitId: body.outfitSnapshot.outfitId,
      wardrobeSnapshotId: body.outfitSnapshot.wardrobeSnapshotId,
      profileRevision: body.profileRevision,
      inputGeneration: body.inputGeneration,
      presentationMode,
      requestedViews: [...requestedViews].sort(),
      selectedItemIds: [...selectedItemIds].sort(),
      consentId: consent.id,
      personHashes: requestedViews.map((v) => `${v}:${validatedPerson[v]!.contentHash}@${validatedPerson[v]!.revision}`),
      garmentHashes: selectedItemIds.flatMap((id) =>
        requestedViews.map(
          (v) => `${id}:${v}:${validatedGarments[id]![v]!.contentHash}@${validatedGarments[id]![v]!.revision}`,
        ),
      ),
    });
    const payloadHash = await sha256Hex(canonicalSummary);
    const idempotencyScopeKey = `${sessionId}::${body.inputGeneration}::${body.idempotencyKey.trim()}`;

    const createdAt = now();
    const baseDeadlineMs =
      requestedViews.length > 1 ? DUAL_DEADLINE_MS : FRONT_DEADLINE_MS;
    const deadlineMs = resolved.timeoutMs
      ? Math.max(baseDeadlineMs, resolved.timeoutMs + 15_000)
      : baseDeadlineMs;
    const unappliedItemIds = body.outfitSnapshot.items
      .map((i) => i.itemId)
      .filter((id) => !selectedItemIds.includes(id));

    const warnings: string[] = [];
    if (resolved.executionMode === "OFFLINE_MOCK_OPENAI") {
      warnings.push(
        "OFFLINE_MOCK_OPENAI: Đã chạy toàn bộ đường ống OpenAI multipart & giải mã cục bộ ở chế độ MOCK (0 phí API — KHÔNG phải ảnh do AI sinh thật).",
      );
    }
    if (resolved.executionMode === "OFFLINE_MOCK_FOSTA") {
      warnings.push(
        "OFFLINE_MOCK_FOSTA: Đã chạy toàn bộ đường ống FOSTA Responses API cục bộ ở chế độ MOCK (0 quota — KHÔNG phải ảnh do AI sinh thật).",
      );
    }
    if (provider.providerId === "fosta") {
      warnings.push(
        "FOSTA_THIRD_PARTY_UNVERIFIED_PROXY: Nhà cung cấp thử nghiệm bên thứ ba (codex.fosta.app / gpt-6-luna); chưa xác minh chính sách lưu giữ dữ liệu từ xa.",
      );
      if (presentationMode === "STUDIO_WHITE") {
        warnings.push(
          "STUDIO_WHITE_REPOSING_WARNING: Chế độ nền trắng Studio dựng lại tư thế đứng toàn thân, có thể làm thay đổi tỷ lệ cơ thể và không dùng để kiểm tra độ vừa kích cỡ.",
        );
      }
      if (requestedViews.includes("BACK")) {
        warnings.push(
          "EXPERIMENTAL_UNVALIDATED_BACK: Góc nhìn mặt sau (BACK) mới chỉ kiểm thử logic, chưa được kiểm chứng chất lượng AI thực tế.",
        );
      }
      if (selectedBottomCount > 0 && selectedTopCount === 0) {
        warnings.push(
          "EXPERIMENTAL_UNVALIDATED_BOTTOM: Chế độ chỉ thay quần (BOTTOM) mới chỉ kiểm thử logic, chưa được kiểm chứng chất lượng AI thực tế.",
        );
      }
      if (selectedTopCount > 0 && selectedBottomCount > 0) {
        warnings.push(
          "EXPERIMENTAL_UNVALIDATED_TOP_BOTTOM: Chế độ thay đồng thời Áo + Quần (TOP+BOTTOM) mới chỉ kiểm thử logic, chưa được kiểm chứng chất lượng AI thực tế.",
        );
      }
    }
    if (requestedViews.length > 1) {
      warnings.push(
        "INDEPENDENT_VIEW_GENERATION: Ảnh mặt trước và mặt sau được tạo bởi hai tiến trình độc lập, không có cam kết đồng nhất đa góc nhìn tự nhiên.",
      );
    }
    if (unappliedItemIds.length > 0) {
      warnings.push(
        `SUBSET_GARMENTS_ONLY: Chỉ mô phỏng ${selectedItemIds.length}/${body.outfitSnapshot.items.length} món trong outfit.`,
      );
    }

    const inputRevisions: Record<string, number> = {
      profile: body.profileRevision,
    };
    for (const item of body.outfitSnapshot.items) {
      inputRevisions[`item:${item.itemId}`] = item.revision;
    }

    const initialViews: Partial<Record<ImageView, StoredChildViewJob>> = {};
    for (const view of requestedViews) {
      initialViews[view] = {
        view,
        state: "QUEUED",
        upstreamPredictionId: null,
        output: null,
        error: null,
        qualityReview: "UNREVIEWED",
      };
    }

    const candidateJobId = `job_${nextId()}`;
    const candidateRecord: StoredTryOnJobRecord = {
      jobId: candidateJobId,
      sessionId,
      requestId: rawReqId,
      idempotencyScopeKey,
      payloadHash,
      outfitId: body.outfitSnapshot.outfitId,
      inputGeneration: body.inputGeneration,
      inputRevisions,
      status: "QUEUED",
      requestedViews,
      views: initialViews,
      appliedItemIds: selectedItemIds,
      unappliedItemIds,
      presentationMode,
      providerId: provider.providerId,
      providerConfigVersion: configVersion,
      createdAt,
      deadlineAt: createdAt + deadlineMs,
      expiresAt: null,
      deletedAt: null,
      cancelledAt: null,
      source: provider.source,
      warnings,
      remoteDeletionState: provider.source === "FAKE" ? "NOT_UPLOADED" : "UNKNOWN",
    };

    // Persist idempotency reservation BEFORE calling upstream provider!
    const reservation = await store.reserveJobAtomically(candidateRecord);

    if (reservation.kind === "CONFLICT_DIFFERENT_PAYLOAD") {
      return makeTypedError(
        "CONFLICT",
        "Khóa idempotencyKey đã được dùng cho một nội dung yêu cầu khác.",
        rawReqId,
      );
    }

    if (reservation.kind === "ACTIVE_JOB_EXISTS") {
      return makeTypedError(
        "CONFLICT",
        "Phiên hiện tại đang có một tiến trình thử đồ chưa hoàn tất; chỉ cho phép tối đa 1 tiến trình mỗi phiên.",
        rawReqId,
      );
    }

    if (reservation.kind === "EXISTING_SAME_PAYLOAD") {
      const inFlight = inFlightSubmissions.get(reservation.record.jobId);
      const settled = inFlight ? await inFlight : reservation.record;
      log({
        event: "tryon.submit.replayed",
        service: "tryon-v2",
        requestId: settled.requestId,
        jobId: settled.jobId,
        status: settled.status,
        latencyMs: now() - startedAt,
      });
      const replayResp: TryOnJobSubmitResponse = {
        jobId: settled.jobId,
        requestId: settled.requestId,
        status: settled.status,
      };
      return jsonResponse(replayResp, 202);
    }

    // Reserve hard monetary budget units & LIVE OpenAI request/USD cost-guard BEFORE upstream live calls
    const hardBudgetCap = Number(env.VTO_BUDGET_CAP_UNITS ?? DEFAULT_BUDGET_CAP_UNITS);
    const unitsNeeded = provider.source === "AI_LIVE" ? requestedViews.length : 0;
    const budgetCheck = await store.reserveBudgetUnits(unitsNeeded, hardBudgetCap);
    if (!budgetCheck.allowed) {
      await store.updateJob(candidateJobId, (cur) => ({
        ...cur,
        status: "FAILED",
        remoteDeletionState: "NOT_UPLOADED",
        views: Object.fromEntries(
          cur.requestedViews.map((v) => [
            v,
            {
              ...cur.views[v]!,
              state: "FAILED",
              error: {
                code: "BUDGET_EXCEEDED",
                message: "Đã chạm trần ngân sách thử đồ cho phép.",
                requestId: rawReqId,
                retryable: false,
              },
            },
          ]),
        ),
      }));
      return makeTypedError(
        "BUDGET_EXCEEDED",
        "Đã vượt trần ngân sách thử đồ được phê duyệt.",
        rawReqId,
      );
    }

    if (provider.source === "AI_LIVE" && resolved.liveCostGuard && store.reserveLiveSpendAtomically) {
      const totalEstimatedUsd = Number(
        (resolved.liveCostGuard.estimatedPerCallUsd * requestedViews.length).toFixed(6),
      );
      const spendCheck = await store.reserveLiveSpendAtomically({
        requestUnits: requestedViews.length,
        maxRequests: resolved.liveCostGuard.maxRequests,
        estimatedUsd: totalEstimatedUsd,
        maxEstimatedUsd: resolved.liveCostGuard.maxEstimatedUsd,
      });
      if (!spendCheck.allowed) {
        const providerLabel = provider.providerId === "fosta" ? "FOSTA LIVE" : "OpenAI LIVE";
        const denyMsg =
          spendCheck.reason === "MAX_REQUESTS"
            ? `Đã chạm giới hạn số lượt gọi ${providerLabel} cho phép (TRYON_LIVE_MAX_REQUESTS=${resolved.liveCostGuard.maxRequests}).`
            : `Ước tính chi phí lượt này ($${totalEstimatedUsd.toFixed(4)}) vượt trần ngân sách được duyệt (TRYON_LIVE_MAX_ESTIMATED_USD=$${resolved.liveCostGuard.maxEstimatedUsd.toFixed(4)}).`;
        await store.updateJob(candidateJobId, (cur) => ({
          ...cur,
          status: "FAILED",
          remoteDeletionState: "NOT_UPLOADED",
          views: Object.fromEntries(
            cur.requestedViews.map((v) => [
              v,
              {
                ...cur.views[v]!,
                state: "FAILED",
                error: {
                  code: "BUDGET_EXCEEDED",
                  message: denyMsg,
                  requestId: rawReqId,
                  retryable: false,
                },
              },
            ]),
          ),
        }));
        return makeTypedError("BUDGET_EXCEEDED", denyMsg, rawReqId);
      }
    }

    const ttlMs = Number(env.VTO_OUTPUT_TTL_MS ?? DEFAULT_OUTPUT_TTL_MS);
    let totalEstimatedCostUsd: number | undefined;
    let totalInputTokens: number | undefined;
    let totalOutputTokens: number | undefined;

    const dispatchPromise = (async (): Promise<StoredTryOnJobRecord> => {
      const updatedViews: Partial<Record<ImageView, StoredChildViewJob>> = {
        ...candidateRecord.views,
      };
      let remoteDeletionState: RemoteDeletionState =
        provider.source === "FAKE" ? "NOT_UPLOADED" : "UNKNOWN";
      let hasAmbiguousTimeout = false;

      for (const view of requestedViews) {
        const garmentsForView = selectedItemIds.map((itemId) => {
          const itemSummary = outfitItemsById.get(itemId)!;
          return {
            itemId,
            category: itemSummary.category as Category,
            colors: itemSummary.colors,
            image: validatedGarments[itemId]![view]!,
          };
        });
        try {
          const submitted = await provider.submitView({
            jobId: candidateJobId,
            requestId: rawReqId,
            view,
            presentationMode,
            personImage: validatedPerson[view]!,
            garments: garmentsForView,
          });
          remoteDeletionState = submitted.remoteDeletionState;
          if (submitted.usageReport) {
            if (submitted.usageReport.estimatedCostUsd !== null) {
              totalEstimatedCostUsd = Number(
                ((totalEstimatedCostUsd ?? 0) + submitted.usageReport.estimatedCostUsd).toFixed(6),
              );
            }
            if (typeof submitted.usageReport.inputTokens === "number") {
              totalInputTokens = (totalInputTokens ?? 0) + submitted.usageReport.inputTokens;
            }
            if (typeof submitted.usageReport.outputTokens === "number") {
              totalOutputTokens = (totalOutputTokens ?? 0) + submitted.usageReport.outputTokens;
            }
          }
          updatedViews[view] = {
            view,
            state: submitted.initialState,
            upstreamPredictionId: submitted.upstreamPredictionId,
            output: submitted.immediateOutput ?? null,
            error: null,
            qualityReview: "UNREVIEWED",
          };
        } catch (err) {
          if (err instanceof UpstreamAmbiguousTimeoutError) {
            hasAmbiguousTimeout = true;
            if (provider.source === "AI_LIVE") {
              remoteDeletionState =
                provider.providerId === "openai" || provider.providerId === "fosta"
                  ? "UNSUPPORTED"
                  : "BASE64_EPHEMERAL_60M";
            }
            updatedViews[view] = {
              view,
              state: "FAILED",
              upstreamPredictionId: null,
              output: null,
              error: {
                code: "UPSTREAM_TIMEOUT",
                message: err.message,
                requestId: rawReqId,
                retryable: false,
              },
              qualityReview: "UNREVIEWED",
            };
            // Stop submitting further views when upstream state is ambiguous
            break;
          }
          const code: TryOnErrorCode =
            err instanceof UpstreamDeterministicError ? err.code : "UPSTREAM_ERROR";
          const retryable =
            err instanceof UpstreamDeterministicError ? err.retryable : false;
          const message =
            err instanceof Error ? err.message : "Lỗi gửi yêu cầu tới dịch vụ thử đồ.";
          updatedViews[view] = {
            view,
            state: "FAILED",
            upstreamPredictionId: null,
            output: null,
            error: {
              code,
              message,
              requestId: rawReqId,
              retryable,
            },
            qualityReview: "UNREVIEWED",
          };
        }
      }

      const saved = await store.updateJob(candidateJobId, (cur) => {
        if (cur.status === "CANCELLED" || cur.status === "DELETED") {
          return cur;
        }
        const nextStatus: TryOnJobStatus = hasAmbiguousTimeout
          ? "UNKNOWN_SUBMISSION"
          : computeAggregateJobStatus(cur.requestedViews, updatedViews, "RUNNING");
        const anySucceeded = cur.requestedViews.some(
          (v) => updatedViews[v]?.state === "SUCCEEDED",
        );
        return {
          ...cur,
          views: updatedViews,
          status: nextStatus,
          remoteDeletionState,
          expiresAt: anySucceeded ? now() + ttlMs : cur.expiresAt,
        };
      });

      return saved ?? candidateRecord;
    })();

    inFlightSubmissions.set(candidateJobId, dispatchPromise);
    let finalRecord: StoredTryOnJobRecord;
    try {
      finalRecord = await dispatchPromise;
    } finally {
      inFlightSubmissions.delete(candidateJobId);
    }

    log({
      event:
        finalRecord.status === "UNKNOWN_SUBMISSION"
          ? "tryon.submit.ambiguous"
          : "tryon.submit.accepted",
      service: "tryon-v2",
      requestId: finalRecord.requestId,
      jobId: finalRecord.jobId,
      providerId: provider.providerId,
      modelId: resolved.modelId,
      mode: resolved.executionMode,
      status: finalRecord.status,
      estimatedCostUsd: totalEstimatedCostUsd,
      inputTokens: totalInputTokens,
      outputTokens: totalOutputTokens,
      latencyMs: now() - startedAt,
    });

    const submitResp: TryOnJobSubmitResponse = {
      jobId: finalRecord.jobId,
      requestId: finalRecord.requestId,
      status: finalRecord.status,
    };
    return jsonResponse(submitResp, 202);
  }

  async function handlePoll(
    jobId: string,
    env: TryOnEnvConfig,
    sessionId: string,
    startedAt: number,
  ): Promise<Response> {
    const record = await store.getJob(jobId);
    if (!record) {
      return makeTypedError("INVALID_REQUEST", "Không tìm thấy tiến trình thử đồ.", `poll_${jobId}`, false, 404);
    }
    if (record.sessionId !== sessionId) {
      return makeTypedError(
        "FORBIDDEN",
        "Bạn không có quyền truy cập tiến trình thử đồ của phiên khác.",
        record.requestId,
      );
    }

    const currentMs = now();
    const ttlMs = Number(env.VTO_OUTPUT_TTL_MS ?? DEFAULT_OUTPUT_TTL_MS);

    // Check output TTL expiry first
    if (
      record.status !== "DELETED" &&
      record.expiresAt !== null &&
      currentMs >= record.expiresAt
    ) {
      const expiredRecord = await store.updateJob(jobId, (cur) => {
        if (cur.status === "DELETED") return cur;
        const nextViews: Partial<Record<ImageView, StoredChildViewJob>> = {};
        for (const v of cur.requestedViews) {
          const child = cur.views[v];
          if (!child) continue;
          if (child.state === "SUCCEEDED") {
            nextViews[v] = {
              ...child,
              state: "EXPIRED",
              output: null,
              error: {
                code: "EXPIRED",
                message: "Ảnh kết quả tạm thời đã hết hạn lưu trữ.",
                requestId: cur.requestId,
                retryable: false,
              },
            };
          } else {
            nextViews[v] = child;
          }
        }
        return {
          ...cur,
          status: "FAILED",
          views: nextViews,
        };
      });
      return jsonResponse(toWireResult(expiredRecord ?? record), 200);
    }

    // Terminal states return without polling upstream
    if (
      record.status !== "QUEUED" &&
      record.status !== "RUNNING"
    ) {
      log({
        event: "tryon.poll",
        service: "tryon-v2",
        requestId: record.requestId,
        jobId: record.jobId,
        status: record.status,
        latencyMs: now() - startedAt,
      });
      return jsonResponse(toWireResult(record), 200);
    }

    // Enforce technical deadline (120s front / 180s dual)
    if (currentMs >= record.deadlineAt) {
      const timedOutRecord = await store.updateJob(jobId, (cur) => {
        if (cur.status === "CANCELLED" || cur.status === "DELETED") return cur;
        const nextViews: Partial<Record<ImageView, StoredChildViewJob>> = {};
        for (const v of cur.requestedViews) {
          const child = cur.views[v];
          if (!child) continue;
          if (child.state === "QUEUED" || child.state === "RUNNING") {
            nextViews[v] = {
              ...child,
              state: "FAILED",
              output: null,
              error: {
                code: "UPSTREAM_TIMEOUT",
                message: "Quá thời gian chờ xử lý kỹ thuật cho góc nhìn này.",
                requestId: cur.requestId,
                retryable: false,
              },
            };
          } else {
            nextViews[v] = child;
          }
        }
        return {
          ...cur,
          views: nextViews,
          status: computeAggregateJobStatus(cur.requestedViews, nextViews, "TIMED_OUT"),
        };
      });
      return jsonResponse(toWireResult(timedOutRecord ?? record), 200);
    }

    const resolved = resolveProvider(env);
    if (!resolved.ok) {
      return makeTypedError(resolved.code, resolved.message, record.requestId, false, resolved.status);
    }

    const polledUpdates = new Map<ImageView, StoredChildViewJob>();
    for (const view of record.requestedViews) {
      const child = record.views[view];
      if (!child || (child.state !== "QUEUED" && child.state !== "RUNNING")) {
        continue;
      }
      if (!child.upstreamPredictionId) {
        continue;
      }
      const polled = await resolved.provider.pollView(
        child.upstreamPredictionId,
        record.requestId,
      );
      if (polled.state === "SUCCEEDED" && polled.output) {
        polledUpdates.set(view, {
          ...child,
          state: "SUCCEEDED",
          output: polled.output,
          error: null,
          qualityReview: "UNREVIEWED",
        });
      } else if (polled.state === "FAILED") {
        polledUpdates.set(view, {
          ...child,
          state: "FAILED",
          output: null,
          error: {
            code: polled.errorCode ?? "UPSTREAM_ERROR",
            message: polled.errorMessage ?? "Xử lý góc nhìn thất bại.",
            requestId: record.requestId,
            retryable: polled.retryable ?? false,
          },
          qualityReview: "UNREVIEWED",
        });
      } else {
        polledUpdates.set(view, {
          ...child,
          state: polled.state,
        });
      }
    }

    const updated = await store.updateJob(jobId, (cur) => {
      // Late-result suppression: NEVER overwrite CANCELLED or DELETED jobs!
      if (cur.status === "CANCELLED" || cur.status === "DELETED") {
        return cur;
      }
      const mergedViews: Partial<Record<ImageView, StoredChildViewJob>> = {
        ...cur.views,
      };
      for (const [view, nextChild] of polledUpdates.entries()) {
        mergedViews[view] = nextChild;
      }
      const nextStatus = computeAggregateJobStatus(
        cur.requestedViews,
        mergedViews,
        cur.status,
      );
      const hasAnySucceeded = cur.requestedViews.some(
        (v) => mergedViews[v]?.state === "SUCCEEDED",
      );
      return {
        ...cur,
        views: mergedViews,
        status: nextStatus,
        expiresAt:
          hasAnySucceeded && cur.expiresAt === null
            ? now() + ttlMs
            : cur.expiresAt,
      };
    });

    const finalRec = updated ?? record;
    log({
      event: "tryon.poll",
      service: "tryon-v2",
      requestId: finalRec.requestId,
      jobId: finalRec.jobId,
      status: finalRec.status,
      latencyMs: now() - startedAt,
    });
    return jsonResponse(toWireResult(finalRec), 200);
  }

  async function handleCancel(
    jobId: string,
    env: TryOnEnvConfig,
    sessionId: string,
    startedAt: number,
  ): Promise<Response> {
    const existing = await store.getJob(jobId);
    if (!existing) {
      return makeTypedError("INVALID_REQUEST", "Không tìm thấy tiến trình thử đồ.", `cancel_${jobId}`, false, 404);
    }
    if (existing.sessionId !== sessionId) {
      return makeTypedError(
        "FORBIDDEN",
        "Bạn không có quyền hủy tiến trình thử đồ của phiên khác.",
        existing.requestId,
      );
    }

    if (existing.status === "DELETED") {
      return makeTypedError(
        "CONFLICT",
        "Tiến trình đã bị xóa trước đó.",
        existing.requestId,
      );
    }

    let upstreamAttempted = false;
    let upstreamConfirmed = false;
    const resolved = resolveProvider(env);

    if (
      resolved.ok &&
      (existing.status === "QUEUED" || existing.status === "RUNNING")
    ) {
      for (const view of existing.requestedViews) {
        const predId = existing.views[view]?.upstreamPredictionId;
        if (predId) {
          const res = await resolved.provider.cancelView(predId);
          upstreamAttempted = upstreamAttempted || res.attempted;
          upstreamConfirmed = upstreamConfirmed || res.confirmed;
        }
      }
    }

    const cancelledAt = now();
    const updated = await store.updateJob(jobId, (cur) => {
      if (cur.status === "DELETED") return cur;
      if (cur.status === "SUCCEEDED" || cur.status === "PARTIAL" || cur.status === "FAILED") {
        return cur;
      }
      const nextViews: Partial<Record<ImageView, StoredChildViewJob>> = {};
      for (const v of cur.requestedViews) {
        const child = cur.views[v];
        if (!child) continue;
        if (child.state === "QUEUED" || child.state === "RUNNING") {
          nextViews[v] = {
            ...child,
            state: "CANCELLED",
            output: null,
            error: {
              code: "CANCELLED",
              message: "Người dùng đã hủy yêu cầu thử đồ.",
              requestId: cur.requestId,
              retryable: false,
            },
          };
        } else {
          nextViews[v] = child;
        }
      }
      return {
        ...cur,
        status: "CANCELLED",
        cancelledAt,
        views: nextViews,
      };
    });

    const finalRec = updated ?? existing;
    log({
      event: "tryon.cancel",
      service: "tryon-v2",
      requestId: finalRec.requestId,
      jobId: finalRec.jobId,
      status: finalRec.status,
      latencyMs: now() - startedAt,
    });

    const receipt: CancellationReceiptWire = {
      jobId: finalRec.jobId,
      status: finalRec.status,
      cancelledAt,
      upstreamCancelAttempted: upstreamAttempted,
      upstreamCancelConfirmed: upstreamConfirmed,
      refundGuaranteed: false,
      message:
        "Đã ghi nhận hủy trên ứng dụng và gửi yêu cầu dừng best-effort tới nhà cung cấp; không cam kết hoàn lại hạn mức nếu mô hình đã bắt đầu xử lý.",
    };
    return jsonResponse(receipt, 200);
  }

  async function handleDelete(
    jobId: string,
    env: TryOnEnvConfig,
    sessionId: string,
    startedAt: number,
  ): Promise<Response> {
    const existing = await store.getJob(jobId);
    if (!existing) {
      return makeTypedError("INVALID_REQUEST", "Không tìm thấy tiến trình thử đồ.", `del_${jobId}`, false, 404);
    }
    if (existing.sessionId !== sessionId) {
      return makeTypedError(
        "FORBIDDEN",
        "Bạn không có quyền xóa tiến trình thử đồ của phiên khác.",
        existing.requestId,
      );
    }

    // Best-effort cancel if still running so upstream stops early
    const resolved = resolveProvider(env);
    if (
      resolved.ok &&
      (existing.status === "QUEUED" || existing.status === "RUNNING")
    ) {
      for (const view of existing.requestedViews) {
        const predId = existing.views[view]?.upstreamPredictionId;
        if (predId) {
          await resolved.provider.cancelView(predId);
        }
      }
    }

    const deletedAt = now();
    const updated = await store.updateJob(jobId, (cur) => {
      const wipedViews: Partial<Record<ImageView, StoredChildViewJob>> = {};
      for (const v of cur.requestedViews) {
        const child = cur.views[v];
        if (!child) continue;
        wipedViews[v] = {
          ...child,
          state: child.state === "SUCCEEDED" ? "EXPIRED" : "CANCELLED",
          output: null,
        };
      }
      return {
        ...cur,
        status: "DELETED",
        deletedAt,
        expiresAt: deletedAt,
        views: wipedViews,
      };
    });

    const finalRec = updated ?? existing;
    log({
      event: "tryon.delete",
      service: "tryon-v2",
      requestId: finalRec.requestId,
      jobId: finalRec.jobId,
      status: "DELETED",
      latencyMs: now() - startedAt,
    });

    const fostaUnsupportedMsg =
      "Đã xóa ngay kết quả cục bộ và bản lưu trên máy chủ FitCheck. Nhà cung cấp thử nghiệm FOSTA (codex.fosta.app) là dịch vụ proxy bên thứ ba không có API xóa từ xa từng request và chưa xác minh thời hạn lưu giữ dữ liệu.";
    const remoteMessageByState: Record<RemoteDeletionState, string> = {
      NOT_UPLOADED: "Ảnh chưa từng được tải lên nhà cung cấp bên thứ ba; dữ liệu cục bộ và máy chủ đã được xóa sạch.",
      BASE64_EPHEMERAL_60M:
        "Đã xóa ngay kết quả trên proxy/máy khách. Ở phía nhà cung cấp (chế độ return_base64), không có API xóa tức thời nhưng dữ liệu không lưu trên CDN 72 giờ và tự động hết hạn sau tối đa 60 phút.",
      EXPIRES_AUTOMATICALLY_72H:
        "Đã xóa kết quả trên proxy/máy khách; bản lưu tạm trên CDN nhà cung cấp tự động hết hạn sau tối đa 72 giờ.",
      PENDING_BEST_EFFORT: "Đã xóa cục bộ và đang chờ nhà cung cấp xử lý yêu cầu xóa.",
      UNSUPPORTED:
        finalRec.providerId === "fosta"
          ? fostaUnsupportedMsg
          : "Đã xóa ngay kết quả cục bộ và bản lưu trên máy chủ FitCheck. Nhà cung cấp OpenAI Images API trả b64_json nội tuyến (không tạo URL công khai) nhưng không cung cấp API xóa tức thời từng request; việc lưu giữ nhật ký tuân theo chính sách Data Retention của OpenAI API Organization.",
      UNKNOWN: "Đã xóa cục bộ và trên máy chủ FitCheck; trạng thái lưu giữ phía nhà cung cấp chưa xác định.",
    };

    const receipt: DeletionReceiptWire = {
      jobId: finalRec.jobId,
      status: "DELETED",
      deletedAt,
      localOutputsDeleted: true,
      lateCompletionSuppressed: true,
      remoteDeletionState: finalRec.remoteDeletionState,
      message: remoteMessageByState[finalRec.remoteDeletionState],
    };
    return jsonResponse(receipt, 200);
  }

  function handleCapabilities(env: TryOnEnvConfig): Response {
    const resolved = resolveProvider(env);
    if (!resolved.ok) {
      const requestedProvider = env.TRYON_PROVIDER?.trim().toLowerCase() || "openai";
      // Return a structured locked/unavailable capability payload so Android UI displays honest gate state
      return jsonResponse({
        decision: env.VTO_FEASIBILITY_DECISION ?? "UNKNOWN",
        supportedViews: [],
        supportedCategories:
          requestedProvider === "fosta"
            ? ["TOP", "BOTTOM"]
            : ["TOP", "BOTTOM", "DRESS", "OUTERWEAR"],
        supportedPresentationModes:
          requestedProvider === "fosta"
            ? ["ORIGINAL_PHOTO", "STUDIO_WHITE"]
            : ["ORIGINAL_PHOTO"],
        maxGarmentsPerJob: 0,
        supportsNativePairedViews: false,
        runtimeVerifiedViews: [],
        providerId: requestedProvider,
        providerConfigVersion: env.VTO_PROVIDER_CONFIG_VERSION ?? "v2-poc-1",
        executionMode: "LOCKED",
        lockedReason: resolved.message,
        ...(requestedProvider === "fosta"
          ? { experimentalCapabilities: FOSTA_EXPERIMENTAL_CAPABILITIES }
          : {}),
      });
    }

    const supportedViews: ImageView[] =
      resolved.decision === "GO_DUAL" ? ["FRONT", "BACK"] : ["FRONT"];
    const supportedPresentationModes: TryOnPresentationMode[] =
      resolved.provider.providerId === "fosta" || resolved.provider.providerId.startsWith("fake")
        ? ["ORIGINAL_PHOTO", "STUDIO_WHITE"]
        : ["ORIGINAL_PHOTO"];

    return jsonResponse({
      decision: resolved.decision,
      supportedViews,
      supportedCategories: Array.from(resolved.provider.supportedCategories),
      supportedPresentationModes,
      maxGarmentsPerJob: resolved.provider.maxGarmentsPerJob,
      supportsNativePairedViews: false,
      runtimeVerifiedViews: resolved.executionMode === "AI_LIVE" ? [] : supportedViews,
      providerId: resolved.provider.providerId,
      providerConfigVersion: resolved.configVersion,
      executionMode: resolved.executionMode,
      modelId: resolved.modelId ?? null,
      ...(resolved.provider.providerId === "fosta"
        ? { experimentalCapabilities: FOSTA_EXPERIMENTAL_CAPABILITIES }
        : {}),
    });
  }

  async function handlePreflight(env: TryOnEnvConfig): Promise<Response> {
    // Zero-cost, zero-network Section 15 Preflight check — NEVER exposes secret values!
    const selectedProvider = env.TRYON_PROVIDER?.trim().toLowerCase() || "openai";
    const liveEnabled = env.TRYON_LIVE_ENABLED === "true";

    // Zero-unit read via existing store lock returns current usedLiveRequests without mutating the ledger.
    const spendSnapshot = store.reserveLiveSpendAtomically
      ? await store.reserveLiveSpendAtomically({
          requestUnits: 0,
          maxRequests: Number.MAX_SAFE_INTEGER,
          estimatedUsd: 0,
          maxEstimatedUsd: Number.MAX_SAFE_INTEGER,
        })
      : { allowed: true, usedRequests: 0, usedEstimatedUsd: 0 };
    const usedLiveRequests = spendSnapshot.usedRequests;

    if (selectedProvider === "fosta") {
      const offlineMockEnabled = env.TRYON_OFFLINE_MOCK_FOSTA === "true";
      const apiKeyConfigured = Boolean(env.FOSTA_API_KEY && env.FOSTA_API_KEY.trim().length > 0);
      const quotaApproved = env.FOSTA_APPROVE_QUOTA === "true";
      const modelId = env.FOSTA_MODEL?.trim() || DEFAULT_FOSTA_MODEL;
      const timeoutMs = Number(env.FOSTA_TIMEOUT_MS ?? String(DEFAULT_FOSTA_TIMEOUT_MS));
      const maxLiveRequests = Number(env.TRYON_LIVE_MAX_REQUESTS ?? "1");
      const validMaxRequests =
        Number.isInteger(maxLiveRequests) && maxLiveRequests >= 1 ? maxLiveRequests : 0;
      const remainingLiveRequests = Math.max(0, validMaxRequests - usedLiveRequests);
      const quotaExhausted = validMaxRequests > 0 ? usedLiveRequests >= validMaxRequests : true;
      const decision = env.VTO_FEASIBILITY_DECISION ?? "GO_DUAL";

      const blockers: string[] = [];
      if (env.VTO_KILL_SWITCH === "true" || env.VTO_MODE === "DISABLED") {
        blockers.push("KILL_SWITCH_ACTIVE: VTO_KILL_SWITCH hoặc VTO_MODE=DISABLED đang bật.");
      }
      if (!apiKeyConfigured) {
        blockers.push("MISSING_FOSTA_API_KEY: Chưa cấu hình FOSTA_API_KEY trong biến môi trường máy chủ.");
      }
      if (!quotaApproved) {
        blockers.push("QUOTA_NOT_APPROVED: Chưa đặt FOSTA_APPROVE_QUOTA=true để xác nhận dùng quota FOSTA.");
      }
      if (!Number.isInteger(maxLiveRequests) || maxLiveRequests < 1) {
        blockers.push("INVALID_MAX_REQUESTS: TRYON_LIVE_MAX_REQUESTS phải là số nguyên dương (mặc định 1).");
      }
      if (!Number.isInteger(timeoutMs) || timeoutMs < 1_000 || timeoutMs > 300_000) {
        blockers.push("INVALID_FOSTA_TIMEOUT: FOSTA_TIMEOUT_MS phải nằm trong 1000..300000 ms.");
      }
      if (!store.isDurable) {
        blockers.push("NON_DURABLE_STORE: Kho lưu trữ hiện tại chưa bền vững (isDurable=false).");
      }
      if (!liveEnabled) {
        blockers.push("LIVE_GATE_LOCKED: TRYON_LIVE_ENABLED đang là false (mặc định an toàn).");
      }
      if (offlineMockEnabled && liveEnabled) {
        blockers.push("CONFLICTING_MOCK_AND_LIVE_FLAGS: Không bật TRYON_OFFLINE_MOCK_FOSTA=true cùng lúc với LIVE.");
      }

      return jsonResponse({
        provider: "fosta",
        liveEnabled,
        offlineMockEnabled,
        readyForLivePocCall: blockers.length === 0,
        usedLiveRequests,
        maxRequests: maxLiveRequests,
        remainingLiveRequests,
        quotaExhausted,
        checks: {
          apiKeyConfigured,
          modelId,
          endpoint: env.FOSTA_ENDPOINT?.trim() || "https://codex.fosta.app/v1/responses",
          quotaApproved,
          maxLiveRequests,
          usedLiveRequests,
          remainingLiveRequests,
          quotaExhausted,
          timeoutMs,
          feasibilityDecision: decision,
          durableStoreActive: store.isDurable,
          verifiedProviderIdentity: false,
          experimentalCapabilities: FOSTA_EXPERIMENTAL_CAPABILITIES,
        },
        blockers,
      });
    }
    const offlineMockEnabled = env.TRYON_OFFLINE_MOCK_OPENAI === "true";
    const apiKeyConfigured = Boolean(env.OPENAI_API_KEY && env.OPENAI_API_KEY.trim().length > 0);
    const budgetConfirmed = env.TRYON_BUDGET_CONFIRMED === "true";
    const allowCustomModel = env.OPENAI_ALLOW_CUSTOM_MODEL === "true";
    const modelCheck = validateOpenAiModelSelection(env.OPENAI_IMAGE_MODEL, allowCustomModel);
    const quality = (env.OPENAI_IMAGE_QUALITY?.trim().toLowerCase() || "medium") as OpenAiQualityTier;
    const estUsd = modelCheck.ok
      ? estimateOpenAiEditMaxUsd({ model: modelCheck.modelId, quality })
      : null;
    const maxLiveRequests = Number(env.TRYON_LIVE_MAX_REQUESTS ?? "1");
    const validMaxRequests =
      Number.isInteger(maxLiveRequests) && maxLiveRequests >= 1 ? maxLiveRequests : 0;
    const remainingLiveRequests = Math.max(0, validMaxRequests - usedLiveRequests);
    const quotaExhausted = validMaxRequests > 0 ? usedLiveRequests >= validMaxRequests : true;
    const maxEstimatedUsd = Number(env.TRYON_LIVE_MAX_ESTIMATED_USD ?? "0");
    const decision = env.VTO_FEASIBILITY_DECISION ?? "GO_FRONT_ONLY";

    const blockers: string[] = [];
    if (env.VTO_KILL_SWITCH === "true" || env.VTO_MODE === "DISABLED") {
      blockers.push("KILL_SWITCH_ACTIVE: VTO_KILL_SWITCH hoặc VTO_MODE=DISABLED đang bật.");
    }
    if (!apiKeyConfigured) {
      blockers.push("MISSING_OPENAI_API_KEY: Chưa cấu hình OPENAI_API_KEY trong biến môi trường máy chủ.");
    }
    if (!modelCheck.ok) {
      blockers.push(`MODEL_NOT_READY: ${modelCheck.message}`);
    }
    if (!budgetConfirmed) {
      blockers.push("BUDGET_NOT_CONFIRMED: Chưa đặt TRYON_BUDGET_CONFIRMED=true sau khi xác minh API credits.");
    }
    if (!Number.isFinite(maxEstimatedUsd) || maxEstimatedUsd <= 0) {
      blockers.push("MISSING_USD_BUDGET_CAP: Cần đặt TRYON_LIVE_MAX_ESTIMATED_USD > 0.");
    }
    if (estUsd !== null && Number.isFinite(maxEstimatedUsd) && maxEstimatedUsd > 0 && estUsd > maxEstimatedUsd) {
      blockers.push(
        `INSUFFICIENT_LOCAL_BUDGET_CAP: Ước tính tối đa 1 lượt ($${estUsd.toFixed(3)}) cao hơn TRYON_LIVE_MAX_ESTIMATED_USD ($${maxEstimatedUsd.toFixed(3)}).`,
      );
    }
    if (estUsd === null && modelCheck.ok && env.TRYON_ALLOW_UNPRICED_MODEL !== "true") {
      blockers.push("UNPRICED_CUSTOM_MODEL: Model tùy chỉnh chưa có bảng giá; cần TRYON_ALLOW_UNPRICED_MODEL=true.");
    }
    if (!store.isDurable) {
      blockers.push("NON_DURABLE_STORE: Kho lưu trữ hiện tại chưa bền vững (isDurable=false).");
    }
    if (!liveEnabled) {
      blockers.push("LIVE_GATE_LOCKED: TRYON_LIVE_ENABLED đang là false (mặc định an toàn).");
    }
    if (offlineMockEnabled && liveEnabled) {
      blockers.push("CONFLICTING_MOCK_AND_LIVE_FLAGS: Không bật TRYON_OFFLINE_MOCK_OPENAI=true cùng lúc với LIVE.");
    }

    return jsonResponse({
      provider: env.TRYON_PROVIDER?.trim().toLowerCase() || "openai",
      liveEnabled,
      offlineMockEnabled,
      readyForLivePocCall: blockers.length === 0,
      usedLiveRequests,
      maxRequests: maxLiveRequests,
      remainingLiveRequests,
      quotaExhausted,
      checks: {
        apiKeyConfigured,
        modelId: modelCheck.ok ? modelCheck.modelId : (env.OPENAI_IMAGE_MODEL ?? null),
        modelSupported: modelCheck.ok,
        organizationVerificationReminder:
          "Một số tài khoản OpenAI yêu cầu hoàn tất API Organization Verification trong Settings -> Organization trước khi gọi các model GPT Image.",
        budgetConfirmed,
        maxLiveRequests,
        usedLiveRequests,
        remainingLiveRequests,
        quotaExhausted,
        maxEstimatedUsd,
        estimatedMaxUsdPerFrontRequest: estUsd,
        feasibilityDecision: decision,
        durableStoreActive: store.isDurable,
      },
      blockers,
    });
  }

  return {
    async handle(
      request: Request,
      env: TryOnEnvConfig,
      context?: TryOnRouteContext,
    ): Promise<Response> {
      const startedAt = now();
      const { pathname } = new URL(request.url);
      const sessionId =
        context?.sessionId?.trim() ||
        request.headers.get("x-session-id")?.trim() ||
        null;

      if (!sessionId) {
        return makeTypedError(
          "UNAUTHORIZED",
          "Thiếu thông tin xác thực phiên làm việc hợp lệ.",
          "req_unauth",
        );
      }

      if (request.method === "GET" && pathname === "/v2/tryon/capabilities") {
        return handleCapabilities(env);
      }

      if (request.method === "GET" && pathname === "/v2/tryon/preflight") {
        return handlePreflight(env);
      }

      if (request.method === "POST" && pathname === "/v2/tryon/jobs") {
        return handleSubmit(request, env, sessionId, startedAt);
      }

      const cancelMatch = /^\/v2\/tryon\/jobs\/([^/]+)\/cancel$/.exec(pathname);
      if (request.method === "POST" && cancelMatch && cancelMatch[1]) {
        return handleCancel(decodeURIComponent(cancelMatch[1]), env, sessionId, startedAt);
      }

      const itemMatch = /^\/v2\/tryon\/jobs\/([^/]+)$/.exec(pathname);
      if (itemMatch && itemMatch[1]) {
        const jobId = decodeURIComponent(itemMatch[1]);
        if (request.method === "GET") {
          return handlePoll(jobId, env, sessionId, startedAt);
        }
        if (request.method === "DELETE") {
          return handleDelete(jobId, env, sessionId, startedAt);
        }
      }

      return makeTypedError(
        "INVALID_REQUEST",
        "Đường dẫn hoặc phương thức HTTP không được hỗ trợ cho /v2/tryon/jobs.",
        "req_bad_route",
        false,
        404,
      );
    },
  };
}
