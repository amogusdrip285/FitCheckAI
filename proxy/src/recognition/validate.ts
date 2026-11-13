// OWNER: Track B. Chuẩn hóa output recognition thành RecognitionResult CT-05.
// Không bao giờ tự lưu: kết quả chỉ là gợi ý; field nghi ngờ/mâu thuẫn được đánh dấu cần review.
import {
  ATTRIBUTE_FIELDS,
  type AttributeField,
  type Category,
  type Color,
  LIMITS,
  type Pattern,
  SEASONS,
  type Season,
  type View,
} from "../shared/domain";
import { garmentTypesById, type ModelRecognition } from "./schema";

export interface SuggestedAttributes {
  category: Category;
  colors: Color[];
  pattern: Pattern;
  styleTags: string[];
  material: string | null;
  formality: number | null;
  seasonTags: Season[];
  garmentType?: string | null;
}

export type RecognitionWarning = "VIEWS_LOOK_LIKE_DIFFERENT_ITEMS" | "CATEGORY_UNKNOWN";

export interface RecognitionResultDto {
  requestId: string;
  itemId: string;
  inputRevision: number;
  suggestedAttributes: SuggestedAttributes;
  needsReviewFields: AttributeField[];
  viewEvidence: Partial<Record<AttributeField, View[]>>;
  warnings: RecognitionWarning[];
  source: "AI_LIVE" | "FAKE";
}

function cleanTags(tags: readonly string[], max: number): { tags: string[]; trimmed: boolean } {
  const out: string[] = [];
  let trimmed = false;
  for (const raw of tags) {
    const tag = raw.trim().toLowerCase();
    if (tag.length === 0 || tag.length > 32) {
      trimmed = true;
      continue;
    }
    if (out.includes(tag)) continue;
    if (out.length >= max) {
      trimmed = true;
      continue;
    }
    out.push(tag);
  }
  return { tags: out, trimmed };
}

export function buildRecognitionResult(
  output: ModelRecognition,
  meta: { requestId: string; itemId: string; inputRevision: number; hasBack: boolean; source: "AI_LIVE" | "FAKE"; detailed: boolean },
): RecognitionResultDto {
  const review = new Set<AttributeField>();
  const warnings: RecognitionWarning[] = [];
  const a = output.attributes;

  const colors = [...new Set(a.colors)].slice(0, LIMITS.maxColors);
  if (a.colors.length > LIMITS.maxColors) review.add("colors");

  let material = a.material?.trim() ?? null;
  if (material !== null && (material.length === 0 || material.length > LIMITS.maxMaterialChars)) {
    material = null;
    review.add("material");
  }

  let formality = a.formality;
  if (formality !== null && (!Number.isInteger(formality) || formality < 1 || formality > 5)) {
    formality = null;
    review.add("formality");
  }

  const style = cleanTags(a.styleTags, LIMITS.maxStyleTags);
  if (style.trimmed) review.add("styleTags");
  // Mùa là enum (không lowercase); bỏ trùng, giữ thứ tự cố định SPRING→WINTER.
  const seasons = SEASONS.filter((s) => a.seasonTags.includes(s));

  if (a.category === "UNKNOWN") {
    review.add("category");
    warnings.push("CATEGORY_UNKNOWN");
  }
  if (a.pattern === "UNKNOWN") review.add("pattern");

  for (const field of output.uncertainFields) review.add(field);
  if (meta.hasBack) {
    for (const field of output.conflictFields) review.add(field);
    // Hai ảnh có vẻ khác món: không gộp im lặng — cảnh báo và yêu cầu review toàn bộ.
    if (output.sameItem === false) {
      warnings.push("VIEWS_LOOK_LIKE_DIFFERENT_ITEMS");
      for (const field of ATTRIBUTE_FIELDS) review.add(field);
    }
  }

  const viewEvidence: Partial<Record<AttributeField, View[]>> = {};
  for (const entry of output.evidence) {
    const views = [...new Set(entry.views)].filter((v) => meta.hasBack || v === "FRONT");
    if (views.length === 0) continue;
    const merged = new Set([...(viewEvidence[entry.field] ?? []), ...views]);
    viewEvidence[entry.field] = (["FRONT", "BACK"] as const).filter((v) => merged.has(v));
  }

  const type = "garmentType" in a ? a.garmentType : null;
  const garmentType = meta.detailed && type && garmentTypesById.get(type)?.category === a.category &&
    viewEvidence.garmentType && !review.has("garmentType") && !(meta.hasBack && output.sameItem === false)
    ? type : null;
  if (garmentType === null) {
    delete viewEvidence.garmentType;
    if (meta.detailed) review.add("garmentType");
    else review.delete("garmentType");
  }

  return {
    requestId: meta.requestId,
    itemId: meta.itemId,
    inputRevision: meta.inputRevision,
    suggestedAttributes: {
      category: a.category,
      colors,
      pattern: a.pattern,
      styleTags: style.tags,
      material,
      formality,
      seasonTags: seasons,
      ...(meta.detailed ? { garmentType } : {}),
    },
    needsReviewFields: ATTRIBUTE_FIELDS.filter((f) => review.has(f)),
    viewEvidence,
    warnings,
    source: meta.source,
  };
}
