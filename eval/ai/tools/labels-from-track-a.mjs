#!/usr/bin/env node
// OWNER: Track B. Sinh labels.json cho run-recognition.mjs từ manifest fixtures của Track A (PRE-004),
// KHÔNG cần merge nhánh A: lấy thư mục fixtures ra chỗ gitignored rồi trỏ script vào đó.
//
//   git archive origin/track/a-wardrobe-imaging data/fixtures/modern-clothing | tar -x -C eval/ai/images/track-a
//   node eval/ai/tools/labels-from-track-a.mjs --fixtures eval/ai/images/track-a/data/fixtures/modern-clothing
//   node eval/ai/run-recognition.mjs --base-url ... --labels labels.track-a.json \
//        --images-dir eval/ai/images/track-a/data/fixtures/modern-clothing --all-front --confirm-spend
//
// Dùng nhãn tham chiếu category/colors/pattern từ manifest A. Review lịch sử của B được áp
// cho các trường chưa chốt; --no-review đọc đúng nhãn đang có trong manifest.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { EVAL_DIR, parseArgs } from "../lib/common.mjs";

const args = parseArgs(process.argv.slice(2));
const fixtures = path.resolve(String(args.fixtures ?? path.join(EVAL_DIR, "images", "track-a", "data", "fixtures", "modern-clothing")));
const out = path.resolve(EVAL_DIR, "recognition", String(args.out ?? "labels.track-a.json"));
const manifest = JSON.parse(readFileSync(path.join(fixtures, "manifest.json"), "utf8"));

const items = manifest.items
  .filter((i) => i.front?.file && i.category && Array.isArray(i.colors) && i.colors.length > 0)
  .map((i) => ({
    id: i.id,
    name: i.name,
    front: i.front.file,
    back: i.back?.file ?? null,
    labels: { category: i.category, colors: i.colors, ...(i.pattern ? { pattern: i.pattern } : {}) },
    license: i.front.license,
    annotationStatus: i.annotationStatus,
  }));

// Áp review nhãn của B (recognition/label-review.track-a.json) trừ khi --no-review.
const reviewPath = path.resolve(EVAL_DIR, "recognition", String(args.review ?? "label-review.track-a.json"));
let applied = 0;
if (args["no-review"] !== true && existsSync(reviewPath)) {
  const review = JSON.parse(readFileSync(reviewPath, "utf8"));
  const byId = new Map(items.map((i) => [i.id, i]));
  const provenanceById = new Map(manifest.items.map((i) => [i.id, i.fieldProvenance]));
  for (const o of review.overrides) {
    const item = byId.get(o.id);
    if (!item) throw new Error(`Review: không thấy món ${o.id} trong manifest`);
    const current = JSON.stringify(item.labels[o.field]);
    const original = JSON.stringify(o.from);
    const keptAfterReview = current === original &&
      provenanceById.get(o.id)?.[o.field]?.method === "track_a_visual_review";
    if (current === JSON.stringify(o.to) || keptAfterReview) {
      // Preserve the resolved fixture label, including explicitly retained labels.
      item.reviewed = true;
      continue;
    }
    if (current !== original) {
      throw new Error(`Review: nhãn ${o.field} của ${o.id} trong manifest đã đổi (khác 'from') — review lại trước khi áp`);
    }
    item.labels[o.field] = o.to;
    item.reviewed = true;
    applied++;
  }
}

const pairs = items.filter((i) => i.back);
const cases = pairs.map((p, n) => ({ id: `N0${n + 1}-matched-pair-${p.id.slice(0, 8)}`, kind: "matched-pair", front: p.id, back: p.id, expectItem: p.id }));
if (pairs.length >= 2) {
  // Mặt trước món này + mặt sau món khác: phải bị đánh dấu review/khác món, không gộp âm thầm.
  cases.push({ id: "N10-cross-pair", kind: "conflicting-pair", front: pairs[0].id, back: pairs[1].id, expectItem: pairs[0].id, expectReview: true });
}

writeFileSync(out, `${JSON.stringify({ _source: "Track A data/fixtures/modern-clothing/manifest.json", items, cases }, null, 2)}\n`, "utf8");
console.log(`Đã ghi ${path.relative(process.cwd(), out)}: ${items.length} món, ${pairs.length} cặp, ${cases.length} case cặp, ${applied} nhãn sửa theo review B (+ --all-front để chạy front-only từng món).`);
