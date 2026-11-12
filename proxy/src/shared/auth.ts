// OWNER: Track B. Session token ngắn hạn ký HMAC-SHA256 cho demo kín.
// Không dùng device ID do client tự khai làm quyền truy cập. Public beta cần BACK-011.

const encoder = new TextEncoder();
const TOKEN_VERSION = "v1";

export interface Session {
  sessionId: string;
  expiresAt: number; // epochMillis
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [
    "sign",
    "verify",
  ]);
}

function toBase64Url(bytes: ArrayBuffer | Uint8Array): string {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binary = "";
  for (const b of view) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64Url(text: string): Uint8Array | null {
  if (!/^[A-Za-z0-9_-]+$/.test(text)) return null;
  const padded = text.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (text.length % 4)) % 4);
  try {
    const binary = atob(padded);
    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
    return out;
  } catch {
    return null;
  }
}

export async function issueSessionToken(
  secret: string,
  ttlSeconds: number,
  nowMs: number,
  sessionId: string = crypto.randomUUID(),
): Promise<{ token: string; session: Session }> {
  const expSec = Math.floor(nowMs / 1000) + ttlSeconds;
  const payload = `${TOKEN_VERSION}.${sessionId}.${expSec}`;
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), encoder.encode(payload));
  return { token: `${payload}.${toBase64Url(sig)}`, session: { sessionId, expiresAt: expSec * 1000 } };
}

/** Trả session nếu chữ ký đúng (so sánh constant-time qua subtle.verify) và chưa hết hạn. */
export async function verifySessionToken(secret: string, token: string, nowMs: number): Promise<Session | null> {
  const parts = token.split(".");
  if (parts.length !== 4) return null;
  const [version, sessionId, expRaw, sigRaw] = parts as [string, string, string, string];
  if (version !== TOKEN_VERSION || !/^[A-Za-z0-9-]{1,64}$/.test(sessionId) || !/^\d{1,12}$/.test(expRaw)) return null;
  const sig = fromBase64Url(sigRaw);
  if (sig === null) return null;
  const ok = await crypto.subtle.verify(
    "HMAC",
    await hmacKey(secret),
    sig,
    encoder.encode(`${version}.${sessionId}.${expRaw}`),
  );
  if (!ok) return null;
  const expiresAt = Number(expRaw) * 1000;
  if (expiresAt <= nowMs) return null;
  return { sessionId, expiresAt };
}

/** So sánh mã truy cập constant-time: ký mã mong đợi rồi verify mã nhận được với chữ ký đó. */
export async function accessCodeMatches(secret: string, provided: string, expected: string): Promise<boolean> {
  const key = await hmacKey(secret);
  const expectedSig = await crypto.subtle.sign("HMAC", key, encoder.encode(`access.${expected}`));
  return crypto.subtle.verify("HMAC", key, expectedSig, encoder.encode(`access.${provided}`));
}

export function bearerToken(request: Request): string | null {
  const header = request.headers.get("authorization");
  if (header === null) return null;
  const match = /^Bearer\s+(\S+)$/i.exec(header.trim());
  return match?.[1] ?? null;
}
