// Kiểm adapter Gemini bằng fetch giả — không gọi mạng.
import { describe, expect, it } from "vitest";
import { defaultDeps } from "../src/app";
import { ModelOutfitsSchema } from "../src/outfits/schema";
import { ModelRecognitionSchema } from "../src/recognition/schema";
import { GeminiModelClient, toGeminiSchema } from "../src/shared/gemini";
import { AnthropicModelClient } from "../src/shared/model";

interface Captured {
  url: string;
  headers: Headers;
  body: Record<string, unknown>;
}

function fakeFetch(responses: Array<{ status: number; body: unknown }>) {
  const captured: Captured[] = [];
  const impl = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const request = new Request(input, init);
    captured.push({ url: request.url, headers: request.headers, body: (await request.json()) as Record<string, unknown> });
    const next = responses.shift();
    if (next === undefined) throw new Error("hết response giả");
    return new Response(JSON.stringify(next.body), { status: next.status, headers: { "content-type": "application/json" } });
  };
  return { impl: impl as typeof fetch, captured };
}

const validOutput = { outfits: [{ itemIds: ["t1", "b1"], explanation: "Đơn giản." }], shortfallReason: null };

function geminiBody(text: string, finishReason = "STOP", extra: Record<string, unknown> = {}) {
  return {
    candidates: [{ content: { role: "model", parts: [{ text: "suy nghĩ nội bộ", thought: true }, { text }] }, finishReason }],
    usageMetadata: { promptTokenCount: 210, candidatesTokenCount: 40, thoughtsTokenCount: 15 },
    modelVersion: "gemini-3.8-flash",
    ...extra,
  };
}

function call(signal = new AbortController().signal) {
  return {
    system: "SYSTEM",
    content: [
      { type: "text" as const, text: "FRONT:" },
      { type: "image" as const, mediaType: "image/png" as const, base64: "iVBORw0KGgo=" },
    ],
    schema: ModelOutfitsSchema,
    maxTokens: 777,
    effort: "medium" as const,
    signal,
  };
}

const client = (f: ReturnType<typeof fakeFetch>) => new GeminiModelClient("gm-test-key", "gemini-3.8-flash", f.impl);

describe("GeminiModelClient", () => {
  it("gửi generateContent đúng dạng và parse JSON, bỏ phần thought", async () => {
    const f = fakeFetch([{ status: 200, body: geminiBody(JSON.stringify(validOutput)) }]);
    const outcome = await client(f).generate(call());
    expect(outcome).toEqual({
      ok: true,
      data: validOutput,
      meta: { model: "gemini-3.8-flash", inputTokens: 210, outputTokens: 55 },
    });
    const sent = f.captured[0]!;
    expect(sent.url).toBe("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent");
    expect(sent.headers.get("x-goog-api-key")).toBe("gm-test-key");
    expect(sent.body).toMatchObject({
      systemInstruction: { parts: [{ text: "SYSTEM" }] },
      contents: [{ role: "user", parts: [{ text: "FRONT:" }, { inlineData: { mimeType: "image/png", data: "iVBORw0KGgo=" } }] }],
      generationConfig: { responseMimeType: "application/json", maxOutputTokens: 777, responseJsonSchema: { type: "object" } },
    });
  });

  it("schema gửi đi không có $schema và giữ enum/null", () => {
    const schema = toGeminiSchema(ModelRecognitionSchema);
    expect(schema).not.toHaveProperty("$schema");
    const text = JSON.stringify(schema);
    expect(text).toContain('"OUTERWEAR"');
    expect(text).toContain('"null"');
  });

  it.each([
    [429, "RATE_LIMITED", true],
    [403, "UPSTREAM_ERROR", false],
    [400, "UPSTREAM_ERROR", false],
    [503, "UPSTREAM_ERROR", true],
  ] as const)("HTTP %d ⇒ %s retryable=%s, chỉ gọi 1 lần", async (status, code, retryable) => {
    const f = fakeFetch([
      { status, body: { error: { message: "nội dung lỗi provider không được lộ ra" } } },
      { status: 200, body: geminiBody(JSON.stringify(validOutput)) },
    ]);
    const outcome = await client(f).generate(call());
    expect(outcome).toMatchObject({ ok: false, code, retryable });
    expect(f.captured).toHaveLength(1);
  });

  it("finishReason SAFETY ⇒ refusal", async () => {
    const f = fakeFetch([{ status: 200, body: geminiBody("", "SAFETY") }]);
    expect(await client(f).generate(call())).toMatchObject({ ok: false, code: "UPSTREAM_ERROR", reason: "refusal" });
  });

  it("promptFeedback.blockReason ⇒ refusal", async () => {
    const f = fakeFetch([{ status: 200, body: { promptFeedback: { blockReason: "PROHIBITED_CONTENT" } } }]);
    expect(await client(f).generate(call())).toMatchObject({ ok: false, code: "UPSTREAM_ERROR", reason: "refusal" });
  });

  it("MAX_TOKENS ⇒ INVALID_AI_OUTPUT", async () => {
    const f = fakeFetch([{ status: 200, body: geminiBody('{"outfits":[', "MAX_TOKENS") }]);
    expect(await client(f).generate(call())).toMatchObject({ ok: false, code: "INVALID_AI_OUTPUT", reason: "max_tokens" });
  });

  it("JSON sai schema ⇒ INVALID_AI_OUTPUT", async () => {
    const f = fakeFetch([{ status: 200, body: geminiBody('{"outfits": "nope"}') }]);
    expect(await client(f).generate(call())).toMatchObject({ ok: false, code: "INVALID_AI_OUTPUT" });
  });

  it("deadline đã hết ⇒ UPSTREAM_TIMEOUT", async () => {
    const controller = new AbortController();
    controller.abort(new DOMException("deadline", "TimeoutError"));
    const impl = (async (_input: RequestInfo | URL, init?: RequestInit) => {
      init?.signal?.throwIfAborted();
      return new Response("{}");
    }) as typeof fetch;
    const outcome = await new GeminiModelClient("k", "gemini-3.8-flash", impl).generate(call(controller.signal));
    expect(outcome).toMatchObject({ ok: false, code: "UPSTREAM_TIMEOUT" });
  });
});

describe("chọn provider theo AI_PROVIDER", () => {
  const modelFor = defaultDeps().modelFor;

  it("gemini + GEMINI_API_KEY ⇒ Gemini, model mặc định gemini-3.5-flash-lite", () => {
    const m = modelFor({ APP_VERSION: "t", AI_PROVIDER: "gemini", GEMINI_API_KEY: "k1" });
    expect(m).toBeInstanceOf(GeminiModelClient);
    expect(m?.model).toBe("gemini-3.5-flash-lite");
  });

  it("gemini nhưng chỉ có ANTHROPIC key ⇒ chưa cấu hình (null), không tự đổi provider", () => {
    expect(modelFor({ APP_VERSION: "t", AI_PROVIDER: "gemini", ANTHROPIC_API_KEY: "k" })).toBeNull();
  });

  it("mặc định anthropic; AI_MODEL ghi đè", () => {
    const m = modelFor({ APP_VERSION: "t", ANTHROPIC_API_KEY: "k2", AI_MODEL: "claude-haiku-5-5" });
    expect(m).toBeInstanceOf(AnthropicModelClient);
    expect(m?.model).toBe("claude-haiku-5-5");
  });
});
