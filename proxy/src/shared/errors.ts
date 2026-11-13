// OWNER: Track B. Error envelope CT-07: {error:{code,message,requestId,retryable}}.

/** Mã lỗi server có thể trả. NETWORK/IO/CANCELLED là lỗi local của Android, server không phát. */
export type ErrorCode =
  | "INVALID_REQUEST"
  | "CONSENT_REQUIRED"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "PAYLOAD_TOO_LARGE"
  | "RATE_LIMITED"
  | "BUDGET_EXCEEDED"
  | "STALE_INPUT"
  | "CONFLICT"
  | "NEEDS_INPUT"
  | "UNSUPPORTED_CAPABILITY"
  | "INSUFFICIENT_WARDROBE"
  | "INVALID_AI_OUTPUT"
  | "UPSTREAM_TIMEOUT"
  | "UPSTREAM_ERROR"
  | "EXPIRED";

const DEFAULT_STATUS: Record<ErrorCode, number> = {
  INVALID_REQUEST: 400,
  CONSENT_REQUIRED: 403,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  PAYLOAD_TOO_LARGE: 413,
  RATE_LIMITED: 429,
  BUDGET_EXCEEDED: 503,
  STALE_INPUT: 409,
  CONFLICT: 409,
  NEEDS_INPUT: 409,
  UNSUPPORTED_CAPABILITY: 400,
  INSUFFICIENT_WARDROBE: 409,
  INVALID_AI_OUTPUT: 502,
  UPSTREAM_TIMEOUT: 504,
  UPSTREAM_ERROR: 502,
  EXPIRED: 409,
};

const DEFAULT_RETRYABLE: Partial<Record<ErrorCode, boolean>> = {
  RATE_LIMITED: true,
  UPSTREAM_TIMEOUT: true,
  UPSTREAM_ERROR: true,
};

export interface ErrorOptions {
  status?: number;
  retryable?: boolean;
}

/** Lỗi có kiểu, ném trong handler và được app chuyển thành envelope. Message không chứa dữ liệu người dùng. */
export class ApiError extends Error {
  readonly status: number;
  readonly retryable: boolean;

  constructor(
    readonly code: ErrorCode,
    message: string,
    options: ErrorOptions = {},
  ) {
    super(message);
    this.status = options.status ?? DEFAULT_STATUS[code];
    this.retryable = options.retryable ?? DEFAULT_RETRYABLE[code] ?? false;
  }
}

export function errorResponse(error: ApiError, requestId: string): Response {
  return new Response(
    JSON.stringify({
      error: { code: error.code, message: error.message, requestId, retryable: error.retryable },
    }),
    {
      status: error.status,
      headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
    },
  );
}
