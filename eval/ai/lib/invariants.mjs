// OWNER: Track B. Bộ kiểm invariant ĐỘC LẬP với validator của proxy (không import code server),
// để eval bắt được cả lỗi của chính validator. Luật theo CONTRACTS.md v2 CT-02.

export function outfitSignature(itemIds) {
  return [...itemIds].sort().join("+");
}

const ADDON = ["OUTERWEAR", "SHOES", "ACCESSORY"];

export function slotsValid(categories) {
  const count = (c) => categories.filter((x) => x === c).length;
  if (count("UNKNOWN") > 0 || categories.some((c) => c === undefined)) return false;
  for (const c of ["TOP", "BOTTOM", "DRESS", ...ADDON]) if (count(c) > 1) return false;
  if (count("DRESS") === 1) return count("TOP") === 0 && count("BOTTOM") === 0;
  return count("TOP") === 1 && count("BOTTOM") === 1;
}

/**
 * Kiểm response 200 của /v2/outfits so với request đã gửi.
 * Trả danh sách vi phạm (rỗng = PASS). Không chấm chất lượng giải thích — phần đó do reviewer.
 */
export function checkRecommendation(request, body) {
  const violations = [];
  const add = (msg) => violations.push(msg);
  if (body === null || typeof body !== "object") return ["Body không phải JSON object"];
  if (body.requestId !== request.requestId) add("requestId không khớp request");
  if (!Array.isArray(body.outfits)) return [...violations, "Thiếu mảng outfits"];

  const outfits = body.outfits;
  if (outfits.length === 0) add("Success với danh sách rỗng (phải là lỗi có kiểu)");
  if (outfits.length > request.requestedCount) add(`Trả ${outfits.length} > requestedCount ${request.requestedCount}`);
  if (outfits.length < request.requestedCount && !body.shortfallReason) add("Trả ít hơn yêu cầu nhưng thiếu shortfallReason");
  if (outfits.length === request.requestedCount && body.shortfallReason) add("Đủ số bộ nhưng vẫn có shortfallReason");

  const items = new Map(request.items.map((i) => [i.id, i]));
  const excluded = new Set((request.excludeOutfitSignatures ?? []).map((s) => outfitSignature(s.split("+"))));
  const seenSignatures = new Set();
  const seenIds = new Set();

  outfits.forEach((o, idx) => {
    const tag = `outfit[${idx}]`;
    if (typeof o.id !== "string" || o.id.length === 0) add(`${tag}: thiếu id`);
    else if (seenIds.has(o.id)) add(`${tag}: trùng outfit id`);
    seenIds.add(o.id);
    if (o.requestId !== request.requestId) add(`${tag}: requestId sai`);
    if (o.wardrobeSnapshotId !== request.wardrobeSnapshotId) add(`${tag}: wardrobeSnapshotId sai`);
    if (!["AI_LIVE", "RULES", "FAKE"].includes(o.source)) add(`${tag}: source lạ ${o.source}`);
    if (typeof o.explanation !== "string" || o.explanation.trim().length === 0 || o.explanation.length > 500) {
      add(`${tag}: explanation rỗng hoặc >500 ký tự`);
    }
    const refs = Array.isArray(o.items) ? o.items : [];
    const ids = refs.map((r) => r.itemId);
    if (new Set(ids).size !== ids.length) add(`${tag}: trùng món trong bộ`);
    for (const ref of refs) {
      const item = items.get(ref.itemId);
      if (item === undefined) add(`${tag}: itemId ${ref.itemId} không thuộc tủ`);
      else if (item.revision !== ref.revision) add(`${tag}: revision của ${ref.itemId} sai`);
    }
    const categories = ids.map((id) => items.get(id)?.attributes.category);
    if (!slotsValid(categories)) add(`${tag}: sai luật slot (${categories.join(",")})`);
    if (request.focusItemId && !ids.includes(request.focusItemId)) add(`${tag}: thiếu món focus`);
    const signature = outfitSignature(ids);
    if (o.signature !== signature) add(`${tag}: signature sai (${o.signature} ≠ ${signature})`);
    if (seenSignatures.has(signature)) add(`${tag}: trùng tập món với bộ khác`);
    if (excluded.has(signature)) add(`${tag}: trả lại bộ đã bị loại trừ`);
    seenSignatures.add(signature);
  });
  return violations;
}

/** Kiểm envelope lỗi CT-07. */
export function checkErrorEnvelope(body) {
  const e = body?.error;
  if (!e) return ["Thiếu error envelope"];
  const v = [];
  if (typeof e.code !== "string") v.push("error.code thiếu");
  if (typeof e.message !== "string") v.push("error.message thiếu");
  if (typeof e.requestId !== "string") v.push("error.requestId thiếu");
  if (typeof e.retryable !== "boolean") v.push("error.retryable thiếu");
  return v;
}
