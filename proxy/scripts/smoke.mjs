#!/usr/bin/env node
// OWNER: Track B (B-001). Smoke test proxy sau khi deploy (hoặc chạy local).
//
//   FITCHECK_ACCESS_CODE=... node proxy/scripts/smoke.mjs --base-url https://<worker>.workers.dev
//       ⇒ chỉ kiểm guard (proxy chặn TRƯỚC khi gọi AI) — KHÔNG tốn tiền.
//   ... --live            ⇒ thêm 1 call /v2/outfits thật (tốn ~1 call) và kiểm invariant CT-02.
//   ... --allow-http      ⇒ cho phép http:// (chỉ khi chạy wrangler dev local).
//   FITCHECK_SMOKE_TIMEOUT_MS=30000 (mặc định 20000, như app).
//
// Không in token/mã truy cập/key. Thoát mã 1 nếu có kiểm tra FAIL.
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { checkErrorEnvelope, checkRecommendation } from "../../eval/ai/lib/invariants.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = Object.fromEntries(
  process.argv.slice(2).map((a, i, all) => {
    if (!a.startsWith("--")) return [];
    const key = a.slice(2);
    const next = all[i + 1];
    return [key, next && !next.startsWith("--") ? next : true];
  }).filter((e) => e.length === 2),
);
const base = String(args["base-url"] ?? process.env.FITCHECK_PROXY_URL ?? "").replace(/\/+$/, "");
const accessCode = process.env.FITCHECK_ACCESS_CODE ?? "";
const timeoutMs = Number(process.env.FITCHECK_SMOKE_TIMEOUT_MS ?? 20_000);
if (!base) fatal("Thiếu --base-url (vd. https://fitcheck-proxy.<tên>.workers.dev)");
if (!accessCode) fatal("Thiếu biến môi trường FITCHECK_ACCESS_CODE (giá trị DEMO_ACCESS_CODE đã đặt bằng wrangler secret)");
if (!base.startsWith("https://") && args["allow-http"] !== true) fatal("Base URL phải là HTTPS (thêm --allow-http chỉ khi test wrangler dev local)");

const results = [];
function record(name, ok, detail = "") {
  results.push({ name, ok });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? ` — ${detail}` : ""}`);
}
function fatal(message) {
  console.error(message);
  process.exit(2);
}

async function call(method, route, { body, token, raw } = {}) {
  const started = performance.now();
  const headers = {};
  if (body !== undefined) headers["content-type"] = "application/json";
  if (token) headers.authorization = `Bearer ${token}`;
  try {
    const res = await fetch(`${base}${route}`, {
      method,
      headers,
      body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
    let json = null;
    try {
      json = await res.json();
    } catch {
      json = null;
    }
    return { status: res.status, json, ms: Math.round(performance.now() - started) };
  } catch (error) {
    return { status: 0, json: null, ms: Math.round(performance.now() - started), error: error.name };
  }
}

const fakeJpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(60, 7)]).toString("base64");
const item = (id, category) => ({ id, revision: 1, attributes: { category, colors: ["BLACK"] } });
const outfitsRequest = (items, extra = {}) => ({
  schemaVersion: 2, requestId: `smoke-${crypto.randomUUID()}`, wardrobeSnapshotId: "smoke", items, profile: null,
  occasion: "CASUAL", styleOverrides: [], freeText: "", weather: null, requestedCount: 1, focusItemId: null,
  excludeOutfitSignatures: [], regenerationOfRequestId: null, ...extra,
});

// 1. Health (không cần auth)
const health = await call("GET", "/health");
const h = health.json ?? {};
record("GET /health 200, schemaVersion 2", health.status === 200 && h.schemaVersion === 2, `${health.ms} ms`);
const enabled = { recognition: h.services?.recognition === true, outfits: h.services?.outfits === true };
console.log(`      services: recognition=${enabled.recognition} outfits=${enabled.outfits} tryon=${h.services?.tryon === true}`);
for (const s of ["recognition", "outfits"]) {
  const p = h.providers?.[s];
  if (p) console.log(`      ${s}: provider=${p.provider} model=${p.model} configured=${p.configured}`);
}

// 2. Session
const wrong = await call("POST", "/v2/session", { body: { accessCode: `${accessCode}-sai` } });
record("Mã truy cập sai ⇒ 401", wrong.status === 401 && checkErrorEnvelope(wrong.json).length === 0);
const session = await call("POST", "/v2/session", { body: { accessCode } });
const token = session.json?.token;
record("Mã truy cập đúng ⇒ token", session.status === 200 && typeof token === "string" && token.length > 0);
if (!token) {
  console.error("Không có token — dừng (kiểm DEMO_ACCESS_CODE và SESSION_SECRET).");
  process.exit(1);
}

// 3. Guard: không token ⇒ 401
const noAuth = await call("POST", "/v2/outfits", { body: outfitsRequest([item("t1", "TOP"), item("b1", "BOTTOM")]) });
record("Không token ⇒ 401", noAuth.status === 401);

// 4. Guard trước AI: tủ chỉ có TOP ⇒ 409 (hoặc 503 nếu service đang tắt)
const onlyTop = await call("POST", "/v2/outfits", { token, body: outfitsRequest([item("t1", "TOP")]) });
if (enabled.outfits) record("Tủ chỉ có TOP ⇒ 409 INSUFFICIENT_WARDROBE, không gọi AI", onlyTop.status === 409 && onlyTop.json?.error?.code === "INSUFFICIENT_WARDROBE");
else record("SERVICE_OUTFITS off ⇒ 503 (kill switch)", onlyTop.status === 503);

// 5. Guard: body quá cap ⇒ 413
const big = JSON.stringify(outfitsRequest([item("t1", "TOP")], { freeText: "" })).replace('"freeText":""', `"freeText":"${"a".repeat(70_000)}"`);
const oversize = await call("POST", "/v2/outfits", { token, body: big, raw: true });
record("Body > 64 KiB ⇒ 413 hoặc 503 nếu service tắt", oversize.status === 413 || (!enabled.outfits && oversize.status === 503), `HTTP ${oversize.status}`);

// 6. Guard: recognition thiếu consent ⇒ 403 (hoặc 503 nếu tắt)
const noConsent = await call("POST", "/v2/recognition", {
  token,
  body: { schemaVersion: 2, requestId: `smoke-${crypto.randomUUID()}`, itemId: "smoke-item", inputRevision: 1,
    front: { view: "FRONT", mediaId: "m1", mimeType: "image/jpeg", dataBase64: fakeJpeg }, back: null, consentGrant: null },
});
if (enabled.recognition) record("Recognition thiếu consent ⇒ 403 CONSENT_REQUIRED", noConsent.status === 403 && noConsent.json?.error?.code === "CONSENT_REQUIRED");
else record("SERVICE_RECOGNITION off ⇒ 503 (kill switch)", noConsent.status === 503);

// 7. Live (tuỳ chọn, tốn 1 call)
if (args.live === true) {
  if (!enabled.outfits) {
    record("Live /v2/outfits", false, "SERVICE_OUTFITS đang off");
  } else {
    const fixture = JSON.parse(readFileSync(path.join(HERE, "..", "test", "fixtures", "v2", "outfits-request-ct02-example.json"), "utf8"));
    const request = { ...fixture, requestId: `smoke-${crypto.randomUUID()}` };
    const live = await call("POST", "/v2/outfits", { token, body: request });
    const violations = live.status === 200 ? checkRecommendation(request, live.json) : [`HTTP ${live.status} ${live.json?.error?.code ?? live.error ?? ""}`];
    record("Live /v2/outfits trả bộ hợp lệ (invariant CT-02)", violations.length === 0, `${live.ms} ms${violations.length ? `; ${violations.join("; ")}` : ""}`);
    if (live.ms > 15_000) console.log(`      CẢNH BÁO: ${live.ms} ms > deadline provider mặc định 15 s; app cắt ở 20 s.`);
  }
} else {
  console.log("      (bỏ qua live — thêm --live để gọi model thật 1 lần)");
}

const failed = results.filter((r) => !r.ok).length;
console.log(`\n${results.length - failed}/${results.length} PASS`);
process.exit(failed === 0 ? 0 : 1);
