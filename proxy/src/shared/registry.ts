// OWNER: Track B. Seam đăng ký route: B giữ entry/auth/limits; track khác (C — tryon) export RouteDef
// từ module của mình (vd. src/tryon/routes.ts) và B mount vào danh sách route trong src/index.ts.
import type { AiService, Env, ServiceName } from "../env";
import type { Session } from "./auth";
import type { UsageGuard } from "./guard";
import type { LogEvent } from "./log";
import type { ModelClient } from "./model";

export type HttpMethod = "GET" | "POST" | "DELETE";

/** Trần deadline riêng của route; job dài hơn phải trả 202 + poll thay vì giữ request mở. */
export const MAX_ROUTE_DEADLINE_MS = 300_000;

export interface AppDeps {
  guard: UsageGuard;
  /** null khi chưa cấu hình provider (thiếu key) ⇒ route trả 503, không gọi upstream. */
  /** [service] chọn provider/model riêng (AI_PROVIDER_<SERVICE>); bỏ trống = cấu hình chung. */
  modelFor(env: Env, service?: AiService): ModelClient | null;
  now(): number;
  uuid(): string;
  log(event: LogEvent): void;
}

export interface RouteContext {
  env: Env;
  deps: AppDeps;
  requestId: string;
  session: Session;
  params: Record<string, string>;
  /** Deadline tổng của request: `RouteDef.deadlineMs` nếu route khai báo, không thì proxyDeadlineMs(env) (mặc định 18s). */
  signal: AbortSignal;
  /**
   * Giữ chỗ một call upstream trong daily cap của service. Gọi SAU validate, NGAY TRƯỚC khi gọi provider;
   * ném BUDGET_EXCEEDED nếu hết cap.
   */
  reserveUpstreamCall(): void;
  /** Ghi thêm số liệu kỹ thuật (không PII) vào log của request. */
  annotate(fields: Partial<LogEvent>): void;
}

export interface RouteDef {
  method: HttpMethod;
  /** Path tĩnh hoặc có tham số dạng `/v2/tryon/jobs/:jobId`. */
  path: string;
  service: ServiceName;
  /** Giới hạn byte của body (chỉ áp dụng khi có body). */
  maxBodyBytes: number;
  /**
   * Deadline riêng của route (ms), vd. try-on 120s/180s (CT-07). Bỏ trống = deadline chung (recognition/outfits 18s).
   * Do B duyệt khi mount; bị kẹp trong [1s, MAX_ROUTE_DEADLINE_MS].
   */
  deadlineMs?: number;
  handle(body: unknown, ctx: RouteContext): Promise<Response>;
}

export function matchPath(pattern: string, pathname: string): Record<string, string> | null {
  const patternParts = pattern.split("/");
  const pathParts = pathname.split("/");
  if (patternParts.length !== pathParts.length) return null;
  const params: Record<string, string> = {};
  for (let i = 0; i < patternParts.length; i++) {
    const p = patternParts[i] as string;
    const v = pathParts[i] as string;
    if (p.startsWith(":")) {
      if (!/^[A-Za-z0-9_-]{1,64}$/.test(v)) return null;
      params[p.slice(1)] = v;
    } else if (p !== v) {
      return null;
    }
  }
  return params;
}
