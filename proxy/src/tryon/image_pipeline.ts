// OWNER: Track C (Deliverable B — Image Validation, Magic Bytes, EXIF Orientation & Synthetic Fixture Encoders)
// Pure Web/Uint8Array implementation compatible with both Cloudflare Workers and Node 22 (zero native deps).

import type { SupportedTryOnMime, TryOnErrorCode } from "./types";

export const MAX_IMAGE_DIMENSION_PX = 4096;
export const MAX_DECODED_PIXEL_COUNT = 16 * 1024 * 1024; // 16 MP (~64 MiB RGBA ceiling)
export const MIN_LIVE_VTO_DIMENSION_PX = 64;

const STRICT_BASE64_REGEX = /^[A-Za-z0-9+/]+={0,2}$/;

export interface InspectedImageMetadata {
  mimeType: SupportedTryOnMime;
  byteLength: number;
  width: number;
  height: number;
  /** TIFF/EXIF Orientation tag (0x0112), 1..8. Defaults to 1 (upright). */
  exifOrientation: number;
  /** Display width after applying EXIF orientation (swapped when orientation is 5..8). */
  effectiveWidth: number;
  /** Display height after applying EXIF orientation (swapped when orientation is 5..8). */
  effectiveHeight: number;
  needsOrientationNormalization: boolean;
  pixelCount: number;
}

export interface ImageValidationOptions {
  label: string;
  declaredMimeType?: string;
  strictMimeMatch?: boolean;
  maxBytes: number;
  minDimensionPx?: number;
  maxDimensionPx?: number;
  maxPixels?: number;
}

export type ImageValidationOutcome =
  | { ok: true; bytes: Uint8Array; metadata: InspectedImageMetadata }
  | { ok: false; code: TryOnErrorCode; message: string };

export function decodeStrictBase64(raw: string): Uint8Array | null {
  const trimmed = raw.trim();
  if (trimmed.length === 0 || trimmed.length % 4 !== 0) {
    return null;
  }
  if (!STRICT_BASE64_REGEX.test(trimmed)) {
    return null;
  }
  try {
    const binary = atob(trimmed);
    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      out[i] = binary.charCodeAt(i);
    }
    return out;
  } catch {
    return null;
  }
}

export function encodeBase64(bytes: Uint8Array): string {
  const CHUNK_SIZE = 0x8000;
  const chunks: string[] = [];
  for (let offset = 0; offset < bytes.length; offset += CHUNK_SIZE) {
    const slice = bytes.subarray(offset, Math.min(offset + CHUNK_SIZE, bytes.length));
    let part = "";
    for (let i = 0; i < slice.length; i += 1) {
      part += String.fromCharCode(slice[i]!);
    }
    chunks.push(part);
  }
  return btoa(chunks.join(""));
}

export async function sha256HexBytes(bytes: Uint8Array): Promise<string> {
  const bufferCopy = new Uint8Array(bytes.byteLength);
  bufferCopy.set(bytes);
  const digest = await crypto.subtle.digest("SHA-256", bufferCopy.buffer);
  return (
    "sha256:" +
    Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("")
      .slice(0, 32)
  );
}

function readU16BE(buf: Uint8Array, offset: number): number {
  return ((buf[offset]! << 8) | buf[offset + 1]!) >>> 0;
}

function readU16LE(buf: Uint8Array, offset: number): number {
  return (buf[offset]! | (buf[offset + 1]! << 8)) >>> 0;
}

function readU32BE(buf: Uint8Array, offset: number): number {
  return (
    ((buf[offset]! * 0x1000000) +
      ((buf[offset + 1]! << 16) | (buf[offset + 2]! << 8) | buf[offset + 3]!)) >>>
    0
  );
}

function readU32LE(buf: Uint8Array, offset: number): number {
  return (
    (buf[offset]! |
      (buf[offset + 1]! << 8) |
      (buf[offset + 2]! << 16) |
      (buf[offset + 3]! * 0x1000000)) >>>
    0
  );
}

/**
 * Parses TIFF header (`II\x2a\x00` or `MM\x00\x2a`) and extracts IFD0 tag 0x0112 (Orientation 1..8).
 */
export function parseTiffExifOrientation(tiffBytes: Uint8Array): number {
  if (tiffBytes.length < 8) {
    return 1;
  }
  const isLittleEndian =
    tiffBytes[0] === 0x49 &&
    tiffBytes[1] === 0x49 &&
    tiffBytes[2] === 0x2a &&
    tiffBytes[3] === 0x00;
  const isBigEndian =
    tiffBytes[0] === 0x4d &&
    tiffBytes[1] === 0x4d &&
    tiffBytes[2] === 0x00 &&
    tiffBytes[3] === 0x2a;

  if (!isLittleEndian && !isBigEndian) {
    return 1;
  }

  const u16 = isLittleEndian ? readU16LE : readU16BE;
  const u32 = isLittleEndian ? readU32LE : readU32BE;

  const ifdOffset = u32(tiffBytes, 4);
  if (ifdOffset + 2 > tiffBytes.length) {
    return 1;
  }

  const entryCount = u16(tiffBytes, ifdOffset);
  for (let i = 0; i < entryCount; i += 1) {
    const entryOffset = ifdOffset + 2 + i * 12;
    if (entryOffset + 12 > tiffBytes.length) {
      break;
    }
    const tag = u16(tiffBytes, entryOffset);
    if (tag === 0x0112) {
      const value = u16(tiffBytes, entryOffset + 8);
      if (value >= 1 && value <= 8) {
        return value;
      }
      return 1;
    }
  }
  return 1;
}

function finalizeMetadata(
  mimeType: SupportedTryOnMime,
  byteLength: number,
  width: number,
  height: number,
  exifOrientation: number,
): InspectedImageMetadata {
  const swapped = exifOrientation >= 5 && exifOrientation <= 8;
  const effectiveWidth = swapped ? height : width;
  const effectiveHeight = swapped ? width : height;
  return {
    mimeType,
    byteLength,
    width,
    height,
    exifOrientation,
    effectiveWidth,
    effectiveHeight,
    needsOrientationNormalization: exifOrientation !== 1,
    pixelCount: width * height,
  };
}

/**
 * Inspects binary magic header, structural markers/chunks, dimensions, and EXIF orientation.
 * Returns `null` if the buffer is empty, truncated, corrupted, or not JPEG/PNG/WebP.
 */
export function inspectImageBytes(bytes: Uint8Array): InspectedImageMetadata | null {
  if (!bytes || bytes.length < 24) {
    return null;
  }

  // 1. PNG: 89 50 4E 47 0D 0A 1A 0A + IHDR chunk + IEND trailer check
  if (
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47 &&
    bytes[4] === 0x0d &&
    bytes[5] === 0x0a &&
    bytes[6] === 0x1a &&
    bytes[7] === 0x0a
  ) {
    if (
      bytes.length < 33 ||
      bytes[12] !== 0x49 ||
      bytes[13] !== 0x48 ||
      bytes[14] !== 0x44 ||
      bytes[15] !== 0x52
    ) {
      return null;
    }
    const width = readU32BE(bytes, 16);
    const height = readU32BE(bytes, 20);
    if (width <= 0 || height <= 0) {
      return null;
    }

    let orientation = 1;
    let sawIend = false;
    let offset = 8;
    while (offset + 12 <= bytes.length) {
      const chunkLen = readU32BE(bytes, offset);
      const c0 = bytes[offset + 4];
      const c1 = bytes[offset + 5];
      const c2 = bytes[offset + 6];
      const c3 = bytes[offset + 7];
      const chunkEnd = offset + 12 + chunkLen;
      if (chunkEnd > bytes.length) {
        // Truncated PNG chunk
        return null;
      }
      // eXIf chunk
      if (c0 === 0x65 && c1 === 0x58 && c2 === 0x49 && c3 === 0x66 && chunkLen >= 8) {
        orientation = parseTiffExifOrientation(bytes.subarray(offset + 8, offset + 8 + chunkLen));
      }
      // IEND chunk
      if (c0 === 0x49 && c1 === 0x45 && c2 === 0x4e && c3 === 0x44) {
        sawIend = true;
        break;
      }
      offset = chunkEnd;
    }

    if (!sawIend) {
      return null;
    }

    return finalizeMetadata("image/png", bytes.length, width, height, orientation);
  }

  // 2. JPEG: FF D8 SOI ... SOFn frame + optional APP1 Exif ... FF D9 EOI
  if (bytes[0] === 0xff && bytes[1] === 0xd8) {
    let idx = 2;
    let width = 0;
    let height = 0;
    let orientation = 1;

    while (idx + 1 < bytes.length) {
      if (bytes[idx] !== 0xff) {
        idx += 1;
        continue;
      }
      while (idx < bytes.length && bytes[idx] === 0xff) {
        idx += 1;
      }
      if (idx >= bytes.length) {
        break;
      }
      const marker = bytes[idx]!;
      idx += 1;

      if (marker === 0xd9 || marker === 0xda) {
        // EOI or SOS (scan entropy data starts)
        break;
      }
      if (marker === 0xd8 || marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
        continue;
      }

      if (idx + 2 > bytes.length) {
        return null;
      }
      const segLen = readU16BE(bytes, idx);
      if (segLen < 2 || idx + segLen > bytes.length) {
        return null;
      }

      // APP1 Exif\0\0
      if (marker === 0xe1 && segLen >= 10) {
        if (
          bytes[idx + 2] === 0x45 &&
          bytes[idx + 3] === 0x78 &&
          bytes[idx + 4] === 0x69 &&
          bytes[idx + 5] === 0x66 &&
          bytes[idx + 6] === 0x00 &&
          bytes[idx + 7] === 0x00
        ) {
          orientation = parseTiffExifOrientation(bytes.subarray(idx + 8, idx + segLen));
        }
      }

      // SOF0..SOF15 (excluding DHT 0xC4, JPG 0xC8, DAC 0xCC)
      const isSof =
        marker >= 0xc0 &&
        marker <= 0xcf &&
        marker !== 0xc4 &&
        marker !== 0xc8 &&
        marker !== 0xcc;
      if (isSof) {
        if (segLen < 7) {
          return null;
        }
        height = readU16BE(bytes, idx + 3);
        width = readU16BE(bytes, idx + 5);
      }

      idx += segLen;
    }

    if (width <= 0 || height <= 0) {
      return null;
    }
    return finalizeMetadata("image/jpeg", bytes.length, width, height, orientation);
  }

  // 3. WebP: RIFF....WEBP
  if (
    bytes.length >= 30 &&
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50
  ) {
    let width = 0;
    let height = 0;
    let orientation = 1;
    let offset = 12;

    while (offset + 8 <= bytes.length) {
      const c0 = bytes[offset]!;
      const c1 = bytes[offset + 1]!;
      const c2 = bytes[offset + 2]!;
      const c3 = bytes[offset + 3]!;
      const chunkSize = readU32LE(bytes, offset + 4);
      const dataStart = offset + 8;
      const dataEnd = dataStart + chunkSize;
      if (dataEnd > bytes.length) {
        return null;
      }

      // "VP8 "
      if (c0 === 0x56 && c1 === 0x50 && c2 === 0x38 && c3 === 0x20 && chunkSize >= 10) {
        if (
          bytes[dataStart + 3] === 0x9d &&
          bytes[dataStart + 4] === 0x01 &&
          bytes[dataStart + 5] === 0x2a
        ) {
          width = readU16LE(bytes, dataStart + 6) & 0x3fff;
          height = readU16LE(bytes, dataStart + 8) & 0x3fff;
        }
      }
      // "VP8L"
      else if (c0 === 0x56 && c1 === 0x50 && c2 === 0x38 && c3 === 0x4c && chunkSize >= 5) {
        if (bytes[dataStart] === 0x2f) {
          const b0 = bytes[dataStart + 1]!;
          const b1 = bytes[dataStart + 2]!;
          const b2 = bytes[dataStart + 3]!;
          const b3 = bytes[dataStart + 4]!;
          width = 1 + (((b1 & 0x3f) << 8) | b0);
          height = 1 + (((b3 & 0x0f) << 10) | (b2 << 2) | ((b1 & 0xc0) >> 6));
        }
      }
      // "VP8X"
      else if (c0 === 0x56 && c1 === 0x50 && c2 === 0x38 && c3 === 0x58 && chunkSize >= 10) {
        width =
          1 +
          (bytes[dataStart + 4]! |
            (bytes[dataStart + 5]! << 8) |
            (bytes[dataStart + 6]! << 16));
        height =
          1 +
          (bytes[dataStart + 7]! |
            (bytes[dataStart + 8]! << 8) |
            (bytes[dataStart + 9]! << 16));
      }
      // "EXIF"
      else if (c0 === 0x45 && c1 === 0x58 && c2 === 0x49 && c3 === 0x46 && chunkSize >= 8) {
        const sub = bytes.subarray(dataStart, dataEnd);
        const hasExifHeader =
          sub[0] === 0x45 && sub[1] === 0x78 && sub[2] === 0x69 && sub[3] === 0x66;
        orientation = parseTiffExifOrientation(hasExifHeader ? sub.subarray(6) : sub);
      }

      offset = dataEnd + (chunkSize % 2);
    }

    if (width <= 0 || height <= 0) {
      return null;
    }
    return finalizeMetadata("image/webp", bytes.length, width, height, orientation);
  }

  return null;
}

export function validateTryOnImageBuffer(
  bytes: Uint8Array | null,
  options: ImageValidationOptions,
): ImageValidationOutcome {
  if (!bytes || bytes.length === 0) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Dữ liệu ảnh ${options.label} trống hoặc base64 không hợp lệ.`,
    };
  }
  if (bytes.length > options.maxBytes) {
    return {
      ok: false,
      code: "PAYLOAD_TOO_LARGE",
      message: `Dung lượng ảnh ${options.label} (${bytes.length} bytes) vượt quá giới hạn cho phép (${options.maxBytes} bytes).`,
    };
  }

  const metadata = inspectImageBytes(bytes);
  if (!metadata) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Ảnh ${options.label} bị hỏng hoặc không đúng magic bytes của JPEG/PNG/WebP.`,
    };
  }

  if (options.strictMimeMatch && options.declaredMimeType && options.declaredMimeType !== metadata.mimeType) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Ảnh ${options.label} khai báo '${options.declaredMimeType}' nhưng nội dung thực tế là '${metadata.mimeType}'.`,
    };
  }

  const minDim = options.minDimensionPx ?? 1;
  const maxDim = options.maxDimensionPx ?? MAX_IMAGE_DIMENSION_PX;
  const maxPixels = options.maxPixels ?? MAX_DECODED_PIXEL_COUNT;

  if (
    metadata.effectiveWidth < minDim ||
    metadata.effectiveHeight < minDim ||
    metadata.effectiveWidth > maxDim ||
    metadata.effectiveHeight > maxDim
  ) {
    return {
      ok: false,
      code: "INVALID_REQUEST",
      message: `Kích thước ảnh ${options.label} (${metadata.effectiveWidth}x${metadata.effectiveHeight}px) nằm ngoài giới hạn hỗ trợ (${minDim}..${maxDim}px).`,
    };
  }

  if (metadata.pixelCount > maxPixels) {
    return {
      ok: false,
      code: "PAYLOAD_TOO_LARGE",
      message: `Độ phân giải ảnh ${options.label} vượt trần bộ nhớ giải mã an toàn.`,
    };
  }

  return { ok: true, bytes, metadata };
}

export function extensionForMime(mimeType: SupportedTryOnMime): "jpg" | "png" | "webp" {
  switch (mimeType) {
    case "image/jpeg":
      return "jpg";
    case "image/webp":
      return "webp";
    case "image/png":
    default:
      return "png";
  }
}

// --- Pure CRC32 + Adler32 + Uncompressed Zlib PNG generator for deterministic Mock & Test fixtures ---

const CRC32_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) {
      c = (c & 1) !== 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i += 1) {
    c = CRC32_TABLE[(c ^ bytes[i]!) & 0xff]! ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

function adler32(bytes: Uint8Array): number {
  let a = 1;
  let b = 0;
  const MOD = 65521;
  for (let i = 0; i < bytes.length; i += 1) {
    a = (a + bytes[i]!) % MOD;
    b = (b + a) % MOD;
  }
  return ((b << 16) | a) >>> 0;
}

function writeU32BE(target: Uint8Array, offset: number, value: number): void {
  target[offset] = (value >>> 24) & 0xff;
  target[offset + 1] = (value >>> 16) & 0xff;
  target[offset + 2] = (value >>> 8) & 0xff;
  target[offset + 3] = value & 0xff;
}

function makePngChunk(typeAscii: string, data: Uint8Array): Uint8Array {
  const chunk = new Uint8Array(12 + data.length);
  writeU32BE(chunk, 0, data.length);
  for (let i = 0; i < 4; i += 1) {
    chunk[4 + i] = typeAscii.charCodeAt(i);
  }
  chunk.set(data, 8);
  const crcVal = crc32(chunk.subarray(4, 8 + data.length));
  writeU32BE(chunk, 8 + data.length, crcVal);
  return chunk;
}

/**
 * Generates a valid, standards-compliant 24-bit RGB PNG with an editorial lookbook silhouette
 * so Coil/BitmapFactory on Android can decode & render real multi-hundred-pixel PNG bytes in offline tests.
 */
export function buildLookbookMockPngBytes(params: {
  width?: number;
  height?: number;
  backgroundRgb?: [number, number, number];
  garmentRgb?: [number, number, number];
  trouserRgb?: [number, number, number];
}): Uint8Array {
  const width = params.width ?? 192;
  const height = params.height ?? 256;
  const bg = params.backgroundRgb ?? [238, 231, 220];
  const top = params.garmentRgb ?? [48, 76, 112];
  const bottom = params.trouserRgb ?? [62, 58, 54];

  const rowStride = 1 + width * 3;
  const rawScanlines = new Uint8Array(height * rowStride);

  const cx = width * 0.5;
  const headCy = height * 0.17;
  const headR2 = Math.pow(width * 0.085, 2);

  for (let y = 0; y < height; y += 1) {
    const rowStart = y * rowStride;
    rawScanlines[rowStart] = 0; // Filter type 0 (None)
    for (let x = 0; x < width; x += 1) {
      let r = bg[0];
      let g = bg[1];
      let b = bg[2];

      const dx = x - cx;
      const dyHead = y - headCy;
      const inBorder = x < 4 || x >= width - 4 || y < 4 || y >= height - 4;
      const inHead = dx * dx + dyHead * dyHead <= headR2;
      const inTorso =
        x >= width * 0.27 &&
        x <= width * 0.73 &&
        y >= height * 0.27 &&
        y <= height * 0.57;
      const inTrousers =
        x >= width * 0.33 &&
        x <= width * 0.67 &&
        y > height * 0.57 &&
        y <= height * 0.86 &&
        Math.abs(x - cx) >= width * 0.02;

      if (inBorder) {
        r = 164;
        g = 136;
        b = 108;
      } else if (inHead) {
        r = 196;
        g = 162;
        b = 138;
      } else if (inTorso) {
        r = top[0];
        g = top[1];
        b = top[2];
      } else if (inTrousers) {
        r = bottom[0];
        g = bottom[1];
        b = bottom[2];
      }

      const px = rowStart + 1 + x * 3;
      rawScanlines[px] = r;
      rawScanlines[px + 1] = g;
      rawScanlines[px + 2] = b;
    }
  }

  // Wrap rawScanlines in valid zlib stored (BTYPE=00) DEFLATE blocks (max 65535 bytes/block)
  const MAX_BLOCK = 65535;
  const numBlocks = Math.max(1, Math.ceil(rawScanlines.length / MAX_BLOCK));
  const zlibData = new Uint8Array(2 + numBlocks * 5 + rawScanlines.length + 4);
  zlibData[0] = 0x78; // CMF
  zlibData[1] = 0x01; // FLG (no preset dict, FCHECK valid: (0x78*256 + 0x01) % 31 === 0)

  let zOff = 2;
  for (let bIdx = 0; bIdx < numBlocks; bIdx += 1) {
    const start = bIdx * MAX_BLOCK;
    const end = Math.min(start + MAX_BLOCK, rawScanlines.length);
    const len = end - start;
    const nlen = ~len & 0xffff;
    const isFinal = bIdx === numBlocks - 1 ? 1 : 0;
    zlibData[zOff] = isFinal;
    zlibData[zOff + 1] = len & 0xff;
    zlibData[zOff + 2] = (len >>> 8) & 0xff;
    zlibData[zOff + 3] = nlen & 0xff;
    zlibData[zOff + 4] = (nlen >>> 8) & 0xff;
    zlibData.set(rawScanlines.subarray(start, end), zOff + 5);
    zOff += 5 + len;
  }
  writeU32BE(zlibData, zOff, adler32(rawScanlines));

  const ihdr = new Uint8Array(13);
  writeU32BE(ihdr, 0, width);
  writeU32BE(ihdr, 4, height);
  ihdr[8] = 8; // 8-bit depth
  ihdr[9] = 2; // Truecolour RGB
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const signature = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdrChunk = makePngChunk("IHDR", ihdr);
  const idatChunk = makePngChunk("IDAT", zlibData);
  const iendChunk = makePngChunk("IEND", new Uint8Array(0));

  const out = new Uint8Array(
    signature.length + ihdrChunk.length + idatChunk.length + iendChunk.length,
  );
  let pos = 0;
  out.set(signature, pos);
  pos += signature.length;
  out.set(ihdrChunk, pos);
  pos += ihdrChunk.length;
  out.set(idatChunk, pos);
  pos += idatChunk.length;
  out.set(iendChunk, pos);
  return out;
}

/**
 * Builds a minimal valid JPEG header + SOF0 + optional EXIF Orientation tag + SOS + EOI for unit tests.
 */
export function buildSyntheticJpegHeaderBytes(
  width: number,
  height: number,
  exifOrientation = 1,
): Uint8Array {
  // APP1 Exif\0\0 + Little-Endian TIFF IFD0 with 1 entry (0x0112 Orientation)
  const app1Payload = new Uint8Array([
    0x45, 0x78, 0x69, 0x66, 0x00, 0x00, // "Exif\0\0"
    0x49, 0x49, 0x2a, 0x00, // "II" + 42
    0x08, 0x00, 0x00, 0x00, // IFD0 offset = 8
    0x01, 0x00, // 1 entry
    0x12, 0x01, // Tag 0x0112 (Orientation)
    0x03, 0x00, // Type SHORT (3)
    0x01, 0x00, 0x00, 0x00, // Count = 1
    exifOrientation & 0xff, 0x00, 0x00, 0x00, // Value
    0x00, 0x00, 0x00, 0x00, // Next IFD = 0
  ]);
  const app1Len = app1Payload.length + 2;

  const sof0 = new Uint8Array([
    0xff, 0xc0,
    0x00, 0x11, // length 17
    0x08, // precision
    (height >>> 8) & 0xff, height & 0xff,
    (width >>> 8) & 0xff, width & 0xff,
    0x03, // 3 components
    0x01, 0x11, 0x00,
    0x02, 0x11, 0x00,
    0x03, 0x11, 0x00,
  ]);

  const sosAndEoi = new Uint8Array([
    0xff, 0xda, 0x00, 0x0c, 0x03, 0x01, 0x00, 0x02, 0x11, 0x03, 0x11, 0x00, 0x3f, 0x00,
    0x00, 0xff, 0xd9,
  ]);

  const out = new Uint8Array(2 + 4 + app1Payload.length + sof0.length + sosAndEoi.length);
  let p = 0;
  out[p++] = 0xff;
  out[p++] = 0xd8;
  out[p++] = 0xff;
  out[p++] = 0xe1;
  out[p++] = (app1Len >>> 8) & 0xff;
  out[p++] = app1Len & 0xff;
  out.set(app1Payload, p);
  p += app1Payload.length;
  out.set(sof0, p);
  p += sof0.length;
  out.set(sosAndEoi, p);
  return out;
}

/**
 * Builds a minimal valid VP8X WebP container buffer for unit tests.
 */
export function buildSyntheticWebpHeaderBytes(width: number, height: number): Uint8Array {
  const wMinus1 = width - 1;
  const hMinus1 = height - 1;
  const vp8xChunk = new Uint8Array([
    0x56, 0x50, 0x38, 0x58, // "VP8X"
    0x0a, 0x00, 0x00, 0x00, // chunk size = 10
    0x00, 0x00, 0x00, 0x00, // flags + reserved
    wMinus1 & 0xff, (wMinus1 >>> 8) & 0xff, (wMinus1 >>> 16) & 0xff,
    hMinus1 & 0xff, (hMinus1 >>> 8) & 0xff, (hMinus1 >>> 16) & 0xff,
  ]);
  const riffSize = 4 + vp8xChunk.length;
  const out = new Uint8Array(12 + vp8xChunk.length);
  out.set([0x52, 0x49, 0x46, 0x46], 0); // "RIFF"
  out[4] = riffSize & 0xff;
  out[5] = (riffSize >>> 8) & 0xff;
  out[6] = (riffSize >>> 16) & 0xff;
  out[7] = (riffSize >>> 24) & 0xff;
  out.set([0x57, 0x45, 0x42, 0x50], 8); // "WEBP"
  out.set(vp8xChunk, 12);
  return out;
}
