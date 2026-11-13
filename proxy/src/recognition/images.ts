// OWNER: Track B. Kiểm ảnh garment trước khi upload provider: base64 hợp lệ, cỡ decoded, magic bytes, hash.
import { ApiError } from "../shared/errors";
import type { ImageMediaType } from "../shared/model";
import type { GarmentImageInput } from "./schema";

export interface CheckedImage {
  view: "FRONT" | "BACK";
  mediaId: string;
  mediaType: ImageMediaType;
  base64: string;
  sha256: string;
  byteLength: number;
}

const BASE64_RE = /^[A-Za-z0-9+/]*={0,2}$/;

export function sniffImageType(bytes: Uint8Array): ImageMediaType | null {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  if (
    bytes.length >= 8 &&
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47 &&
    bytes[4] === 0x0d &&
    bytes[5] === 0x0a &&
    bytes[6] === 0x1a &&
    bytes[7] === 0x0a
  ) {
    return "image/png";
  }
  if (
    bytes.length >= 12 &&
    String.fromCharCode(bytes[0]!, bytes[1]!, bytes[2]!, bytes[3]!) === "RIFF" &&
    String.fromCharCode(bytes[8]!, bytes[9]!, bytes[10]!, bytes[11]!) === "WEBP"
  ) {
    return "image/webp";
  }
  return null;
}

export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function decodeBase64(text: string): Uint8Array | null {
  if (text.length % 4 !== 0 || !BASE64_RE.test(text)) return null;
  try {
    const binary = atob(text);
    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
    return out;
  } catch {
    return null;
  }
}

export async function checkImage(input: GarmentImageInput, maxDecodedBytes: number): Promise<CheckedImage> {
  const label = input.view === "FRONT" ? "front" : "back";
  // Chặn sớm theo độ dài base64 trước khi decode (4 ký tự ≈ 3 byte).
  if (Math.floor((input.dataBase64.length * 3) / 4) > maxDecodedBytes + 2) {
    throw new ApiError("PAYLOAD_TOO_LARGE", `Ảnh ${label} vượt ${maxDecodedBytes} byte`);
  }
  const bytes = decodeBase64(input.dataBase64);
  if (bytes === null) throw new ApiError("INVALID_REQUEST", `Ảnh ${label} không phải base64 hợp lệ`);
  if (bytes.byteLength > maxDecodedBytes) {
    throw new ApiError("PAYLOAD_TOO_LARGE", `Ảnh ${label} vượt ${maxDecodedBytes} byte`);
  }
  const sniffed = sniffImageType(bytes);
  if (sniffed === null || sniffed !== input.mimeType) {
    throw new ApiError("INVALID_REQUEST", `Ảnh ${label} không khớp mimeType`);
  }
  const sha256 = await sha256Hex(bytes);
  if (input.sha256 !== undefined && input.sha256 !== sha256) {
    throw new ApiError("INVALID_REQUEST", `sha256 của ảnh ${label} không khớp`);
  }
  return {
    view: input.view,
    mediaId: input.mediaId,
    mediaType: sniffed,
    base64: input.dataBase64,
    sha256,
    byteLength: bytes.byteLength,
  };
}
