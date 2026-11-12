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
  OPENAI_API_KEY?: string;
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
  /** Request/phút mỗi session (best-effort theo isolate). */
  RATE_LIMIT_PER_MINUTE?: string;
}

export type AiProvider = "anthropic" | "gemini" | "openai";

export const DEFAULT_MODELS: Record<AiProvider, string> = {
  anthropic: "claude-opus-5-5",
  // 11/11/2026: gemini-3.8-flash free tier quá tải (timeout/503); flash-lite trả lời ~1–3s.
  gemini: "gemini-3.5-flash-lite",
  // 12/11/2026 qua FOSTA: gpt-6.1-sol 8–21s/call (vision + tool strict); xem docs/track-b/PRE-003-provider-budget.md.
  openai: "gpt-6.1-sol",
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
