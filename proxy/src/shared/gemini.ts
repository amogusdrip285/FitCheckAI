// OWNER: Track B. Adapter Google Gemini (REST generateContent) cùng interface ModelClient.
// Dùng để TEST bằng free tier. Lưu ý: ở free tier Google được dùng nội dung gửi lên để cải thiện
// sản phẩm ⇒ chỉ gửi ảnh/dữ liệu test, KHÔNG gửi ảnh người dùng thật.
import { z } from "zod";
import type { ErrorCode } from "./errors";
import { type ContentPart, type ModelClient, type ModelOutcome, PROVIDER_TIMEOUT_MS, type StructuredCall } from "./model";

const GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

/** finishReason/blockReason coi là bị chặn theo chính sách (tương đương refusal). */
const BLOCKED_REASONS = new Set([
  "SAFETY",
  "RECITATION",
  "BLOCKLIST",
  "PROHIBITED_CONTENT",
  "SPII",
  "IMAGE_SAFETY",
  "LANGUAGE",
]);

interface GeminiResponse {
  candidates?: Array<{
    content?: { parts?: Array<{ text?: string; thought?: boolean }> };
    finishReason?: string;
  }>;
  promptFeedback?: { blockReason?: string };
  usageMetadata?: { promptTokenCount?: number; candidatesTokenCount?: number; thoughtsTokenCount?: number };
  modelVersion?: string;
}

function fail(code: ErrorCode, retryable: boolean, reason: string): { ok: false; code: ErrorCode; retryable: boolean; reason: string } {
  return { ok: false, code, retryable, reason };
}

/** Zod → JSON Schema cho `responseJsonSchema`; bỏ `$schema` vì API không cần. */
export function toGeminiSchema(schema: z.ZodType): Record<string, unknown> {
  const json = z.toJSONSchema(schema) as Record<string, unknown>;
  delete json.$schema;
  return json;
}

function toParts(content: ContentPart[]): Array<Record<string, unknown>> {
  return content.map((part) =>
    part.type === "text" ? { text: part.text } : { inlineData: { mimeType: part.mediaType, data: part.base64 } },
  );
}

export class GeminiModelClient implements ModelClient {
  readonly source = "AI_LIVE" as const;

  constructor(
    private readonly apiKey: string,
    readonly model: string,
    private readonly fetchImpl: typeof fetch = (input, init) => fetch(input, init),
    private readonly timeoutMs: number = PROVIDER_TIMEOUT_MS,
  ) {}

  async generate<S extends z.ZodType>(call: StructuredCall<S>): Promise<ModelOutcome<z.infer<S>>> {
    const body = {
      systemInstruction: { parts: [{ text: call.system }] },
      contents: [{ role: "user", parts: toParts(call.content) }],
      generationConfig: {
        responseMimeType: "application/json",
        responseJsonSchema: toGeminiSchema(call.schema),
        maxOutputTokens: call.maxTokens,
      },
    };

    let response: Response;
    try {
      response = await this.fetchImpl(`${GEMINI_BASE_URL}/models/${encodeURIComponent(this.model)}:generateContent`, {
        method: "POST",
        headers: { "content-type": "application/json", "x-goog-api-key": this.apiKey },
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

    let data: GeminiResponse;
    try {
      data = (await response.json()) as GeminiResponse;
    } catch {
      return fail("UPSTREAM_ERROR", false, "provider_body_not_json");
    }

    if (data.promptFeedback?.blockReason) return fail("UPSTREAM_ERROR", false, "refusal");
    const candidate = data.candidates?.[0];
    if (candidate === undefined) return fail("UPSTREAM_ERROR", false, "no_candidate");
    const finish = candidate.finishReason ?? "STOP";
    if (BLOCKED_REASONS.has(finish)) return fail("UPSTREAM_ERROR", false, "refusal");
    if (finish === "MAX_TOKENS") return fail("INVALID_AI_OUTPUT", false, "max_tokens");

    const text = (candidate.content?.parts ?? [])
      .filter((p) => p.thought !== true)
      .map((p) => p.text ?? "")
      .join("");
    let raw: unknown;
    try {
      raw = JSON.parse(text);
    } catch {
      return fail("INVALID_AI_OUTPUT", false, "output_not_json");
    }
    const parsed = call.schema.safeParse(raw);
    if (!parsed.success) return fail("INVALID_AI_OUTPUT", false, "output_schema_mismatch");

    const usage = data.usageMetadata ?? {};
    return {
      ok: true,
      data: parsed.data as z.infer<S>,
      meta: {
        model: data.modelVersion ?? this.model,
        inputTokens: usage.promptTokenCount ?? 0,
        outputTokens: (usage.candidatesTokenCount ?? 0) + (usage.thoughtsTokenCount ?? 0),
      },
    };
  }
}
