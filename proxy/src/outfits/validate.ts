// OWNER: Track B. Semantic validator cho recommendation — chặn mọi output sai trước khi tới client.
import type { Category } from "../shared/domain";
import { LIMITS } from "../shared/domain";
import type { ItemSummary, ModelOutfits, OutfitRequest } from "./schema";

/** Signature canonical: ID sắp xếp tăng dần nối bằng "|" (khớp outfitSignature() trong core/model của D). Client tính giống hệt để gửi excludeOutfitSignatures. */
export function outfitSignature(itemIds: readonly string[]): string {
  return [...itemIds].sort().join("|");
}

export function parseSignature(signature: string): string[] {
  return signature.split("|").filter((s) => s.length > 0);
}

/**
 * Luật slot baseline CT-02: (TOP+BOTTOM) hoặc DRESS; thêm tối đa 1 OUTERWEAR/SHOES/ACCESSORY mỗi loại;
 * không DRESS kèm TOP/BOTTOM; không UNKNOWN; không bắt buộc giày.
 */
export function slotsValid(categories: readonly Category[]): boolean {
  const count = (c: Category) => categories.filter((x) => x === c).length;
  if (count("UNKNOWN") > 0) return false;
  for (const c of ["TOP", "BOTTOM", "DRESS", "OUTERWEAR", "SHOES", "ACCESSORY"] as const) {
    if (count(c) > 1) return false;
  }
  if (count("DRESS") === 1) return count("TOP") === 0 && count("BOTTOM") === 0;
  return count("TOP") === 1 && count("BOTTOM") === 1;
}

/** Tủ có thể tạo ít nhất một bộ hợp lệ không (kiểm trước khi tốn tiền gọi AI). */
export function wardrobeCanFormOutfit(items: readonly ItemSummary[], focusItemId: string | null): boolean {
  const has = (c: Category) => items.some((i) => i.attributes.category === c);
  const basic = has("DRESS") || (has("TOP") && has("BOTTOM"));
  if (!basic) return false;
  if (focusItemId === null) return true;
  const focus = items.find((i) => i.id === focusItemId);
  if (focus === undefined) return false;
  switch (focus.attributes.category) {
    case "DRESS":
      return true;
    case "TOP":
      return has("BOTTOM");
    case "BOTTOM":
      return has("TOP");
    case "UNKNOWN":
      return false;
    default:
      return basic;
  }
}

const ADDON_CATEGORIES = ["OUTERWEAR", "SHOES", "ACCESSORY"] as const;

/** Liệt kê lười các bộ hợp lệ theo luật slot (core × tuỳ chọn mỗi loại phụ kiện), dừng được bất cứ lúc nào. */
function* validCombinations(items: readonly ItemSummary[]): Generator<string[]> {
  const of = (c: Category) => items.filter((i) => i.attributes.category === c).map((i) => i.id);
  const cores = [...of("TOP").flatMap((t) => of("BOTTOM").map((b) => [t, b])), ...of("DRESS").map((d) => [d])];
  const addonChoices = ADDON_CATEGORIES.map((c) => [null, ...of(c)]);
  for (const core of cores) {
    for (const outer of addonChoices[0]!) {
      for (const shoes of addonChoices[1]!) {
        for (const accessory of addonChoices[2]!) {
          yield [...core, ...[outer, shoes, accessory].filter((x): x is string => x !== null)];
        }
      }
    }
  }
}

/**
 * Còn ít nhất một bộ hợp lệ chứa focus và KHÔNG nằm trong excluded (C-102/LATER-006) không.
 * Kiểm trước khi gọi AI: reroll khi mọi tổ hợp đã bị loại ⇒ 409, không tốn tiền.
 * Dừng ngay khi tìm được một bộ; excluded ≤5 nên thường chỉ duyệt vài tổ hợp.
 */
export function hasAvailableOutfit(
  items: readonly ItemSummary[],
  focusItemId: string | null,
  excludeOutfitSignatures: readonly string[],
): boolean {
  const excluded = new Set(excludeOutfitSignatures.map((s) => outfitSignature(parseSignature(s))));
  for (const combo of validCombinations(items)) {
    if (focusItemId !== null && !combo.includes(focusItemId)) continue;
    if (!excluded.has(outfitSignature(combo))) return true;
  }
  return false;
}

export type RejectReason =
  | "EMPTY"
  | "UNKNOWN_ITEM"
  | "DUPLICATE_ITEM"
  | "INVALID_SLOTS"
  | "MISSING_FOCUS"
  | "EXCLUDED_SET"
  | "DUPLICATE_SET"
  | "BAD_EXPLANATION"
  | "OVER_REQUESTED_COUNT";

export interface AcceptedOutfit {
  itemIds: string[];
  explanation: string;
  signature: string;
}

export interface SelectionResult {
  accepted: AcceptedOutfit[];
  rejected: Partial<Record<RejectReason, number>>;
}

/**
 * Giữ các outfit hợp lệ theo thứ tự model trả, tối đa requestedCount; đếm lý do loại.
 * Không sửa nội dung outfit (không tự bỏ món sai rồi giữ phần còn lại) — sai là loại cả bộ.
 */
export function selectValidOutfits(output: ModelOutfits, request: OutfitRequest): SelectionResult {
  const byId = new Map(request.items.map((item) => [item.id, item] as const));
  const excluded = new Set(request.excludeOutfitSignatures.map((s) => outfitSignature(parseSignature(s))));
  const seen = new Set<string>();
  const accepted: AcceptedOutfit[] = [];
  const rejected: Partial<Record<RejectReason, number>> = {};
  const reject = (reason: RejectReason) => {
    rejected[reason] = (rejected[reason] ?? 0) + 1;
  };

  for (const candidate of output.outfits) {
    const ids = candidate.itemIds.map((id) => id.trim());
    if (ids.length === 0) {
      reject("EMPTY");
      continue;
    }
    if (new Set(ids).size !== ids.length) {
      reject("DUPLICATE_ITEM");
      continue;
    }
    const items = ids.map((id) => byId.get(id));
    if (items.some((item) => item === undefined)) {
      reject("UNKNOWN_ITEM");
      continue;
    }
    if (!slotsValid(items.map((item) => (item as ItemSummary).attributes.category))) {
      reject("INVALID_SLOTS");
      continue;
    }
    if (request.focusItemId !== null && !ids.includes(request.focusItemId)) {
      reject("MISSING_FOCUS");
      continue;
    }
    const signature = outfitSignature(ids);
    if (excluded.has(signature)) {
      reject("EXCLUDED_SET");
      continue;
    }
    if (seen.has(signature)) {
      reject("DUPLICATE_SET");
      continue;
    }
    const explanation = candidate.explanation.trim();
    if (explanation.length === 0 || explanation.length > LIMITS.maxExplanationChars) {
      reject("BAD_EXPLANATION");
      continue;
    }
    if (accepted.length >= request.requestedCount) {
      reject("OVER_REQUESTED_COUNT");
      continue;
    }
    seen.add(signature);
    accepted.push({ itemIds: ids, explanation, signature });
  }
  return { accepted, rejected };
}
