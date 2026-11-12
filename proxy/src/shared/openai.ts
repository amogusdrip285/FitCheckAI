// OWNER: Track B. Adapter OpenAI-compatible Chat Completions (OpenAI hoặc gateway như FOSTA) cùng interface ModelClient.
// Structured output bằng function calling bắt buộc (tool_choice + strict schema): đo ngày 12/11/2026,
// gateway FOSTA bỏ qua `response_format: json_schema` nhưng tôn trọng tool strict. Route vẫn validate ngữ nghĩa sau đó.
import { z } from "zod";
import type { ErrorCode } from "./errors";
import { type ContentPart, type ModelClient, type ModelOutcome, PROVIDER_TIMEOUT_MS, type StructuredCall } from "./model";

export const OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1";
const TOOL_NAME = "submit_result";

interface ChatResponse {
  model?: string;
  choices?: Array<{
    message?: {
      content?: string | null;
      refusal?: string | null;
      tool_calls?: Array<{ type?: string; function?: { name?: string; arguments?: string } }>;
    };
    finish_reason?: string;
  }>;
  usage?: { prompt_tokens?: number; completion_tokens?: number };
}

function fail(code: ErrorCode, retryable: boolean, reason: string): { ok: false; code: ErrorCode; retryable: boolean; reason: string } {
  return { ok: false, code, retryable, reason };
}

/** Base URL hợp lệ: chỉ HTTPS, không credential/query. Sai ⇒ null (provider coi như chưa cấu hình). */
export function normalizeBaseUrl(raw: string | undefined): string | null {
  const value = (raw ?? "").trim() || OPENAI_DEFAULT_BASE_URL;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash) return null;
    return url.toString().replace(/\/+$/, "");
  } catch {
    return null;
  }
}

/**
 * Zod → JSON Schema kiểu "strict" của OpenAI: mọi object `additionalProperties: false`, mọi key là required
 * (field tuỳ chọn trong schema model của ta đều dùng nullable nên vẫn đúng nghĩa).
 */
export function toStrictToolSchema(schema: z.ZodType): Record<string, unknown> {
  const json = z.toJSONSchema(schema) as Record<string, unknown>;
  delete json.$schema;
  const visit = (node: unknown): void => {
    if (Array.isArray(node)) return node.forEach(visit);
    if (node === null || typeof node !== "object") return;
    const obj = node as Record<string, unknown>;
    if (obj.type === "object" && obj.properties && typeof obj.properties === "object") {
      obj.additionalProperties = false;
      obj.required = Object.keys(obj.properties as Record<string, unknown>);
    }
    Object.values(obj).forEach(visit);
  };
  visit(json);
  return json;
}

function toMessages(content: ContentPart[]): Array<Record<string, unknown>> {
  return content.map((part) =>
    part.type === "text"
      ? { type: "text", text: part.text }
      : { type: "image_url", image_url: { url: `data:${part.mediaType};base64,${part.base64}` } },
  );
}

/** Schema node cho phép null không (`type: [..,"null"]` hoặc `anyOf` có `{type:"null"}`). */
function allowsNull(node: unknown): boolean {
  if (node === null || typeof node !== "object") return false;
  const n = node as { type?: unknown; anyOf?: unknown[] };
  if (n.type === "null" || (Array.isArray(n.type) && n.type.includes("null"))) return true;
  return Array.isArray(n.anyOf) && n.anyOf.some(allowsNull);
}

/**
 * Gateway không thực thi strict hoàn toàn: đo 12/11/2026, model bỏ hẳn key nullable (`sameItem`).
 * Key thiếu mà schema cho phép null ⇒ điền null (đúng nghĩa "không biết"); key không-nullable vẫn để zod chặn.
 */
export function fillMissingNullables(value: unknown, schema: unknown): unknown {
  if (value === null || typeof value !== "object" || schema === null || typeof schema !== "object") return value;
  const s = schema as { properties?: Record<string, unknown>; items?: unknown };
  if (Array.isArray(value)) return s.items === undefined ? value : value.map((v) => fillMissingNullables(v, s.items));
  if (s.properties === undefined) return value;
  const out: Record<string, unknown> = { ...(value as Record<string, unknown>) };
  for (const [key, child] of Object.entries(s.properties)) {
    if (!(key in out)) {
      if (allowsNull(child)) out[key] = null;
    } else {
      out[key] = fillMissingNullables(out[key], child);
    }
  }
  return out;
}

/** Gỡ ```json … ``` nếu model trả JSON trong content thay vì tool call. */
function stripFences(text: string): string {
  const match = /^\s*```(?:json)?\s*([\s\S]*?)\s*```\s*$/.exec(text);
  return match ? match[1]! : text;
}

export class OpenAICompatibleModelClient implements ModelClient {
  readonly source = "AI_LIVE" as const;

  constructor(
    private readonly apiKey: string,
    readonly model: string,
    private readonly baseUrl: string = OPENAI_DEFAULT_BASE_URL,
    private readonly fetchImpl: typeof fetch = (input, init) => fetch(input, init),
    private readonly timeoutMs: number = PROVIDER_TIMEOUT_MS,
  ) {}

  async generate<S extends z.ZodType>(call: StructuredCall<S>): Promise<ModelOutcome<z.infer<S>>> {
    const toolSchema = toStrictToolSchema(call.schema);
    const body = {
      model: this.model,
      max_completion_tokens: call.maxTokens,
      reasoning_effort: call.effort,
      messages: [
        { role: "system", content: call.system },
        { role: "user", content: toMessages(call.content) },
      ],
      tools: [
        {
          type: "function",
          function: {
            name: TOOL_NAME,
            description: "Trả kết quả cuối cùng đúng schema.",
            parameters: toolSchema,
            strict: true,
          },
        },
      ],
      tool_choice: { type: "function", function: { name: TOOL_NAME } },
    };

    let response: Response;
    try {
      response = await this.fetchImpl(`${this.baseUrl}/chat/completions`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${this.apiKey}` },
        body: JSON.stringify(body),
        signal: AbortSignal.any([call.signal, AbortSignal.timeout(this.timeoutMs)]),
      });
    } catch (error) {
      if (error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError")) {
        return fail("UPSTREAM_TIMEOUT", true, "timeout");
      }
      return fail("UPSTREAM_ERROR", true, "provider_unreachable");
    }

    // Không đọc/ghi body lỗi của provider ra ngoài (có thể chứa nội dung request).
    if (response.status === 429) return fail("RATE_LIMITED", true, "provider_rate_limited");
    if (response.status === 401 || response.status === 403) return fail("UPSTREAM_ERROR", false, "provider_auth");
    if (response.status >= 500) return fail("UPSTREAM_ERROR", true, `provider_status_${response.status}`);
    if (!response.ok) return fail("UPSTREAM_ERROR", false, `provider_status_${response.status}`);

    let data: ChatResponse;
    try {
      data = (await response.json()) as ChatResponse;
    } catch {
      return fail("UPSTREAM_ERROR", false, "provider_body_not_json");
    }

    const choice = data.choices?.[0];
    if (choice === undefined) return fail("UPSTREAM_ERROR", false, "no_choice");
    const message = choice.message ?? {};
    if (choice.finish_reason === "content_filter" || (message.refusal ?? "").trim() !== "") {
      return fail("UPSTREAM_ERROR", false, "refusal");
    }
    if (choice.finish_reason === "length") return fail("INVALID_AI_OUTPUT", false, "max_tokens");

    const toolCall = message.tool_calls?.find((c) => c.function?.name === TOOL_NAME);
    const text = toolCall?.function?.arguments ?? stripFences(message.content ?? "");
    let raw: unknown;
    try {
      raw = JSON.parse(text);
    } catch {
      return fail("INVALID_AI_OUTPUT", false, "output_not_json");
    }
    const parsed = call.schema.safeParse(fillMissingNullables(raw, toolSchema));
    if (!parsed.success) return fail("INVALID_AI_OUTPUT", false, "output_schema_mismatch");

    return {
      ok: true,
      data: parsed.data as z.infer<S>,
      meta: {
        model: data.model ?? this.model,
        inputTokens: data.usage?.prompt_tokens ?? 0,
        outputTokens: data.usage?.completion_tokens ?? 0,
      },
    };
  }
}
