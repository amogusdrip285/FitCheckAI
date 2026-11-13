// OWNER: Track B. Ranh giới provider: route chỉ biết ModelClient, không biết SDK cụ thể.
// Đổi provider (PRE-003) = viết adapter mới cùng interface; route/validator giữ nguyên.
import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import type { z } from "zod";
import type { ErrorCode } from "./errors";

export type ImageMediaType = "image/jpeg" | "image/png" | "image/webp";

export type ContentPart =
  | { type: "text"; text: string }
  | { type: "image"; mediaType: ImageMediaType; base64: string };

export interface StructuredCall<S extends z.ZodType> {
  system: string;
  content: ContentPart[];
  /** Schema output. Provider bị ràng buộc theo schema; route vẫn validate ngữ nghĩa sau đó. */
  schema: S;
  maxTokens: number;
  effort: "low" | "medium" | "high";
  signal: AbortSignal;
}

export interface ModelMeta {
  model: string;
  inputTokens: number;
  outputTokens: number;
}

export type ModelOutcome<T> =
  | { ok: true; data: T; meta: ModelMeta }
  | { ok: false; code: ErrorCode; retryable: boolean; reason: string };

export interface ModelClient {
  /** Nhãn source gắn vào kết quả. Adapter thật luôn là AI_LIVE; không có fake-as-live. */
  readonly source: "AI_LIVE";
  readonly model: string;
  generate<S extends z.ZodType>(call: StructuredCall<S>): Promise<ModelOutcome<z.infer<S>>>;
}

/** Provider timeout ≤15s (CT-07); proxy deadline tổng 18s nằm ở app. */
export const PROVIDER_TIMEOUT_MS = 15_000;

const FALLBACK_BETA = "server-side-fallback-2026-07-01";

function fail(code: ErrorCode, retryable: boolean, reason: string): { ok: false; code: ErrorCode; retryable: boolean; reason: string } {
  return { ok: false, code, retryable, reason };
}

/**
 * Adapter Anthropic Messages API (structured outputs qua zod).
 * - maxRetries 0: không tự retry call tốn tiền (CT-07).
 * - fallbacks "default": nếu model từ chối theo policy, API tự chạy lại trên model dự phòng cùng call.
 */
export class AnthropicModelClient implements ModelClient {
  readonly source = "AI_LIVE" as const;

  constructor(
    private readonly client: Anthropic,
    readonly model: string,
    private readonly timeoutMs: number = PROVIDER_TIMEOUT_MS,
  ) {}

  static fromApiKey(
    apiKey: string,
    model: string,
    fetchImpl?: typeof fetch,
    timeoutMs: number = PROVIDER_TIMEOUT_MS,
  ): AnthropicModelClient {
    const client = new Anthropic({
      apiKey,
      maxRetries: 0,
      timeout: timeoutMs,
      ...(fetchImpl ? { fetch: fetchImpl } : {}),
    });
    return new AnthropicModelClient(client, model, timeoutMs);
  }

  async generate<S extends z.ZodType>(call: StructuredCall<S>): Promise<ModelOutcome<z.infer<S>>> {
    const content: Anthropic.Beta.BetaContentBlockParam[] = call.content.map((part) =>
      part.type === "text"
        ? { type: "text", text: part.text }
        : { type: "image", source: { type: "base64", media_type: part.mediaType, data: part.base64 } },
    );

    let response: Anthropic.Beta.BetaMessage;
    try {
      // Dùng create (không phải parse) để kiểm stop_reason TRƯỚC khi parse: refusal/max_tokens
      // phải ra mã lỗi đúng thay vì lỗi parse chung chung.
      response = await this.client.beta.messages.create(
        {
          model: this.model,
          max_tokens: call.maxTokens,
          system: call.system,
          messages: [{ role: "user", content }],
          output_config: { effort: call.effort, format: betaZodOutputFormat(call.schema) },
          betas: [FALLBACK_BETA],
          fallbacks: "default",
        },
        { signal: call.signal, timeout: this.timeoutMs, maxRetries: 0 },
      );
    } catch (error) {
      return mapAnthropicError(error, call.signal);
    }

    if (response.stop_reason === "refusal") return fail("UPSTREAM_ERROR", false, "refusal");
    if (response.stop_reason === "max_tokens") return fail("INVALID_AI_OUTPUT", false, "max_tokens");

    const text = response.content
      .map((block) => (block.type === "text" ? block.text : ""))
      .join("");
    let raw: unknown;
    try {
      raw = JSON.parse(text);
    } catch {
      return fail("INVALID_AI_OUTPUT", false, "output_not_json");
    }
    const parsed = call.schema.safeParse(raw);
    if (!parsed.success) return fail("INVALID_AI_OUTPUT", false, "output_schema_mismatch");

    return {
      ok: true,
      data: parsed.data as z.infer<S>,
      meta: {
        model: response.model,
        inputTokens: response.usage.input_tokens,
        outputTokens: response.usage.output_tokens,
      },
    };
  }
}

function mapAnthropicError(error: unknown, signal: AbortSignal): { ok: false; code: ErrorCode; retryable: boolean; reason: string } {
  if (error instanceof Anthropic.APIConnectionTimeoutError) return fail("UPSTREAM_TIMEOUT", true, "timeout");
  if (error instanceof Anthropic.APIUserAbortError) {
    return signal.aborted ? fail("UPSTREAM_TIMEOUT", true, "deadline") : fail("UPSTREAM_ERROR", true, "aborted");
  }
  if (error instanceof Anthropic.RateLimitError) return fail("RATE_LIMITED", true, "provider_rate_limited");
  if (error instanceof Anthropic.AuthenticationError || error instanceof Anthropic.PermissionDeniedError) {
    return fail("UPSTREAM_ERROR", false, "provider_auth");
  }
  if (error instanceof Anthropic.BadRequestError) return fail("UPSTREAM_ERROR", false, "provider_bad_request");
  if (error instanceof Anthropic.APIConnectionError) return fail("UPSTREAM_ERROR", true, "provider_unreachable");
  if (error instanceof Anthropic.APIError) {
    const status = typeof error.status === "number" ? error.status : 0;
    return fail("UPSTREAM_ERROR", status >= 500, `provider_status_${status}`);
  }
  return fail("UPSTREAM_ERROR", false, "unexpected_client_error");
}
