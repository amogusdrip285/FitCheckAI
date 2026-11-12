// OWNER: Track B. Helpers HTTP: JSON response và đọc body có giới hạn byte.
import type { z } from "zod";
import { ApiError } from "./errors";

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

/**
 * Đọc JSON body nhưng dừng ngay khi vượt `maxBytes` (không tin Content-Length một mình).
 * Sai content-type / UTF-8 / JSON ⇒ INVALID_REQUEST; vượt cỡ ⇒ PAYLOAD_TOO_LARGE.
 */
export async function readJsonCapped(request: Request, maxBytes: number): Promise<unknown> {
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().startsWith("application/json")) {
    throw new ApiError("INVALID_REQUEST", "Content-Type phải là application/json");
  }
  const declared = request.headers.get("content-length");
  if (declared !== null && Number(declared) > maxBytes) {
    throw new ApiError("PAYLOAD_TOO_LARGE", `Body vượt ${maxBytes} byte`);
  }
  if (request.body === null) throw new ApiError("INVALID_REQUEST", "Thiếu body");

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > maxBytes) {
      await reader.cancel();
      throw new ApiError("PAYLOAD_TOO_LARGE", `Body vượt ${maxBytes} byte`);
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }

  let text: string;
  try {
    text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes);
  } catch {
    throw new ApiError("INVALID_REQUEST", "Body không phải UTF-8 hợp lệ");
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    throw new ApiError("INVALID_REQUEST", "Body không phải JSON hợp lệ");
  }
}

/** Parse theo zod schema; báo lỗi bằng đường dẫn field, không lặp lại giá trị người dùng gửi. */
export function parseOrThrow<S extends z.ZodType>(schema: S, value: unknown): z.infer<S> {
  const result = schema.safeParse(value);
  if (result.success) return result.data;
  const issue = result.error.issues[0];
  const path = issue && issue.path.length > 0 ? issue.path.join(".") : "(root)";
  throw new ApiError("INVALID_REQUEST", `Field không hợp lệ: ${path}`);
}
