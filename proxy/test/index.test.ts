import { describe, expect, it } from "vitest";
import { issueSessionToken } from "../src/shared/auth";
import modelOutfits from "./fixtures/v2/model-outfits.json";
import ctExample from "./fixtures/v2/outfits-request-ct02-example.json";
import { ACCESS_CODE, SECRET, setup } from "./helpers";

describe("B-001 proxy bootstrap", () => {
  it("health trả version, schemaVersion và trạng thái service", async () => {
    const t = setup({ SERVICE_RECOGNITION: "off" });
    const res = await t.request("/health");
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({
      status: "ok",
      version: "test",
      schemaVersion: 2,
      services: { recognition: false, outfits: true, tryon: false },
      recognitionViews: "DUAL",
      recognitionGarmentTypeVersion: 1,
      provider: "anthropic",
      providerConfigured: true,
      providers: {
        recognition: { provider: "anthropic", model: "claude-opus-5-5", configured: true },
        outfits: { provider: "anthropic", model: "claude-opus-5-5", configured: true },
      },
    });
  });

  it("endpoint lạ trả 404 với error envelope đầy đủ", async () => {
    const t = setup();
    const res = await t.request("/v1/outfits", { method: "POST" });
    expect(res.status).toBe(404);
    const body = (await res.json()) as { error: Record<string, unknown> };
    expect(body.error).toMatchObject({ code: "INVALID_REQUEST", retryable: false });
    expect(typeof body.error.requestId).toBe("string");
  });

  describe("session", () => {
    it("đổi mã truy cập đúng lấy token dùng được", async () => {
      const t = setup();
      const res = await t.post("/v2/session", { accessCode: ACCESS_CODE }, { token: null });
      expect(res.status).toBe(200);
      const { token, expiresAt } = (await res.json()) as { token: string; expiresAt: number };
      expect(expiresAt).toBe(t.clock.now + 12 * 3600 * 1000);
      t.model.respond(modelOutfits.fewerWithReason);
      const reco = await t.post("/v2/outfits", ctExample, { token });
      expect(reco.status).toBe(200);
    });

    it("sai mã truy cập ⇒ 401", async () => {
      const t = setup();
      const res = await t.post("/v2/session", { accessCode: "wrong" }, { token: null });
      expect(res.status).toBe(401);
    });

    it("chưa cấu hình secret ⇒ 503", async () => {
      const t = setup({ DEMO_ACCESS_CODE: undefined });
      const res = await t.post("/v2/session", { accessCode: ACCESS_CODE }, { token: null });
      expect(res.status).toBe(503);
    });

    it("giới hạn số lần thử mã mỗi IP", async () => {
      const t = setup();
      const statuses: number[] = [];
      for (let i = 0; i < 11; i++) {
        const res = await t.post("/v2/session", { accessCode: "wrong" }, { token: null, headers: { "cf-connecting-ip": "1.2.3.4" } });
        statuses.push(res.status);
      }
      expect(statuses.slice(0, 10).every((s) => s === 401)).toBe(true);
      expect(statuses[10]).toBe(429);
    });
  });

  describe("request bị chặn không gọi upstream", () => {
    it("thiếu token ⇒ 401", async () => {
      const t = setup();
      const res = await t.post("/v2/outfits", ctExample, { token: null });
      expect(res.status).toBe(401);
      expect(t.model.calls).toHaveLength(0);
    });

    it("token sai chữ ký ⇒ 401", async () => {
      const t = setup();
      const forged = (await issueSessionToken("another-secret", 3600, t.clock.now)).token;
      const res = await t.post("/v2/outfits", ctExample, { token: forged });
      expect(res.status).toBe(401);
      expect(t.model.calls).toHaveLength(0);
    });

    it("token bị sửa payload ⇒ 401", async () => {
      const t = setup();
      const token = await t.token();
      const parts = token.split(".");
      parts[2] = String(Number(parts[2]) + 999_999);
      const res = await t.post("/v2/outfits", ctExample, { token: parts.join(".") });
      expect(res.status).toBe(401);
    });

    it("token hết hạn ⇒ 401", async () => {
      const t = setup();
      const token = await t.token(60);
      t.clock.now += 61_000;
      const res = await t.post("/v2/outfits", ctExample, { token });
      expect(res.status).toBe(401);
      expect(t.model.calls).toHaveLength(0);
    });

    it("kill switch tắt ⇒ 503", async () => {
      const t = setup({ SERVICE_OUTFITS: "off" });
      const res = await t.post("/v2/outfits", ctExample);
      expect(res.status).toBe(503);
      expect(t.model.calls).toHaveLength(0);
    });

    it("body vượt cap (khai báo content-length) ⇒ 413", async () => {
      const t = setup();
      const res = await t.post("/v2/outfits", ctExample, { headers: { "content-length": String(65 * 1024) } });
      expect(res.status).toBe(413);
      expect(t.model.calls).toHaveLength(0);
    });

    it("body vượt cap (không khai báo, đọc stream) ⇒ 413", async () => {
      const t = setup();
      const big = JSON.stringify({ ...ctExample, freeText: "x".repeat(70 * 1024) });
      const res = await t.post("/v2/outfits", big);
      expect(res.status).toBe(413);
      expect(t.model.calls).toHaveLength(0);
    });

    it("content-type sai ⇒ 400", async () => {
      const t = setup();
      const res = await t.post("/v2/outfits", ctExample, { headers: { "content-type": "text/plain" } });
      expect(res.status).toBe(400);
    });

    it("JSON hỏng ⇒ 400", async () => {
      const t = setup();
      const res = await t.post("/v2/outfits", "{not json");
      expect(res.status).toBe(400);
    });

    it("chưa có API key ⇒ 503, không gọi upstream", async () => {
      const t = setup({ ANTHROPIC_API_KEY: undefined });
      const res = await t.post("/v2/outfits", ctExample);
      expect(res.status).toBe(503);
      expect(t.model.calls).toHaveLength(0);
    });

    it("rate limit theo session ⇒ 429", async () => {
      const t = setup({ RATE_LIMIT_PER_MINUTE: "2" });
      t.model.respond(modelOutfits.fewerWithReason).respond(modelOutfits.fewerWithReason);
      expect((await t.post("/v2/outfits", ctExample)).status).toBe(200);
      expect((await t.post("/v2/outfits", ctExample)).status).toBe(200);
      const third = await t.post("/v2/outfits", ctExample);
      expect(third.status).toBe(429);
      expect(((await third.json()) as { error: { retryable: boolean } }).error.retryable).toBe(true);
      expect(t.model.calls).toHaveLength(2);
    });

    it("daily cap ⇒ 503 BUDGET_EXCEEDED sau đúng số call cho phép", async () => {
      const t = setup({ DAILY_CAP_OUTFITS: "1" });
      t.model.respond(modelOutfits.fewerWithReason);
      expect((await t.post("/v2/outfits", ctExample)).status).toBe(200);
      const res = await t.post("/v2/outfits", ctExample);
      expect(res.status).toBe(503);
      expect(await res.json()).toMatchObject({ error: { code: "BUDGET_EXCEEDED" } });
      expect(t.model.calls).toHaveLength(1);
    });

    it("request không hợp lệ không tiêu daily cap", async () => {
      const t = setup({ DAILY_CAP_OUTFITS: "1" });
      expect((await t.post("/v2/outfits", { ...ctExample, requestedCount: 9 })).status).toBe(400);
      t.model.respond(modelOutfits.fewerWithReason);
      expect((await t.post("/v2/outfits", ctExample)).status).toBe(200);
    });
  });

  it("log chỉ có trường kỹ thuật, không lộ freeText/token/secret", async () => {
    const t = setup();
    t.model.respond(modelOutfits.fewerWithReason);
    const token = await t.token();
    await t.post("/v2/outfits", { ...ctExample, freeText: "BÍ_MẬT_NGƯỜI_DÙNG" }, { token });
    const dump = JSON.stringify(t.logs);
    expect(dump).not.toContain("BÍ_MẬT_NGƯỜI_DÙNG");
    expect(dump).not.toContain(token);
    expect(dump).not.toContain(SECRET);
    expect(t.logs[0]).toMatchObject({ requestId: "r1", service: "outfits", status: 200, accepted: 1 });
  });
});
