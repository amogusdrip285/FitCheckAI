// Seam mount route (B): deadline riêng từng route — chuẩn bị cho try-on của C (C-207), chưa mount.
import { describe, expect, it } from "vitest";
import { createApp, routeDeadlineMs } from "../src/app";
import { issueSessionToken } from "../src/shared/auth";
import { UsageGuard } from "../src/shared/guard";
import { json } from "../src/shared/http";
import type { RouteDef } from "../src/shared/registry";
import { SECRET, makeEnv } from "./helpers";

describe("routeDeadlineMs", () => {
  const env = makeEnv();
  it("route không khai ⇒ deadline chung 18s (CT-07); env nới được", () => {
    expect(routeDeadlineMs({}, env)).toBe(18_000);
    expect(routeDeadlineMs({}, { ...env, PROVIDER_TIMEOUT_MS: "60000" })).toBe(61_000);
  });
  it("route khai riêng (vd. try-on 120s) được dùng, kẹp trong 1s–300s", () => {
    expect(routeDeadlineMs({ deadlineMs: 120_000 }, env)).toBe(120_000);
    expect(routeDeadlineMs({ deadlineMs: 10 }, env)).toBe(1_000);
    expect(routeDeadlineMs({ deadlineMs: 10_000_000 }, env)).toBe(300_000);
    expect(routeDeadlineMs({ deadlineMs: Number.NaN }, env)).toBe(18_000);
  });
});

describe("signal của route theo deadline riêng", () => {
  it("route có deadlineMs nhận signal bị huỷ đúng hạn; route khác vẫn 18s", async () => {
    const seen: Record<string, { aborted: boolean; elapsed: number }> = {};
    const probe = (path: string, deadlineMs?: number, waitMs = 0): RouteDef => ({
      method: "POST",
      path,
      service: "outfits",
      maxBodyBytes: 1024,
      ...(deadlineMs === undefined ? {} : { deadlineMs }),
      async handle(_body, ctx) {
        const started = Date.now();
        await new Promise<void>((resolve) => {
          const timer = setTimeout(resolve, waitMs);
          ctx.signal.addEventListener("abort", () => (clearTimeout(timer), resolve()));
        });
        seen[path] = { aborted: ctx.signal.aborted, elapsed: Date.now() - started };
        return json({ ok: true });
      },
    });
    const app = createApp([probe("/v2/probe-own", 1_000, 5_000), probe("/v2/probe-default", undefined, 50)], {
      guard: new UsageGuard(),
      modelFor: () => null,
      now: () => Date.now(),
      uuid: () => crypto.randomUUID(),
      log: () => {},
    });
    const env = makeEnv();
    const { token } = await issueSessionToken(SECRET, 3600, Date.now(), "session-1");
    const call = (path: string) =>
      app.fetch(new Request(`https://proxy.test${path}`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
        body: "{}",
      }), env);

    expect((await call("/v2/probe-own")).status).toBe(200);
    expect(seen["/v2/probe-own"]).toMatchObject({ aborted: true });
    expect(seen["/v2/probe-own"]!.elapsed).toBeLessThan(3_000);

    expect((await call("/v2/probe-default")).status).toBe(200);
    expect(seen["/v2/probe-default"]).toMatchObject({ aborted: false });
  }, 15_000);
});
