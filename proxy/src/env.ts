// OWNER: Track B. Cấu hình proxy v2 — xem docs/contracts/proxy-api.md.

/**
 * Biến môi trường của Worker.
 * - `[vars]` trong wrangler.toml: cấu hình không bí mật (kill switch, cap, model).
 * - Secrets (`wrangler secret put` / `.dev.vars`): ANTHROPIC_API_KEY, SESSION_SECRET, DEMO_ACCESS_CODE.
 */
export interface Env {
  APP_VERSION: string;
  /**
   * "anthropic" | "gemini" | "openai". Mặc định "anthropic". Gemini free tier chỉ dùng để test (xem shared/gemini.ts).
   * "openai" = API Chat Completions chuẩn OpenAI, gồm cả gateway tương thích (vd. FOSTA) qua OPENAI_BASE_URL.
   */
  AI_PROVIDER?: string;
  /**
   * Ghi đè provider/model cho từng service (vd. outfits dùng GPT qua FOSTA, recognition dùng Gemini).
   * Trống ⇒ dùng AI_PROVIDER/AI_MODEL chung. AI_MODEL chung chỉ áp khi provider của service trùng provider chung.
   */
  AI_PROVIDER_RECOGNITION?: string;
  AI_PROVIDER_OUTFITS?: string;
  AI_MODEL_RECOGNITION?: string;
  AI_MODEL_OUTFITS?: string;
  /** Secret. Thiếu key của provider đang chọn ⇒ route gọi AI trả 503, không bao giờ nhúng vào APK. */
  ANTHROPIC_API_KEY?: string;
  GEMINI_API_KEY?: string;
  /** Key OpenAI CHÍNH CHỦ (api.openai.com) — dùng chung với Track C (`TRYON_PROVIDER=openai`). */
  OPENAI_API_KEY?: string;
  /**
   * Key cho gateway OpenAI-compatible bên thứ ba (OPENAI_BASE_URL khác api.openai.com, vd. FOSTA).
   * Tách riêng để key gateway không bao giờ bị gửi tới api.openai.com (C dùng OPENAI_API_KEY cho OpenAI thật) và ngược lại.
   */
  OPENAI_COMPAT_API_KEY?: string;
  /** Không bí mật. HTTPS; trống ⇒ https://api.openai.com/v1. Đổi gateway = dữ liệu đi tới bên thứ ba khác. */
  OPENAI_BASE_URL?: string;
  /** Model cho recognition/recommendation; mặc định theo provider. PRE-003 chốt lựa chọn. */
  AI_MODEL?: string;
  /** Secret HMAC ký session token. Thiếu ⇒ mọi route cần auth bị từ chối. */
  SESSION_SECRET?: string;
  /** Secret mã truy cập demo đổi lấy session token ngắn hạn (demo kín, không phải public beta). */
  DEMO_ACCESS_CODE?: string;
  SESSION_TTL_SECONDS?: string;
  /** Kill switch theo service: chỉ "on" mới bật. Mặc định tắt để không gọi provider ngoài ý muốn. */
  SERVICE_RECOGNITION?: string;
  SERVICE_OUTFITS?: string;
  SERVICE_TRYON?: string;
  /** Capability recognition: "DUAL" (front+back) hoặc "FRONT_ONLY". */
  RECOGNITION_VIEWS?: string;
  /** Số call upstream tối đa mỗi ngày (UTC) mỗi isolate — best-effort, KHÔNG phải hard cap tiền. */
  DAILY_CAP_RECOGNITION?: string;
  DAILY_CAP_OUTFITS?: string;
  /**
   * Deadline (ms). Mặc định CT-07: provider 15000, proxy 18000 — đổi mặc định là đổi contract với client.
   * Chỉ nới để TEST provider chậm (vd. gateway FOSTA ~20s); client Android vẫn cắt ở 20s.
   */
  PROVIDER_TIMEOUT_MS?: string;
  PROXY_DEADLINE_MS?: string;
  /** Request/phút mỗi session (best-effort theo isolate). */
  RATE_LIMIT_PER_MINUTE?: string;
}

export type AiProvider = "anthropic" | "gemini" | "openai";

export const DEFAULT_MODELS: Record<AiProvider, string> = {
  anthropic: "claude-opus-5-5",
  // 11/11/2026: gemini-3.8-flash free tier quá tải (timeout/503); flash-lite trả lời ~1–3s.
  gemini: "gemini-3.5-flash-lite",
  // 12/11/2026 qua FOSTA: luna ("việc nhỏ, rẻ nhất") nhanh ngang sol (gateway chiếm phần lớn độ trễ) nên chọn luna
  // để tiết kiệm; chất lượng giải thích chưa chấm. Xem docs/track-b/PRE-003-provider-budget.md.
  openai: "gpt-6-luna",
};

/** Service gọi AI (tryon do Track C tự quản provider). */
export type AiService = "recognition" | "outfits";
export const AI_SERVICES: readonly AiService[] = ["recognition", "outfits"];

function parseProvider(raw: string | undefined): AiProvider | null {
  const value = (raw ?? "").trim().toLowerCase();
  return value === "gemini" || value === "openai" || value === "anthropic" ? value : null;
}

/** Provider của [service] (ghi đè riêng nếu có), không thì provider chung (mặc định anthropic). */
export function aiProvider(env: Env, service?: AiService): AiProvider {
  const override =
    service === "recognition" ? env.AI_PROVIDER_RECOGNITION : service === "outfits" ? env.AI_PROVIDER_OUTFITS : undefined;
  return parseProvider(override) ?? parseProvider(env.AI_PROVIDER) ?? "anthropic";
}

/** Model của [service]: AI_MODEL_<SERVICE> → AI_MODEL (chỉ khi cùng provider chung) → mặc định của provider. */
export function aiModel(env: Env, service?: AiService): string {
  const provider = aiProvider(env, service);
  const own = (service === "recognition" ? env.AI_MODEL_RECOGNITION : service === "outfits" ? env.AI_MODEL_OUTFITS : undefined)?.trim();
  if (own) return own;
  const shared = env.AI_MODEL?.trim();
  if (shared && provider === aiProvider(env)) return shared;
  return DEFAULT_MODELS[provider];
}

/**
 * Nhãn provider cho /health và consent: với "openai" ghi kèm host thật để người dùng biết ảnh đi đâu
 * (vd. "openai@codex.fosta.app" — gateway bên thứ ba, không phải OpenAI trực tiếp).
 */
export const OPENAI_OFFICIAL_HOST = "api.openai.com";

/** Key đúng cho base URL đang dùng: chính chủ ⇒ OPENAI_API_KEY; gateway ⇒ OPENAI_COMPAT_API_KEY (không fallback chéo). */
export function openAiKeyFor(env: Env, baseUrl: string): string | undefined {
  let host = "";
  try {
    host = new URL(baseUrl).host;
  } catch {
    return undefined;
  }
  return host === OPENAI_OFFICIAL_HOST ? env.OPENAI_API_KEY : env.OPENAI_COMPAT_API_KEY;
}

export function providerLabel(env: Env, service?: AiService): string {
  const provider = aiProvider(env, service);
  if (provider !== "openai") return provider;
  try {
    return `openai@${new URL((env.OPENAI_BASE_URL ?? "").trim() || "https://api.openai.com/v1").host}`;
  } catch {
    return "openai@invalid";
  }
}

export type ServiceName = "recognition" | "outfits" | "tryon";

export const SERVICES: readonly ServiceName[] = ["recognition", "outfits", "tryon"];

export function serviceEnabled(env: Env, service: ServiceName): boolean {
  const raw =
    service === "recognition"
      ? env.SERVICE_RECOGNITION
      : service === "outfits"
        ? env.SERVICE_OUTFITS
        : env.SERVICE_TRYON;
  return (raw ?? "off").trim().toLowerCase() === "on";
}

export function intVar(raw: string | undefined, fallback: number, min: number, max: number): number {
  if (raw === undefined || raw.trim() === "") return fallback;
  const n = Number(raw);
  if (!Number.isInteger(n)) return fallback;
  return Math.min(max, Math.max(min, n));
}

export type RecognitionViews = "DUAL" | "FRONT_ONLY";

export function recognitionViews(env: Env): RecognitionViews {
  return (env.RECOGNITION_VIEWS ?? "DUAL").trim().toUpperCase() === "FRONT_ONLY" ? "FRONT_ONLY" : "DUAL";
}

export function dailyCap(env: Env, service: ServiceName): number {
  const raw = service === "recognition" ? env.DAILY_CAP_RECOGNITION : service === "outfits" ? env.DAILY_CAP_OUTFITS : undefined;
  return intVar(raw, 200, 0, 100_000);
}

export const DEFAULT_PROVIDER_TIMEOUT_MS = 15_000;
export const DEFAULT_PROXY_DEADLINE_MS = 18_000;

/** Timeout một call provider (1s–120s). */
export function providerTimeoutMs(env: Env): number {
  return intVar(env.PROVIDER_TIMEOUT_MS, DEFAULT_PROVIDER_TIMEOUT_MS, 1_000, 120_000);
}

/** Deadline tổng của request; luôn lớn hơn provider timeout ≥1s để kịp trả lỗi có kiểu thay vì bị cắt ngang. */
export function proxyDeadlineMs(env: Env): number {
  return Math.max(intVar(env.PROXY_DEADLINE_MS, DEFAULT_PROXY_DEADLINE_MS, 1_000, 125_000), providerTimeoutMs(env) + 1_000);
}
