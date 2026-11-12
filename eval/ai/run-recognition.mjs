#!/usr/bin/env node
// OWNER: Track B. B-006 — eval nhận diện hai góc nhìn trên ảnh garment đã gán nhãn (PRE-004).
//
//   FITCHECK_ACCESS_CODE=... node eval/ai/run-recognition.mjs --base-url http://127.0.0.1:8787
//       ⇒ kiểm ảnh/nhãn + chạy guard (thiếu consent ⇒ 403) — KHÔNG tốn tiền; in kế hoạch case AI.
//   ... --confirm-spend [--all-front] [--max-ai-calls 9]
//       ⇒ gọi model thật (TỐN TIỀN). --all-front: thêm mỗi món một lần front-only để đo theo field.
import { createHash } from "node:crypto";
import { existsSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { EVAL_DIR, health, mdEscape, newId, openSession, parseArgs, postJson, requireConfig, writeResults } from "./lib/common.mjs";
import { checkErrorEnvelope } from "./lib/invariants.mjs";

const MAX_IMAGE_BYTES = 1024 * 1024;
let imagesDir = path.join(EVAL_DIR, "images");
const ENUMS = {
  category: ["TOP", "BOTTOM", "DRESS", "OUTERWEAR", "SHOES", "ACCESSORY", "UNKNOWN"],
  colors: ["BLACK", "WHITE", "GRAY", "BEIGE", "BROWN", "BLUE", "GREEN", "RED", "YELLOW", "PINK", "PURPLE", "OTHER"],
  pattern: ["SOLID", "STRIPED", "CHECKED", "PRINTED", "OTHER", "UNKNOWN"],
};

function sniff(bytes) {
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  if (bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return "image/png";
  if (bytes.subarray(0, 4).toString("latin1") === "RIFF" && bytes.subarray(8, 12).toString("latin1") === "WEBP") return "image/webp";
  return null;
}

function loadImage(file, view, mediaId) {
  const full = path.resolve(imagesDir, file);
  if (!existsSync(full)) throw new Error(`Thiếu ảnh ${path.relative(process.cwd(), full)}`);
  if (statSync(full).size > MAX_IMAGE_BYTES) throw new Error(`Ảnh ${file} > 1 MiB — resize trước (A lo derivative)`);
  const bytes = readFileSync(full);
  const mimeType = sniff(bytes);
  if (!mimeType) throw new Error(`Ảnh ${file} không phải JPEG/PNG/WebP`);
  return { view, mediaId, mimeType, sha256: createHash("sha256").update(bytes).digest("hex"), dataBase64: bytes.toString("base64") };
}

function buildRequest(caseId, front, back) {
  const mediaIds = [front.mediaId, ...(back ? [back.mediaId] : [])];
  return {
    schemaVersion: 2,
    requestId: newId(caseId.slice(0, 3).toLowerCase()),
    itemId: `eval-${caseId}`,
    inputRevision: 1,
    front,
    back,
    // Người chạy eval xác nhận đồng ý gửi đúng các ảnh garment này cho provider để nhận diện.
    consentGrant: { grantId: newId("consent"), purpose: "RECOGNITION", mediaIds, issuedAt: Date.now(), revokedAt: null, userConfirmed: true },
  };
}

function validateLabels(suite) {
  const problems = [];
  const ids = new Set();
  for (const item of suite.items) {
    if (ids.has(item.id)) problems.push(`Trùng item id ${item.id}`);
    ids.add(item.id);
    const l = item.labels ?? {};
    if (!ENUMS.category.includes(l.category)) problems.push(`${item.id}: category nhãn không hợp lệ`);
    if (!Array.isArray(l.colors) || l.colors.length === 0 || l.colors.some((c) => !ENUMS.colors.includes(c))) problems.push(`${item.id}: colors nhãn không hợp lệ`);
    if (l.pattern !== undefined && !ENUMS.pattern.includes(l.pattern)) problems.push(`${item.id}: pattern nhãn không hợp lệ`);
  }
  for (const c of suite.cases) {
    if (!ids.has(c.front)) problems.push(`${c.id}: front ${c.front} không có trong items`);
    if (c.back && !ids.has(c.back)) problems.push(`${c.id}: back ${c.back} không có trong items`);
    if (c.back && !suite.items.find((i) => i.id === c.back)?.back) problems.push(`${c.id}: món ${c.back} không có ảnh sau`);
  }
  return problems;
}

function score(labels, attrs) {
  return {
    categoryCorrect: attrs.category === labels.category,
    primaryColorFound: attrs.colors.includes(labels.colors[0]),
    colorOverlap: labels.colors.filter((c) => attrs.colors.includes(c)).length / labels.colors.length,
    patternCorrect: labels.pattern === undefined ? null : attrs.pattern === labels.pattern,
  };
}

function checkResultShape(request, body) {
  const v = [];
  if (body?.requestId !== request.requestId) v.push("requestId không khớp");
  if (body?.itemId !== request.itemId || body?.inputRevision !== request.inputRevision) v.push("itemId/inputRevision không khớp");
  const a = body?.suggestedAttributes;
  if (!a) return [...v, "Thiếu suggestedAttributes"];
  if (!ENUMS.category.includes(a.category)) v.push("category ngoài enum");
  if (!Array.isArray(a.colors) || a.colors.length > 3 || a.colors.some((c) => !ENUMS.colors.includes(c))) v.push("colors sai enum hoặc >3");
  if (!ENUMS.pattern.includes(a.pattern)) v.push("pattern ngoài enum");
  if (a.formality !== null && !(Number.isInteger(a.formality) && a.formality >= 1 && a.formality <= 5)) v.push("formality ngoài 1–5");
  if (!Array.isArray(a.seasonTags) || a.seasonTags.some((s) => !["SPRING", "SUMMER", "AUTUMN", "WINTER"].includes(s))) v.push("seasonTags ngoài enum (core/model sẽ từ chối)");
  if (!request.back && JSON.stringify(body.viewEvidence ?? {}).includes("BACK")) v.push("viewEvidence có BACK dù không gửi ảnh sau");
  return v;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const config = requireConfig(args);
  const confirmSpend = args["confirm-spend"] === true;
  const maxAiCalls = Number(args["max-ai-calls"] ?? 9);
  const delayMs = Number(args["delay-ms"] ?? 0);
  const labelsPath = path.resolve(EVAL_DIR, "recognition", String(args.labels ?? "labels.json"));
  if (typeof args["images-dir"] === "string") imagesDir = path.resolve(args["images-dir"]);
  if (!existsSync(labelsPath)) {
    throw new Error(`Chưa có ${path.relative(process.cwd(), labelsPath)} — copy từ labels.example.json và chụp ảnh theo PRE-004`);
  }
  const suite = JSON.parse(readFileSync(labelsPath, "utf8"));
  const labelProblems = validateLabels(suite);
  if (labelProblems.length) throw new Error(`File nhãn lỗi:\n- ${labelProblems.join("\n- ")}`);
  const items = new Map(suite.items.map((i) => [i.id, i]));

  const plan = suite.cases.map((c) => ({ ...c }));
  if (args["all-front"] === true) {
    for (const item of suite.items) plan.push({ id: `F-${item.id}`, kind: "front-only", front: item.id, expectItem: item.id });
  }

  const serverHealth = await health(config.baseUrl);
  console.log(`Proxy ${config.baseUrl}: recognitionViews=${serverHealth.recognitionViews} services=${JSON.stringify(serverHealth.services)}`);
  const token = await openSession(config);
  const results = [];

  // Guard: thiếu consent phải bị chặn trước AI (không tốn tiền).
  {
    const first = suite.items[0];
    const request = { ...buildRequest("G01", loadImage(first.front, "FRONT", `m-${first.id}-f`), null), consentGrant: null };
    const r = await postJson(config.baseUrl, token, "/v2/recognition", request);
    const problems = [...(r.status === 403 ? [] : [`HTTP ${r.status}, mong đợi 403`]), ...checkErrorEnvelope(r.body)];
    if (r.body?.error?.code !== "CONSENT_REQUIRED") problems.push(`error.code ${r.body?.error?.code}`);
    results.push({ id: "G01-no-consent", kind: "guard", outcome: problems.length ? "FAIL" : "PASS", problems, status: r.status, latencyMs: r.latencyMs });
    console.log(`G01-no-consent: ${results.at(-1).outcome}`);
  }

  let aiCalls = 0;
  for (const c of plan) {
    const frontItem = items.get(c.front);
    const backItem = c.back ? items.get(c.back) : null;
    const expectItem = items.get(c.expectItem ?? c.front);
    const isPair = backItem !== null;
    const frontOnlyServer = serverHealth.recognitionViews === "FRONT_ONLY";

    if (!confirmSpend || aiCalls >= maxAiCalls) {
      if (!(isPair && frontOnlyServer)) {
        results.push({ id: c.id, kind: c.kind, outcome: "NOT_RUN", note: confirmSpend ? "vượt --max-ai-calls" : "cần --confirm-spend" });
        continue;
      }
    }
    const front = loadImage(frontItem.front, "FRONT", `m-${frontItem.id}-f`);
    const back = isPair ? loadImage(backItem.back, "BACK", `m-${backItem.id}-b`) : null;
    const request = buildRequest(c.id, front, back);
    if (!(isPair && frontOnlyServer)) {
      // --delay-ms: giãn call để không chạm rate limit của proxy (mặc định 20/phút) và free tier provider.
      if (aiCalls > 0 && delayMs > 0) await new Promise((r) => setTimeout(r, delayMs));
      aiCalls++;
    }
    const r = await postJson(config.baseUrl, token, "/v2/recognition", request);

    if (isPair && frontOnlyServer) {
      // Server FRONT_ONLY: cặp phải bị chặn trước billing — ghi rõ chưa đạt dual recognition.
      const ok = r.status === 400 && r.body?.error?.code === "UNSUPPORTED_CAPABILITY";
      results.push({ id: c.id, kind: c.kind, outcome: ok ? "PASS" : "FAIL", status: r.status, problems: ok ? [] : ["Mong đợi 400 UNSUPPORTED_CAPABILITY"], note: "FRONT_ONLY: dual recognition CHƯA đạt" });
      continue;
    }
    if (r.status !== 200) {
      results.push({ id: c.id, kind: c.kind, outcome: "FAIL", status: r.status, latencyMs: r.latencyMs, requestId: request.requestId, problems: [`HTTP ${r.status} ${r.body?.error?.code ?? ""}`, ...checkErrorEnvelope(r.body)] });
      console.log(`${c.id}: FAIL (HTTP ${r.status})`);
      continue;
    }
    const problems = checkResultShape(request, r.body);
    const s = score(expectItem.labels, r.body.suggestedAttributes);
    const flagged = r.body.needsReviewFields.length > 0 || r.body.warnings.includes("VIEWS_LOOK_LIKE_DIFFERENT_ITEMS");
    if (c.expectReview && !flagged) problems.push("Cặp mâu thuẫn nhưng không có field cần review/cảnh báo (bị gộp âm thầm)");
    results.push({
      id: c.id,
      kind: c.kind,
      outcome: problems.length ? "FAIL" : "PASS",
      problems,
      status: r.status,
      latencyMs: r.latencyMs,
      requestId: request.requestId,
      score: s,
      predicted: r.body.suggestedAttributes,
      labels: expectItem.labels,
      needsReviewFields: r.body.needsReviewFields,
      warnings: r.body.warnings,
      source: r.body.source,
    });
    console.log(`${c.id}: ${results.at(-1).outcome} category=${s.categoryCorrect ? "đúng" : "SAI"} màu chính=${s.primaryColorFound ? "đúng" : "SAI"} (${r.latencyMs} ms)`);
  }

  const scored = results.filter((r) => r.score);
  const n = scored.length;
  const pct = (k) => (n ? `${scored.filter((r) => r.score[k] === true).length}/${n}` : "—");
  const md = [];
  md.push(`# B-006 — Eval recognition (${new Date().toISOString()})`);
  md.push("");
  md.push(`- Proxy: \`${config.baseUrl}\` · recognitionViews \`${serverHealth.recognitionViews}\` · call AI: ${aiCalls}${confirmSpend ? "" : " (chưa bật --confirm-spend)"}`);
  md.push(`- Mẫu chấm được: **n = ${n}**. Category đúng ${pct("categoryCorrect")} · màu chính có mặt ${pct("primaryColorFound")} · pattern đúng ${pct("patternCorrect")}.`);
  md.push("- Không suy material/fit accuracy từ category đúng. Mẫu nhỏ: không phải tỷ lệ production. Model/token/chi phí: xem `wrangler tail` theo requestId.");
  md.push("");
  md.push("| Case | Loại | Kết quả | HTTP | Latency | Nhãn (cat/màu/pattern) | Dự đoán (cat/màu/pattern) | needsReview | warnings | requestId | Vấn đề |");
  md.push("|---|---|---|---|---|---|---|---|---|---|---|");
  for (const r of results) {
    const lab = r.labels ? `${r.labels.category}/${r.labels.colors.join(",")}/${r.labels.pattern ?? "-"}` : "—";
    const pred = r.predicted ? `${r.predicted.category}/${r.predicted.colors.join(",")}/${r.predicted.pattern}` : "—";
    md.push(`| ${r.id} | ${r.kind} | ${r.outcome} | ${r.status ?? "—"} | ${r.latencyMs !== undefined ? `${r.latencyMs} ms` : "—"} | ${lab} | ${pred} | ${(r.needsReviewFields ?? []).join(", ") || "—"} | ${(r.warnings ?? []).join(", ") || "—"} | ${r.requestId ?? "—"} | ${mdEscape((r.problems ?? []).join("; ") || r.note || "")} |`);
  }
  const base = writeResults("recognition", `${md.join("\n")}\n`, { generatedAt: new Date().toISOString(), baseUrl: config.baseUrl, aiCalls, results });
  console.log(`\nReport: ${base}.md`);
  process.exitCode = results.some((r) => r.outcome === "FAIL") ? 1 : 0;
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 2;
});
