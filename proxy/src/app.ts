// OWNER: Track B. Bootstrap proxy v2: health, session, auth, kill switch, rate limit, body cap, deadline, log.
// Thứ tự chặn trước khi tới handler: route → auth → kill switch → rate limit → body cap → validate (handler)
// → daily cap → provider. Request bị chặn ở bất kỳ bước nào KHÔNG gọi upstream.
import { z } from "zod";
import {
  AI_SERVICES,
  type AiService,
  type Env,
  SERVICES,
  aiModel,
  aiProvider,
  providerTimeoutMs,
  proxyDeadlineMs,
  dailyCap,
  intVar,
  openAiKeyFor,
  providerLabel,
  recognitionViews,
  serviceEnabled,
} from "./env";
import { GeminiModelClient } from "./shared/gemini";
import { GARMENT_TYPE_CONTRACT_VERSION } from "./recognition/schema";
import { OpenAICompatibleModelClient, normalizeBaseUrl } from "./shared/openai";
import { accessCodeMatches, bearerToken, issueSessionToken, verifySessionToken } from "./shared/auth";
import { ApiError, errorResponse } from "./shared/errors";
import { UsageGuard } from "./shared/guard";
import { json, parseOrThrow, readJsonCapped } from "./shared/http";
import { consoleLogger, type LogEvent } from "./shared/log";
import { AnthropicModelClient, type ModelClient } from "./shared/model";
import { type AppDeps, MAX_ROUTE_DEADLINE_MS, matchPath, type RouteContext, type RouteDef } from "./shared/registry";

/** Deadline tổng của proxy (CT-07: client 20s, proxy ≤18s, provider ≤15s). */
/** Deadline của một request: route tự khai (kẹp 1s–300s) hoặc deadline chung theo env. */
export function routeDeadlineMs(route: Pick<RouteDef, "deadlineMs">, env: Env): number {
  const own = route.deadlineMs;
  if (own === undefined || !Number.isFinite(own)) return proxyDeadlineMs(env);
  return Math.min(MAX_ROUTE_DEADLINE_MS, Math.max(1_000, Math.trunc(own)));
}

/** Mặc định CT-07; giá trị thật theo env qua proxyDeadlineMs(). */
export const PROXY_DEADLINE_MS = 18_000;
const SESSION_BODY_BYTES = 1024;

const SessionRequestSchema = z.object({ accessCode: z.string().min(1).max(128) }).strict();

function defaultModelFor(): (env: Env, service?: AiService) => ModelClient | null {
  const cache = new Map<string, ModelClient>();
  return (env, service) => {
    const provider = aiProvider(env, service);
    const baseUrl = provider === "openai" ? normalizeBaseUrl(env.OPENAI_BASE_URL) : "";
    if (baseUrl === null) return null;
    const apiKey = (
      provider === "gemini"
        ? env.GEMINI_API_KEY
        : provider === "openai"
          ? openAiKeyFor(env, baseUrl)
          : env.ANTHROPIC_API_KEY
    )?.trim();
    if (!apiKey) return null;
    const model = aiModel(env, service);
    const timeoutMs = providerTimeoutMs(env);
    const cacheKey = `${provider}:${baseUrl}:${model}:${timeoutMs}:${apiKey.length}:${apiKey.slice(-6)}`;
    let client = cache.get(cacheKey);
    if (client === undefined) {
      client =
        provider === "gemini"
          ? new GeminiModelClient(apiKey, model, undefined, timeoutMs)
          : provider === "openai"
            ? new OpenAICompatibleModelClient(apiKey, model, baseUrl, undefined, timeoutMs)
            : AnthropicModelClient.fromApiKey(apiKey, model, undefined, timeoutMs);
      // Vài service có thể dùng provider khác nhau cùng lúc ⇒ giữ vài client, không xoá nhau.
      if (cache.size >= 4) cache.clear();
      cache.set(cacheKey, client);
    }
    return client;
  };
}

export function defaultDeps(): AppDeps {
  return {
    guard: new UsageGuard(),
    modelFor: defaultModelFor(),
    now: () => Date.now(),
    uuid: () => crypto.randomUUID(),
    log: consoleLogger,
  };
}

function requestIdFrom(body: unknown): string | null {
  if (typeof body !== "object" || body === null) return null;
  const value = (body as { requestId?: unknown }).requestId;
  return typeof value === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(value) ? value : null;
}

function clientKey(request: Request): string {
  return request.headers.get("cf-connecting-ip") ?? "unknown";
}

export function createApp(routes: readonly RouteDef[], overrides: Partial<AppDeps> = {}) {
  const deps: AppDeps = { ...defaultDeps(), ...overrides };

  async function handleSession(request: Request, env: Env): Promise<Response> {
    const secret = env.SESSION_SECRET?.trim();
    const expected = env.DEMO_ACCESS_CODE?.trim();
    if (!secret || !expected) {
      throw new ApiError("UNSUPPORTED_CAPABILITY", "Session chưa được cấu hình trên server", { status: 503 });
    }
    if (!deps.guard.allowRate(`session:${clientKey(request)}`, 10, deps.now())) {
      throw new ApiError("RATE_LIMITED", "Thử lại sau ít phút");
    }
    const body = parseOrThrow(SessionRequestSchema, await readJsonCapped(request, SESSION_BODY_BYTES));
    if (!(await accessCodeMatches(secret, body.accessCode, expected))) {
      throw new ApiError("UNAUTHORIZED", "Mã truy cập không đúng");
    }
    const ttl = intVar(env.SESSION_TTL_SECONDS, 12 * 3600, 60, 7 * 24 * 3600);
    const { token, session } = await issueSessionToken(secret, ttl, deps.now(), deps.uuid());
    return json({ token, expiresAt: session.expiresAt });
  }

  function health(env: Env): Response {
    const services = Object.fromEntries(SERVICES.map((s) => [s, serviceEnabled(env, s)]));
    return json({
      status: "ok",
      version: env.APP_VERSION,
      schemaVersion: 2,
      services,
      recognitionViews: recognitionViews(env),
      recognitionGarmentTypeVersion: GARMENT_TYPE_CONTRACT_VERSION,
      provider: providerLabel(env),
      providerConfigured: deps.modelFor(env) !== null,
      // Theo từng service: consent recognition phải hiện đúng nơi ảnh được gửi.
      providers: Object.fromEntries(
        AI_SERVICES.map((s) => [s, { provider: providerLabel(env, s), model: aiModel(env, s), configured: deps.modelFor(env, s) !== null }]),
      ),
    });
  }

  async function dispatch(request: Request, env: Env, url: URL, log: LogEvent): Promise<Response> {
    if (request.method === "GET" && url.pathname === "/health") return health(env);
    if (request.method === "POST" && url.pathname === "/v2/session") return handleSession(request, env);

    let route: RouteDef | undefined;
    let params: Record<string, string> = {};
    for (const candidate of routes) {
      if (candidate.method !== request.method) continue;
      const matched = matchPath(candidate.path, url.pathname);
      if (matched !== null) {
        route = candidate;
        params = matched;
        break;
      }
    }
    if (route === undefined) throw new ApiError("INVALID_REQUEST", "Không tìm thấy endpoint", { status: 404 });
    log.service = route.service;

    const secret = env.SESSION_SECRET?.trim();
    const token = bearerToken(request);
    const session = secret && token ? await verifySessionToken(secret, token, deps.now()) : null;
    if (session === null) throw new ApiError("UNAUTHORIZED", "Thiếu hoặc sai session token");

    if (!serviceEnabled(env, route.service)) {
      throw new ApiError("UNSUPPORTED_CAPABILITY", "Dịch vụ đang tắt", { status: 503 });
    }

    const perMinute = intVar(env.RATE_LIMIT_PER_MINUTE, 20, 1, 10_000);
    if (!deps.guard.allowRate(`session:${session.sessionId}`, perMinute, deps.now())) {
      throw new ApiError("RATE_LIMITED", "Quá nhiều yêu cầu, thử lại sau");
    }

    let body: unknown = null;
    if (request.method === "POST") {
      body = await readJsonCapped(request, route.maxBodyBytes);
      const clientRequestId = requestIdFrom(body);
      if (clientRequestId !== null) log.requestId = clientRequestId;
    }

    const service = route.service;
    const ctx: RouteContext = {
      env,
      deps,
      requestId: log.requestId,
      session,
      params,
      signal: AbortSignal.timeout(routeDeadlineMs(route, env)),
      reserveUpstreamCall() {
        if (!deps.guard.tryReserveDaily(service, dailyCap(env, service), deps.now())) {
          throw new ApiError("BUDGET_EXCEEDED", "Đã hết hạn mức AI hôm nay");
        }
      },
      annotate(fields) {
        Object.assign(log, fields);
      },
    };
    return route.handle(body, ctx);
  }

  return {
    async fetch(request: Request, env: Env): Promise<Response> {
      const started = deps.now();
      const url = new URL(request.url);
      const log: LogEvent = { requestId: deps.uuid(), route: `${request.method} ${url.pathname}`, status: 0, latencyMs: 0 };
      let response: Response;
      try {
        response = await dispatch(request, env, url, log);
      } catch (error) {
        const apiError =
          error instanceof ApiError ? error : new ApiError("UPSTREAM_ERROR", "Lỗi máy chủ", { status: 500, retryable: false });
        if (!(error instanceof ApiError)) log.errorCode = "INTERNAL";
        response = errorResponse(apiError, log.requestId);
        log.errorCode ??= apiError.code;
      }
      log.status = response.status;
      log.latencyMs = deps.now() - started;
      deps.log(log);
      return response;
    },
  };
}
