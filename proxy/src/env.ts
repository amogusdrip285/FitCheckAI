// OWNER: Track B. Cấu hình proxy v2 — xem docs/contracts/proxy-api.md.

/**
 * Biến môi trường của Worker.
 * - `[vars]` trong wrangler.toml: cấu hình không bí mật (kill switch, cap, model).
 * - Secrets (`wrangler secret put` / `.dev.vars`): ANTHROPIC_API_KEY, SESSION_SECRET, DEMO_ACCESS_CODE.
 */
export interface Env {
  APP_VERSION: string;
  /** Secret. Thiếu ⇒ route gọi AI trả 503, không bao giờ nhúng vào APK. */
  ANTHROPIC_API_KEY?: string;
  /** Model cho recognition/recommendation; mặc định DEFAULT_AI_MODEL. PRE-003 chốt lựa chọn. */
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

export const DEFAULT_AI_MODEL = "claude-opus-5-5";

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
