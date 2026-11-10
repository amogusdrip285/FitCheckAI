// OWNER: Track B. Contract: docs/contracts/proxy-api.md

export interface Env {
  APP_VERSION: string;
  ANTHROPIC_API_KEY?: string;
}

export type ErrorCode =
  | "BAD_REQUEST"
  | "MISSING_DEVICE_ID"
  | "NOT_FOUND"
  | "RATE_LIMITED"
  | "BUDGET_EXCEEDED"
  | "AI_INVALID_OUTPUT"
  | "UPSTREAM_ERROR"
  | "NOT_IMPLEMENTED";

const STATUS: Record<ErrorCode, number> = {
  BAD_REQUEST: 400,
  MISSING_DEVICE_ID: 400,
  NOT_FOUND: 404,
  RATE_LIMITED: 429,
  BUDGET_EXCEEDED: 503,
  AI_INVALID_OUTPUT: 502,
  UPSTREAM_ERROR: 502,
  NOT_IMPLEMENTED: 501,
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

export function apiError(code: ErrorCode, message: string): Response {
  return json({ error: { code, message } }, STATUS[code]);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);

    if (request.method === "GET" && pathname === "/health") {
      return json({ status: "ok", version: env.APP_VERSION });
    }

    if (request.method === "POST" && pathname.startsWith("/v1/")) {
      if (!request.headers.get("x-device-id")) {
        return apiError("MISSING_DEVICE_ID", "Thiếu header X-Device-Id");
      }
      if (pathname === "/v1/tag" || pathname === "/v1/outfits") {
        // TODO(Track B): validate input → gọi Claude → validate output.
        return apiError("NOT_IMPLEMENTED", `${pathname} chưa được triển khai`);
      }
    }

    return apiError("NOT_FOUND", "Không tìm thấy endpoint");
  },
} satisfies ExportedHandler<Env>;
