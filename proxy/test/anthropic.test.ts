// Kiểm adapter Anthropic bằng fetch giả — không gọi mạng, không tốn tiền.
import { describe, expect, it } from "vitest";
import { ModelOutfitsSchema } from "../src/outfits/schema";
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
    return new Response(JSON.stringify(next.body), { status: next.status, headers: { "content-type": "application/json", "request-id": "req_fake" } });
  };
  return { impl: impl as typeof fetch, captured };
}

function message(text: string, stopReason = "end_turn") {
  return {
    id: "msg_fake",
    type: "message",
    role: "assistant",
    model: "claude-opus-5-5",
    content: [{ type: "text", text }],
    stop_reason: stopReason,
    stop_sequence: null,
    usage: { input_tokens: 321, output_tokens: 45 },
  };
}

const validOutput = { outfits: [{ itemIds: ["t1", "b1"], explanation: "Đơn giản." }], shortfallReason: null };

function call(signal = new AbortController().signal) {
  return {
    system: "SYSTEM",
    content: [
      { type: "text" as const, text: "FRONT:" },
      { type: "image" as const, mediaType: "image/jpeg" as const, base64: "/9j/AAAA" },
    ],
    schema: ModelOutfitsSchema,
    maxTokens: 1234,
    effort: "medium" as const,
    signal,
  };
}

describe("AnthropicModelClient", () => {
  it("gửi đúng model, ảnh base64, structured output, effort và fallback; parse kết quả", async () => {
    const f = fakeFetch([{ status: 200, body: message(JSON.stringify(validOutput)) }]);
    const client = AnthropicModelClient.fromApiKey("sk-test", "claude-opus-5-5", f.impl);
    const outcome = await client.generate(call());

    expect(outcome).toEqual({
      ok: true,
      data: validOutput,
      meta: { model: "claude-opus-5-5", inputTokens: 321, outputTokens: 45 },
    });
    const sent = f.captured[0]!;
    expect(sent.url).toContain("/v1/messages");
    expect(`${sent.headers.get("x-api-key") ?? ""} ${sent.headers.get("authorization") ?? ""}`).toContain("sk-test");
    expect(sent.headers.get("anthropic-beta")).toContain("server-side-fallback-2026-07-01");
    expect(sent.body).toMatchObject({
      model: "claude-opus-5-5",
      max_tokens: 1234,
      system: "SYSTEM",
      fallbacks: "default",
      output_config: { effort: "medium", format: { type: "json_schema" } },
      messages: [
        {
          role: "user",
          content: [
            { type: "text", text: "FRONT:" },
            { type: "image", source: { type: "base64", media_type: "image/jpeg", data: "/9j/AAAA" } },
          ],
        },
      ],
    });
    expect(sent.body).not.toHaveProperty("thinking");
  });

  it("stop_reason refusal ⇒ UPSTREAM_ERROR không retry", async () => {
    const f = fakeFetch([{ status: 200, body: message("", "refusal") }]);
    const outcome = await AnthropicModelClient.fromApiKey("sk-test", "claude-opus-5-5", f.impl).generate(call());
    expect(outcome).toMatchObject({ ok: false, code: "UPSTREAM_ERROR", retryable: false, reason: "refusal" });
  });

  it("stop_reason max_tokens ⇒ INVALID_AI_OUTPUT", async () => {
    const f = fakeFetch([{ status: 200, body: message('{"outfits": [', "max_tokens") }]);
    const outcome = await AnthropicModelClient.fromApiKey("sk-test", "claude-opus-5-5", f.impl).generate(call());
    expect(outcome).toMatchObject({ ok: false, code: "INVALID_AI_OUTPUT" });
  });

  it("JSON sai schema ⇒ INVALID_AI_OUTPUT", async () => {
    const f = fakeFetch([{ status: 200, body: message('{"wrong": true}') }]);
    const outcome = await AnthropicModelClient.fromApiKey("sk-test", "claude-opus-5-5", f.impl).generate(call());
    expect(outcome).toMatchObject({ ok: false, code: "INVALID_AI_OUTPUT" });
  });

  it("429 ⇒ RATE_LIMITED và chỉ gọi 1 lần (không auto retry)", async () => {
    const f = fakeFetch([
      { status: 429, body: { type: "error", error: { type: "rate_limit_error", message: "slow down" } } },
      { status: 200, body: message(JSON.stringify(validOutput)) },
    ]);
    const outcome = await AnthropicModelClient.fromApiKey("sk-test", "claude-opus-5-5", f.impl).generate(call());
    expect(outcome).toMatchObject({ ok: false, code: "RATE_LIMITED", retryable: true });
    expect(f.captured).toHaveLength(1);
  });

  it("500 ⇒ UPSTREAM_ERROR retryable, không auto retry", async () => {
    const f = fakeFetch([
      { status: 500, body: { type: "error", error: { type: "api_error", message: "boom" } } },
      { status: 200, body: message(JSON.stringify(validOutput)) },
    ]);
    const outcome = await AnthropicModelClient.fromApiKey("sk-test", "claude-opus-5-5", f.impl).generate(call());
    expect(outcome).toMatchObject({ ok: false, code: "UPSTREAM_ERROR", retryable: true });
    expect(f.captured).toHaveLength(1);
  });

  it("401 (key sai) ⇒ UPSTREAM_ERROR không retryable", async () => {
    const f = fakeFetch([{ status: 401, body: { type: "error", error: { type: "authentication_error", message: "bad key" } } }]);
    const outcome = await AnthropicModelClient.fromApiKey("sk-test", "claude-opus-5-5", f.impl).generate(call());
    expect(outcome).toMatchObject({ ok: false, code: "UPSTREAM_ERROR", retryable: false, reason: "provider_auth" });
  });

  it("deadline đã hết ⇒ UPSTREAM_TIMEOUT", async () => {
    const controller = new AbortController();
    controller.abort();
    const f = fakeFetch([{ status: 200, body: message(JSON.stringify(validOutput)) }]);
    const outcome = await AnthropicModelClient.fromApiKey("sk-test", "claude-opus-5-5", f.impl).generate(call(controller.signal));
    expect(outcome).toMatchObject({ ok: false, code: "UPSTREAM_TIMEOUT" });
  });
});
