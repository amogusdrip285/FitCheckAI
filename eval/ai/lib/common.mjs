// OWNER: Track B. Tiện ích dùng chung cho eval: đọc tham số, session, gọi proxy có đo latency.
// Không ghi API key/token/ảnh vào kết quả.
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const EVAL_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

export function parseArgs(argv) {
  const args = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith("--")) {
      args._.push(a);
      continue;
    }
    const [key, inline] = a.slice(2).split("=", 2);
    if (inline !== undefined) args[key] = inline;
    else if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) args[key] = argv[++i];
    else args[key] = true;
  }
  return args;
}

export function requireConfig(args) {
  const baseUrl = String(args["base-url"] ?? process.env.FITCHECK_PROXY_URL ?? "").replace(/\/+$/, "");
  const accessCode = process.env.FITCHECK_ACCESS_CODE ?? "";
  if (!baseUrl) throw new Error("Thiếu --base-url hoặc biến FITCHECK_PROXY_URL (vd. http://127.0.0.1:8787)");
  if (!accessCode) throw new Error("Thiếu biến môi trường FITCHECK_ACCESS_CODE (mã DEMO_ACCESS_CODE của proxy)");
  return { baseUrl, accessCode };
}

export async function openSession({ baseUrl, accessCode }) {
  const res = await fetch(`${baseUrl}/v2/session`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ accessCode }),
  });
  if (res.status !== 200) throw new Error(`Không lấy được session token: HTTP ${res.status} ${await res.text()}`);
  const { token } = await res.json();
  return token;
}

export async function health(baseUrl) {
  const res = await fetch(`${baseUrl}/health`);
  return res.json();
}

/** POST có đo thời gian; trả {status, body, latencyMs}. Body lỗi giữ nguyên envelope. */
export async function postJson(baseUrl, token, route, payload, timeoutMs = 20_000) {
  const started = performance.now();
  const res = await fetch(`${baseUrl}${route}`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(timeoutMs),
  });
  const latencyMs = Math.round(performance.now() - started);
  let body;
  try {
    body = await res.json();
  } catch {
    body = null;
  }
  return { status: res.status, body, latencyMs };
}

export function newId(prefix) {
  return `${prefix}-${crypto.randomUUID()}`;
}

export function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-").slice(0, 23);
}

export function writeResults(name, markdown, json) {
  // FITCHECK_EVAL_RESULTS_DIR: ghi report ra chỗ khác (vd. lần chạy thử không muốn commit).
  const dir = process.env.FITCHECK_EVAL_RESULTS_DIR ? path.resolve(process.env.FITCHECK_EVAL_RESULTS_DIR) : path.join(EVAL_DIR, "results");
  mkdirSync(dir, { recursive: true });
  const base = path.join(dir, `${timestamp()}-${name}`);
  writeFileSync(`${base}.md`, markdown, "utf8");
  writeFileSync(`${base}.json`, `${JSON.stringify(json, null, 2)}\n`, "utf8");
  return base;
}

export function mdEscape(text) {
  return String(text ?? "").replace(/\|/g, "\\|").replace(/\r?\n/g, " ");
}
