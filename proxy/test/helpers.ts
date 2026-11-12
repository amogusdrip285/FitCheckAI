import type { z } from "zod";
import { createApp } from "../src/app";
import type { Env } from "../src/env";
import { ROUTES } from "../src/index";
import { issueSessionToken } from "../src/shared/auth";
import type { ErrorCode } from "../src/shared/errors";
import { UsageGuard } from "../src/shared/guard";
import type { LogEvent } from "../src/shared/log";
import type { ModelClient, ModelOutcome, StructuredCall } from "../src/shared/model";

export const SECRET = "test-session-secret-0123456789";
export const ACCESS_CODE = "demo-access-code";

export function makeEnv(overrides: Partial<Env> = {}): Env {
  return {
    APP_VERSION: "test",
    ANTHROPIC_API_KEY: "sk-test-not-real",
    SESSION_SECRET: SECRET,
    DEMO_ACCESS_CODE: ACCESS_CODE,
    SERVICE_RECOGNITION: "on",
    SERVICE_OUTFITS: "on",
    ...overrides,
  };
}

/** Model giả: ghi lại call, trả outcome theo hàng đợi. Data được parse qua schema để fixture luôn khớp structured output. */
export class FakeModel implements ModelClient {
  readonly source = "AI_LIVE" as const;
  readonly model = "fake-model";
  readonly calls: StructuredCall<z.ZodType>[] = [];
  private readonly queue: Array<{ data: unknown } | { code: ErrorCode; retryable: boolean }> = [];

  respond(data: unknown): this {
    this.queue.push({ data });
    return this;
  }

  fail(code: ErrorCode, retryable = false): this {
    this.queue.push({ code, retryable });
    return this;
  }

  async generate<S extends z.ZodType>(call: StructuredCall<S>): Promise<ModelOutcome<z.infer<S>>> {
    this.calls.push(call);
    const next = this.queue.shift();
    if (next === undefined) throw new Error("FakeModel: không còn response trong hàng đợi");
    if ("code" in next) return { ok: false, code: next.code, retryable: next.retryable, reason: "fake" };
    return {
      ok: true,
      data: call.schema.parse(next.data) as z.infer<S>,
      meta: { model: this.model, inputTokens: 100, outputTokens: 50 },
    };
  }
}

export function setup(envOverrides: Partial<Env> = {}) {
  const model = new FakeModel();
  const logs: LogEvent[] = [];
  const clock = { now: Date.UTC(2026, 10, 14, 2, 0, 0) };
  let counter = 0;
  const env = makeEnv(envOverrides);
  const app = createApp(ROUTES, {
    guard: new UsageGuard(),
    modelFor: (e) => (e.ANTHROPIC_API_KEY ? model : null),
    now: () => clock.now,
    uuid: () => `00000000-0000-4000-8000-${String(++counter).padStart(12, "0")}`,
    log: (event) => logs.push(event),
  });

  async function token(ttlSeconds = 3600): Promise<string> {
    return (await issueSessionToken(SECRET, ttlSeconds, clock.now, "session-1")).token;
  }

  async function request(path: string, init: RequestInit = {}): Promise<Response> {
    return app.fetch(new Request(`https://proxy.test${path}`, init), env);
  }

  async function post(path: string, body: unknown, options: { token?: string | null; headers?: Record<string, string> } = {}) {
    const authToken = options.token === undefined ? await token() : options.token;
    const headers: Record<string, string> = { "content-type": "application/json", ...options.headers };
    if (authToken !== null) headers.authorization = `Bearer ${authToken}`;
    return request(path, { method: "POST", headers, body: typeof body === "string" ? body : JSON.stringify(body) });
  }

  return { app, env, model, logs, clock, token, request, post };
}

function base64(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

/** Bytes ảnh tổng hợp (chỉ có magic header + nội dung giả) — không phải ảnh thật, không có PII. */
export function fakeImage(kind: "png" | "jpeg" | "webp", seed: number, size = 64): string {
  const header =
    kind === "png"
      ? [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
      : kind === "jpeg"
        ? [0xff, 0xd8, 0xff, 0xe0]
        : [0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50];
  const bytes = new Uint8Array(Math.max(size, header.length));
  bytes.set(header);
  for (let i = header.length; i < bytes.length; i++) bytes[i] = (i * 31 + seed) % 256;
  return base64(bytes);
}
