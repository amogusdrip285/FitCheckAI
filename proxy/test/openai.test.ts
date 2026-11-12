// Kiểm adapter OpenAI-compatible bằng fetch giả — không gọi mạng.
import { describe, expect, it } from "vitest";
import { createApp, defaultDeps } from "../src/app";
import { aiProvider, providerLabel, providerTimeoutMs, proxyDeadlineMs } from "../src/env";
import { GeminiModelClient } from "../src/shared/gemini";
import { ModelOutfitsSchema } from "../src/outfits/schema";
import { ModelRecognitionSchema } from "../src/recognition/schema";
import { OpenAICompatibleModelClient, normalizeBaseUrl, toStrictToolSchema } from "../src/shared/openai";

interface Captured {
  url: string;
  headers: Headers;
  body: Record<string, unknown>;
}

function fakeFetch(responses: Array<{ status: number; body: unknown } | Error>) {
  const captured: Captured[] = [];
  const impl = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const request = new Request(input, init);
    captured.push({ url: request.url, headers: request.headers, body: (await request.json()) as Record<string, unknown> });
    const next = responses.shift();
    if (next === undefined) throw new Error("hết response giả");
    if (next instanceof Error) throw next;
    return new Response(JSON.stringify(next.body), { status: next.status, headers: { "content-type": "application/json" } });
  };
  return { impl: impl as typeof fetch, captured };
}

const validOutput = { outfits: [{ itemIds: ["t1", "b1"], explanation: "Đơn giản." }], shortfallReason: null };

function chatBody(message: Record<string, unknown>, finish = "stop") {
  return {
    model: "gpt-6.1-sol",
    choices: [{ index: 0, message: { role: "assistant", content: null, ...message }, finish_reason: finish }],
    usage: { prompt_tokens: 210, completion_tokens: 40 },
  };
}
const toolMessage = (args: unknown) => ({
  tool_calls: [{ id: "c1", type: "function", function: { name: "submit_result", arguments: JSON.stringify(args) } }],
});

function call(signal = new AbortController().signal) {
  return {
    system: "SYSTEM",
    content: [
      { type: "text" as const, text: "FRONT:" },
      { type: "image" as const, mediaType: "image/png" as const, base64: "iVBORw0KGgo=" },
    ],
    schema: ModelOutfitsSchema,
    maxTokens: 777,
    effort: "low" as const,
    signal,
  };
}

const BASE = "https://codex.fosta.app/v1";
const client = (f: ReturnType<typeof fakeFetch>) => new OpenAICompatibleModelClient("sk-test", "gpt-6.1-sol", BASE, f.impl);

describe("OpenAICompatibleModelClient", () => {
  it("gửi chat/completions với tool strict bắt buộc, ảnh dạng data URL, parse arguments", async () => {
    const f = fakeFetch([{ status: 200, body: chatBody(toolMessage(validOutput)) }]);
    const outcome = await client(f).generate(call());
    expect(outcome).toEqual({
      ok: true,
      data: validOutput,
      meta: { model: "gpt-6.1-sol", inputTokens: 210, outputTokens: 40 },
    });
    const sent = f.captured[0]!;
    expect(sent.url).toBe(`${BASE}/chat/completions`);
    expect(sent.headers.get("authorization")).toBe("Bearer sk-test");
    expect(sent.body).toMatchObject({
      model: "gpt-6.1-sol",
      max_completion_tokens: 777,
      reasoning_effort: "low",
      tool_choice: { type: "function", function: { name: "submit_result" } },
    });
    const messages = sent.body.messages as Array<{ role: string; content: unknown }>;
    expect(messages[0]).toEqual({ role: "system", content: "SYSTEM" });
    expect(messages[1]!.content).toEqual([
      { type: "text", text: "FRONT:" },
      { type: "image_url", image_url: { url: "data:image/png;base64,iVBORw0KGgo=" } },
    ]);
    const tool = (sent.body.tools as Array<{ function: { strict: boolean; parameters: Record<string, unknown> } }>)[0]!;
    expect(tool.function.strict).toBe(true);
    expect(tool.function.parameters.additionalProperties).toBe(false);
  });

  it("chấp nhận JSON trong content (kể cả trong ```json) khi gateway không trả tool call", async () => {
    const f = fakeFetch([{ status: 200, body: chatBody({ content: "```json\n" + JSON.stringify(validOutput) + "\n```" }) }]);
    expect(await client(f).generate(call())).toMatchObject({ ok: true, data: validOutput });
  });

  it("key nullable bị bỏ ⇒ điền null; key bắt buộc không-nullable bị bỏ vẫn bị chặn", async () => {
    const recognition = {
      attributes: { category: "TOP", colors: ["WHITE"], pattern: "SOLID", styleTags: [], formality: 1, seasonTags: [] },
      evidence: [{ field: "category", views: ["FRONT"] }],
      conflictFields: [],
      uncertainFields: [],
    }; // thiếu sameItem và attributes.material (đều nullable) — đúng hành vi gateway đo được
    const f = fakeFetch([{ status: 200, body: chatBody(toolMessage(recognition)) }]);
    const outcome = await client(f).generate({ ...call(), schema: ModelRecognitionSchema });
    expect(outcome).toMatchObject({ ok: true, data: { sameItem: null, attributes: { material: null, category: "TOP" } } });

    const missingCategory = { ...recognition, attributes: { ...recognition.attributes, category: undefined } };
    const g = fakeFetch([{ status: 200, body: chatBody(toolMessage(missingCategory)) }]);
    expect(await client(g).generate({ ...call(), schema: ModelRecognitionSchema })).toMatchObject({
      ok: false,
      reason: "output_schema_mismatch",
    });
  });

  it("output không phải JSON / sai schema / cụt ⇒ INVALID_AI_OUTPUT", async () => {
    const cases = [
      [chatBody({ content: "Áo khoác mùa đông." }), "output_not_json"],
      [chatBody(toolMessage({ outfits: "x" })), "output_schema_mismatch"],
      [chatBody(toolMessage(validOutput), "length"), "max_tokens"],
    ] as const;
    for (const [body, reason] of cases) {
      const f = fakeFetch([{ status: 200, body }]);
      expect(await client(f).generate(call())).toEqual({ ok: false, code: "INVALID_AI_OUTPUT", retryable: false, reason });
    }
  });

  it("refusal/content_filter ⇒ UPSTREAM_ERROR refusal", async () => {
    for (const body of [chatBody({ refusal: "Không thể." }), chatBody({ content: "" }, "content_filter")]) {
      const f = fakeFetch([{ status: 200, body }]);
      expect(await client(f).generate(call())).toMatchObject({ ok: false, code: "UPSTREAM_ERROR", reason: "refusal" });
    }
  });

  it("map HTTP status và lỗi mạng, không lộ body provider", async () => {
    const cases: Array<[number, string, boolean, string]> = [
      [429, "RATE_LIMITED", true, "provider_rate_limited"],
      [401, "UPSTREAM_ERROR", false, "provider_auth"],
      [503, "UPSTREAM_ERROR", true, "provider_status_503"],
      [400, "UPSTREAM_ERROR", false, "provider_status_400"],
    ];
    for (const [status, code, retryable, reason] of cases) {
      const f = fakeFetch([{ status, body: { error: { message: "secret prompt echo" } } }]);
      const outcome = await client(f).generate(call());
      expect(outcome).toEqual({ ok: false, code, retryable, reason });
    }
    const unreachable = fakeFetch([new TypeError("fetch failed")]);
    expect(await client(unreachable).generate(call())).toMatchObject({ code: "UPSTREAM_ERROR", reason: "provider_unreachable" });
  });

  it("deadline proxy huỷ ⇒ UPSTREAM_TIMEOUT", async () => {
    const controller = new AbortController();
    const hanging = (async (_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_, reject) => {
        init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      })) as typeof fetch;
    const pending = new OpenAICompatibleModelClient("sk-test", "m", BASE, hanging).generate(call(controller.signal));
    controller.abort();
    expect(await pending).toMatchObject({ ok: false, code: "UPSTREAM_TIMEOUT" });
  });
});

describe("schema strict cho tool", () => {
  it("mọi object có additionalProperties=false và required đủ key", () => {
    for (const schema of [ModelOutfitsSchema, ModelRecognitionSchema]) {
      const json = toStrictToolSchema(schema);
      const objects: Array<Record<string, unknown>> = [];
      const walk = (n: unknown): void => {
        if (Array.isArray(n)) return n.forEach(walk);
        if (n === null || typeof n !== "object") return;
        const o = n as Record<string, unknown>;
        if (o.type === "object") objects.push(o);
        Object.values(o).forEach(walk);
      };
      walk(json);
      expect(objects.length).toBeGreaterThan(0);
      for (const o of objects) {
        expect(o.additionalProperties).toBe(false);
        expect(new Set(o.required as string[])).toEqual(new Set(Object.keys(o.properties as object)));
      }
      expect(json.$schema).toBeUndefined();
    }
  });
});

describe("provider riêng từng service", () => {
  const env = {
    APP_VERSION: "t",
    AI_PROVIDER: "openai",
    AI_MODEL: "gpt-6.1-sol",
    AI_PROVIDER_RECOGNITION: "gemini",
    OPENAI_API_KEY: "sk-x",
    OPENAI_BASE_URL: BASE,
    GEMINI_API_KEY: "gm-x",
  };

  it("recognition ghi đè sang Gemini với model mặc định của Gemini; outfits theo cấu hình chung", () => {
    const modelFor = defaultDeps().modelFor;
    const recognition = modelFor(env, "recognition");
    const outfits = modelFor(env, "outfits");
    expect(recognition).toBeInstanceOf(GeminiModelClient);
    expect(recognition?.model).toBe("gemini-3.5-flash-lite"); // AI_MODEL chung (GPT) không áp sang Gemini
    expect(outfits).toBeInstanceOf(OpenAICompatibleModelClient);
    expect(outfits?.model).toBe("gpt-6.1-sol");
    // gọi xen kẽ vẫn ra đúng client (cache không xoá nhau)
    expect(modelFor(env, "recognition")).toBe(recognition);
    expect(modelFor(env, "outfits")).toBe(outfits);
  });

  it("AI_MODEL_<SERVICE> thắng; thiếu key của provider ghi đè ⇒ chỉ service đó chưa cấu hình", () => {
    const modelFor = defaultDeps().modelFor;
    expect(modelFor({ ...env, AI_MODEL_RECOGNITION: "gemini-3.8-flash" }, "recognition")?.model).toBe("gemini-3.8-flash");
    expect(modelFor({ ...env, GEMINI_API_KEY: "" }, "recognition")).toBeNull();
    expect(modelFor({ ...env, GEMINI_API_KEY: "" }, "outfits")).toBeInstanceOf(OpenAICompatibleModelClient);
    expect(aiProvider({ ...env, AI_PROVIDER_RECOGNITION: "nonsense" }, "recognition")).toBe("openai");
  });

  it("/health báo provider/model từng service", async () => {
    const app = createApp([], {});
    const res = await app.fetch(new Request("https://proxy.test/health"), env);
    const body = (await res.json()) as { providers: Record<string, unknown> };
    expect(body.providers).toEqual({
      recognition: { provider: "gemini", model: "gemini-3.5-flash-lite", configured: true },
      outfits: { provider: "openai@codex.fosta.app", model: "gpt-6.1-sol", configured: true },
    });
  });
});

describe("deadline cấu hình được", () => {
  const base = { APP_VERSION: "t" };
  it("mặc định đúng CT-07 15s/18s", () => {
    expect(providerTimeoutMs(base)).toBe(15_000);
    expect(proxyDeadlineMs(base)).toBe(18_000);
  });
  it("nới để test; proxy luôn > provider; giá trị rác/vượt biên bị kẹp", () => {
    expect(providerTimeoutMs({ ...base, PROVIDER_TIMEOUT_MS: "45000" })).toBe(45_000);
    expect(proxyDeadlineMs({ ...base, PROVIDER_TIMEOUT_MS: "45000" })).toBe(46_000);
    expect(proxyDeadlineMs({ ...base, PROVIDER_TIMEOUT_MS: "45000", PROXY_DEADLINE_MS: "50000" })).toBe(50_000);
    expect(providerTimeoutMs({ ...base, PROVIDER_TIMEOUT_MS: "abc" })).toBe(15_000);
    expect(providerTimeoutMs({ ...base, PROVIDER_TIMEOUT_MS: "999999" })).toBe(120_000);
  });
});

describe("cấu hình provider openai", () => {
  it("base URL chỉ HTTPS, mặc định OpenAI", () => {
    expect(normalizeBaseUrl(undefined)).toBe("https://api.openai.com/v1");
    expect(normalizeBaseUrl("https://codex.fosta.app/v1/")).toBe("https://codex.fosta.app/v1");
    expect(normalizeBaseUrl("http://codex.fosta.app/v1")).toBeNull();
    expect(normalizeBaseUrl("https://u:p@x.test/v1")).toBeNull();
    expect(normalizeBaseUrl("not a url")).toBeNull();
  });

  it("modelFor chọn adapter openai khi có key + base URL hợp lệ", () => {
    const modelFor = defaultDeps().modelFor;
    const env = { APP_VERSION: "t", AI_PROVIDER: "openai", OPENAI_API_KEY: "sk-x", OPENAI_BASE_URL: BASE };
    expect(modelFor(env)).toBeInstanceOf(OpenAICompatibleModelClient);
    expect(modelFor(env)?.model).toBe("gpt-6-luna"); // mặc định openai khi không đặt AI_MODEL
    expect(modelFor({ ...env, OPENAI_API_KEY: "" })).toBeNull();
    expect(modelFor({ ...env, OPENAI_BASE_URL: "http://insecure.test/v1" })).toBeNull();
  });

  it("/health ghi rõ host gateway để consent không gọi nhầm là OpenAI", () => {
    expect(providerLabel({ APP_VERSION: "t", AI_PROVIDER: "openai", OPENAI_BASE_URL: BASE })).toBe("openai@codex.fosta.app");
    expect(providerLabel({ APP_VERSION: "t", AI_PROVIDER: "openai" })).toBe("openai@api.openai.com");
    expect(providerLabel({ APP_VERSION: "t", AI_PROVIDER: "gemini" })).toBe("gemini");
  });
});
