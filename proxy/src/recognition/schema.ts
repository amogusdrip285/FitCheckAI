// OWNER: Track B. Wire schema POST /v2/recognition — CONTRACTS.md v2 CT-05.
import { z } from "zod";
import { ATTRIBUTE_FIELDS, CATEGORIES, COLORS, PATTERNS, VIEWS, idSchema } from "../shared/domain";

export const IMAGE_MIME_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;

/** Ảnh trên wire: chỉ bytes base64 + metadata. Không nhận URL từ client. */
export const GarmentImageInputSchema = z
  .object({
    view: z.enum(VIEWS),
    mediaId: idSchema,
    mimeType: z.enum(IMAGE_MIME_TYPES),
    sha256: z
      .string()
      .regex(/^[a-f0-9]{64}$/)
      .optional(),
    dataBase64: z.string().min(1),
  })
  .strict();

/** ConsentGrant CT-03 cho mục đích RECOGNITION; server kiểm chứ không tin một boolean vĩnh viễn. */
export const ConsentGrantSchema = z
  .object({
    grantId: idSchema,
    purpose: z.string().max(32),
    providerConfigVersion: z.string().max(64).optional(),
    mediaIds: z.array(idSchema).max(2),
    issuedAt: z.number().int().nonnegative(),
    revokedAt: z.number().int().nonnegative().nullable(),
    userConfirmed: z.boolean(),
  })
  .strict();

export const RecognitionRequestSchema = z
  .object({
    schemaVersion: z.literal(2),
    requestId: idSchema,
    itemId: idSchema,
    inputRevision: z.number().int().min(1),
    front: GarmentImageInputSchema,
    back: GarmentImageInputSchema.nullable().default(null),
    consentGrant: ConsentGrantSchema.nullable(),
  })
  .strict();

export type RecognitionRequest = z.infer<typeof RecognitionRequestSchema>;
export type GarmentImageInput = z.infer<typeof GarmentImageInputSchema>;

/** Output bắt model trả. Giới hạn số lượng/độ dài do validator áp, không đặt ở schema. */
export const ModelRecognitionSchema = z.object({
  sameItem: z.boolean().nullable(),
  attributes: z.object({
    category: z.enum(CATEGORIES),
    colors: z.array(z.enum(COLORS)),
    pattern: z.enum(PATTERNS),
    styleTags: z.array(z.string()),
    material: z.string().nullable(),
    formality: z.number().int().nullable(),
    seasonTags: z.array(z.string()),
  }),
  evidence: z.array(z.object({ field: z.enum(ATTRIBUTE_FIELDS), views: z.array(z.enum(VIEWS)) })),
  conflictFields: z.array(z.enum(ATTRIBUTE_FIELDS)),
  uncertainFields: z.array(z.enum(ATTRIBUTE_FIELDS)),
});

export type ModelRecognition = z.infer<typeof ModelRecognitionSchema>;
