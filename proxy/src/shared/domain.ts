// OWNER: Track B (wire schema). Enum phải trùng CONTRACTS.md v2 CT-01/02/05; đổi ở đây = đổi contract.
import { z } from "zod";

export const CATEGORIES = ["TOP", "BOTTOM", "DRESS", "OUTERWEAR", "SHOES", "ACCESSORY", "UNKNOWN"] as const;
export type Category = (typeof CATEGORIES)[number];

export const COLORS = [
  "BLACK",
  "WHITE",
  "GRAY",
  "BEIGE",
  "BROWN",
  "BLUE",
  "GREEN",
  "RED",
  "YELLOW",
  "PINK",
  "PURPLE",
  "OTHER",
] as const;
export type Color = (typeof COLORS)[number];

export const PATTERNS = ["SOLID", "STRIPED", "CHECKED", "PRINTED", "OTHER", "UNKNOWN"] as const;
export type Pattern = (typeof PATTERNS)[number];

export const OCCASIONS = ["CASUAL", "SCHOOL", "WORK", "DATE", "PARTY", "SPORT", "FORMAL_EVENT"] as const;
export type Occasion = (typeof OCCASIONS)[number];

export const VIEWS = ["FRONT", "BACK"] as const;
export type View = (typeof VIEWS)[number];

export const ATTRIBUTE_FIELDS = [
  "category",
  "colors",
  "pattern",
  "styleTags",
  "material",
  "formality",
  "seasonTags",
] as const;
export type AttributeField = (typeof ATTRIBUTE_FIELDS)[number];

export type Source = "AI_LIVE" | "FAKE";

/** ID trên wire: UUID khi triển khai; cho phép ID ngắn chữ-số để fixture dễ đọc. Không có "+" (dùng làm separator signature). */
export const idSchema = z.string().regex(/^[A-Za-z0-9_-]{1,64}$/);

/** Tag ngắn (style/season): 1–32 ký tự sau trim. */
export const shortTagSchema = z.string().trim().min(1).max(32);

export const LIMITS = {
  maxItems: 50,
  maxOutfits: 5,
  maxColors: 3,
  maxStyleTags: 5,
  maxSeasonTags: 4,
  maxMaterialChars: 40,
  maxFreeTextChars: 500,
  maxExplanationChars: 500,
  outfitsBodyBytes: 64 * 1024,
  recognitionBodyBytes: 4 * 1024 * 1024,
  recognitionImageBytes: 1024 * 1024,
} as const;
