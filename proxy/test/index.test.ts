import { describe, expect, it } from "vitest";
import worker, { type Env } from "../src/index";

const env: Env = { APP_VERSION: "test" };
const call = (path: string, init?: RequestInit) =>
  worker.fetch(new Request(`https://proxy.test${path}`, init), env);

describe("proxy skeleton", () => {
  it("returns health status", async () => {
    const res = await call("/health");
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ status: "ok", version: "test" });
  });

  it("rejects v1 calls without device id", async () => {
    const res = await call("/v1/tag", { method: "POST", body: "{}" });
    expect(res.status).toBe(400);
    expect(await res.json()).toMatchObject({ error: { code: "MISSING_DEVICE_ID" } });
  });

  it("marks unimplemented endpoints with 501", async () => {
    const res = await call("/v1/outfits", {
      method: "POST",
      body: "{}",
      headers: { "X-Device-Id": "d1" },
    });
    expect(res.status).toBe(501);
  });

  it("returns 404 for unknown paths", async () => {
    const res = await call("/nope");
    expect(res.status).toBe(404);
  });
});
