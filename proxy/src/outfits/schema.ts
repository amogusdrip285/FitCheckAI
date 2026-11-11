// OWNER: Track B. Wire schema POST /v2/outfits — CONTRACTS.md v2 CT-02.
// `.strict()` toàn bộ: field lạ (vd. weightKg, ảnh, local path) bị từ chối thay vì âm thầm gửi cho LLM.
import { z } from "zod";
import { CATEGORIES, COLORS, LIMITS, OCCASIONS, PATTERNS, idSchema, shortTagSchema } from "../shared/domain";

export const ItemAttributesSchema = z
  .object({
    category: z.enum(CATEGORIES),
    colors: z.array(z.enum(COLORS)).max(LIMITS.maxColors).default([]),
    pattern: z.enum(PATTERNS).nullable().default(null),
    styleTags: z.array(shortTagSchema).max(LIMITS.maxStyleTags).default([]),
    material: z.string().trim().min(1).max(LIMITS.maxMaterialChars).nullable().default(null),
    formality: z.number().int().min(1).max(5).nullable().default(null),
    seasonTags: z.array(shortTagSchema).max(LIMITS.maxSeasonTags).default([]),
  })
  .strict();

export const ItemSummarySchema = z
  .object({
    id: idSchema,
    revision: z.number().int().min(1),
    attributes: ItemAttributesSchema,
  })
  .strict();

/** Projection mặc định CT-04: chỉ chiều cao + style + category ưa thích. Không cân nặng/ảnh/UUID người dùng. */
export const ProfileProjectionSchema = z
  .object({
    heightCm: z.number().positive().max(300).nullable().default(null),
    stylePreferences: z.array(shortTagSchema).max(5).default([]),
    preferredCategories: z.array(z.enum(CATEGORIES)).max(CATEGORIES.length).default([]),
  })
  .strict();

export const WeatherSnapshotSchema = z
  .object({
    cityLabel: z.string().trim().min(1).max(64),
    tempC: z.number().min(-60).max(60),
    condition: z.string().trim().min(1).max(64),
    validAt: z.number().int().nonnegative(),
    fetchedAt: z.number().int().nonnegative(),
    source: z.string().trim().min(1).max(64),
    isStale: z.boolean(),
  })
  .strict();

export const OutfitRequestSchema = z
  .object({
    schemaVersion: z.literal(2),
    requestId: idSchema,
    wardrobeSnapshotId: idSchema,
    items: z.array(ItemSummarySchema).min(1).max(LIMITS.maxItems),
    profile: ProfileProjectionSchema.nullable(),
    occasion: z.enum(OCCASIONS),
    styleOverrides: z.array(shortTagSchema).max(5).default([]),
    freeText: z.string().max(LIMITS.maxFreeTextChars).default(""),
    weather: WeatherSnapshotSchema.nullable(),
    requestedCount: z.number().int().min(1).max(LIMITS.maxOutfits),
    focusItemId: idSchema.nullable().default(null),
    excludeOutfitSignatures: z.array(z.string().min(1).max(65 * 8)).max(5).default([]),
    regenerationOfRequestId: idSchema.nullable().default(null),
  })
  .strict();

export type OutfitRequest = z.infer<typeof OutfitRequestSchema>;
export type ItemSummary = z.infer<typeof ItemSummarySchema>;

/**
 * Output bắt model trả (structured output). Cố ý không đặt min/max ở đây:
 * mọi ràng buộc số lượng/ID/slot do semantic validator kiểm, để output sai bị loại có đếm lý do.
 */
export const ModelOutfitsSchema = z.object({
  outfits: z.array(
    z.object({
      itemIds: z.array(z.string()),
      explanation: z.string(),
    }),
  ),
  shortfallReason: z.enum(["NOT_ENOUGH_DISTINCT_COMBINATIONS", "CONSTRAINTS_TOO_STRICT"]).nullable(),
});

export type ModelOutfits = z.infer<typeof ModelOutfitsSchema>;

/** Outfit trả cho client — CT-02. */
export interface OutfitDto {
  id: string;
  requestId: string;
  wardrobeSnapshotId: string;
  items: Array<{ itemId: string; revision: number }>;
  explanation: string;
  source: "AI_LIVE" | "FAKE";
  createdAt: number;
  signature: string;
}

export interface RecommendationResultDto {
  requestId: string;
  outfits: OutfitDto[];
  shortfallReason: string | null;
}
