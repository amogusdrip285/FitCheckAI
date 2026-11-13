#!/usr/bin/env node
// OWNER: Track B. B-004 — chạy 10 case recommendation qua proxy, kiểm invariant, xuất report cho reviewer chấm.
//
//   FITCHECK_ACCESS_CODE=... node eval/ai/run-recommendation.mjs --base-url http://127.0.0.1:8787
//       ⇒ chỉ chạy case "guard" (proxy chặn trước AI, KHÔNG tốn tiền) + in kế hoạch case AI.
//   ... --confirm-spend [--only R05,R06] [--max-ai-calls 6]
//       ⇒ chạy thêm case "ai" (gọi model thật, TỐN TIỀN).
import { readFileSync } from "node:fs";
import path from "node:path";
import { EVAL_DIR, health, mdEscape, newId, openSession, parseArgs, postJson, requireConfig, writeResults } from "./lib/common.mjs";
import { checkErrorEnvelope, checkRecommendation } from "./lib/invariants.mjs";

const PROMPT_VERSION_HINT = "outfits-v2.1"; // đối chiếu proxy/src/outfits/prompt.ts

function buildRequest(spec, wardrobes) {
  const r = spec.request;
  const items = r.wardrobe ? wardrobes[r.wardrobe] : r.items;
  if (!items) throw new Error(`Case ${spec.id}: không tìm thấy wardrobe ${r.wardrobe}`);
  return {
    schemaVersion: 2,
    requestId: newId(spec.id.slice(0, 3).toLowerCase()),
    wardrobeSnapshotId: `eval-${r.wardrobe ?? "custom"}`,
    items,
    profile: r.profile === undefined ? { heightCm: null, stylePreferences: [], preferredCategories: [] } : r.profile,
    occasion: r.occasion,
    styleOverrides: r.styleOverrides ?? [],
    freeText: r.freeText ?? "",
    weather: r.weather ?? null,
    requestedCount: r.requestedCount,
    focusItemId: r.focusItemId ?? null,
    excludeOutfitSignatures: r.excludeOutfitSignatures ?? [],
    regenerationOfRequestId: null,
  };
}

function checkExpectations(spec, request, result) {
  const e = spec.expect;
  const problems = [];
  if (result.status !== e.status) problems.push(`HTTP ${result.status}, mong đợi ${e.status}`);
  if (result.status !== 200) {
    problems.push(...checkErrorEnvelope(result.body));
    if (e.errorCode && result.body?.error?.code !== e.errorCode) {
      problems.push(`error.code ${result.body?.error?.code}, mong đợi ${e.errorCode}`);
    }
    return problems;
  }
  problems.push(...checkRecommendation(request, result.body));
  const n = result.body?.outfits?.length ?? 0;
  if (e.minOutfits !== undefined && n < e.minOutfits) problems.push(`Chỉ ${n} bộ, mong đợi ≥${e.minOutfits}`);
  if (e.maxOutfits !== undefined && n > e.maxOutfits) problems.push(`${n} bộ, tủ chỉ cho phép ≤${e.maxOutfits}`);
  if (e.shortfall && !result.body?.shortfallReason) problems.push("Thiếu shortfallReason");
  return problems;
}

function describeOutfit(outfit, request) {
  const byId = new Map(request.items.map((i) => [i.id, i]));
  const parts = outfit.items.map((ref) => {
    const a = byId.get(ref.itemId)?.attributes;
    return a ? `${ref.itemId} (${a.category} ${(a.colors ?? []).join("/")})` : ref.itemId;
  });
  return parts.join(" + ");
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const config = requireConfig(args);
  const confirmSpend = args["confirm-spend"] === true;
  const only = typeof args.only === "string" ? new Set(args.only.split(",").map((s) => s.trim())) : null;
  const maxAiCalls = Number(args["max-ai-calls"] ?? 7);

  const suite = JSON.parse(readFileSync(path.join(EVAL_DIR, "cases", "recommendation.json"), "utf8"));
  const cases = suite.cases.filter((c) => only === null || [...only].some((o) => c.id.startsWith(o)));

  const serverHealth = await health(config.baseUrl);
  console.log(`Proxy ${config.baseUrl}: version=${serverHealth.version} services=${JSON.stringify(serverHealth.services)} provider=${serverHealth.providerConfigured}`);
  const token = await openSession(config);

  const results = [];
  let aiCalls = 0;
  for (const spec of cases) {
    if (spec.kind === "unit") {
      results.push({ id: spec.id, title: spec.title, kind: spec.kind, outcome: "COVERED_BY_UNIT_TEST", note: spec.coveredBy });
      continue;
    }
    if (spec.kind === "ai" && (!confirmSpend || aiCalls >= maxAiCalls)) {
      results.push({ id: spec.id, title: spec.title, kind: spec.kind, outcome: "NOT_RUN", note: confirmSpend ? "vượt --max-ai-calls" : "cần --confirm-spend" });
      continue;
    }
    const request = buildRequest(spec, suite.wardrobes);
    if (spec.kind === "ai") aiCalls++;
    let result;
    try {
      result = await postJson(config.baseUrl, token, "/v2/outfits", request);
    } catch (error) {
      results.push({ id: spec.id, title: spec.title, kind: spec.kind, outcome: "FAIL", problems: [`Lỗi mạng/timeout client: ${error.message}`] });
      continue;
    }
    const problems = checkExpectations(spec, request, result);
    results.push({
      id: spec.id,
      title: spec.title,
      kind: spec.kind,
      live: spec.live === true,
      outcome: problems.length === 0 ? "PASS" : "FAIL",
      problems,
      requestId: request.requestId,
      status: result.status,
      latencyMs: result.latencyMs,
      errorCode: result.body?.error?.code ?? null,
      shortfallReason: result.body?.shortfallReason ?? null,
      source: result.body?.outfits?.[0]?.source ?? null,
      reviewNote: spec.expect.reviewNote ?? null,
      outfits: (result.body?.outfits ?? []).map((o) => ({ items: describeOutfit(o, request), explanation: o.explanation })),
      freeText: request.freeText || null,
      occasion: request.occasion,
    });
    console.log(`${spec.id}: ${results.at(-1).outcome} (HTTP ${result.status}, ${result.latencyMs} ms)${problems.length ? ` — ${problems.join("; ")}` : ""}`);
  }

  const ran = results.filter((r) => r.outcome === "PASS" || r.outcome === "FAIL");
  const failed = ran.filter((r) => r.outcome === "FAIL");
  const md = [];
  md.push(`# B-004 — Eval recommendation (${new Date().toISOString()})`);
  md.push("");
  md.push(`- Proxy: \`${config.baseUrl}\` · version \`${serverHealth.version}\` · prompt (kỳ vọng) \`${PROMPT_VERSION_HINT}\``);
  md.push(`- Đã chạy ${ran.length}/${results.length} case; invariant FAIL: **${failed.length}**; call AI: ${aiCalls}${confirmSpend ? "" : " (chưa bật --confirm-spend)"}`);
  md.push("- Model/token/chi phí: lấy từ log Worker (`npx wrangler tail`) theo requestId bên dưới — response không chứa usage.");
  md.push("- Mẫu nhỏ: không suy ra tỷ lệ production hay p90 latency.");
  md.push("");
  md.push("| Case | Loại | Kết quả | HTTP | Latency | Số bộ | shortfall | requestId | Vấn đề |");
  md.push("|---|---|---|---|---|---|---|---|---|");
  for (const r of results) {
    md.push(
      `| ${r.id} ${mdEscape(r.title)} | ${r.kind}${r.live ? " (live≥3)" : ""} | ${r.outcome} | ${r.status ?? "—"} | ${r.latencyMs !== undefined ? `${r.latencyMs} ms` : "—"} | ${r.outfits?.length ?? "—"} | ${r.shortfallReason ?? "—"} | ${r.requestId ?? "—"} | ${mdEscape((r.problems ?? []).join("; ") || r.note || "")} |`,
    );
  }
  md.push("");
  md.push("## Chấm điểm giải thích (2 reviewer: B và A, chấm độc lập)");
  md.push("");
  md.push("Thang: 1 sai/mâu thuẫn input · 2 chung chung · 3 đúng thuộc tính + ≥1 context thật · 4 cụ thể, nhất quán · 5 rõ cả trade-off/giới hạn. Đạt khi mỗi case ≥3/5 ở cả hai tiêu chí, cả hai reviewer. Invariant phải 100% — điểm văn phong không bù được sai đồ.");
  for (const r of results.filter((x) => x.outfits && x.outfits.length > 0)) {
    md.push("");
    md.push(`### ${r.id} — ${r.title} (dịp ${r.occasion}${r.freeText ? `; freeText: "${mdEscape(r.freeText)}"` : ""})`);
    if (r.reviewNote) md.push(`> Lưu ý chấm: ${r.reviewNote}`);
    md.push("");
    md.push("| # | Món | Giải thích | Phù hợp context (B / A) | Giải thích (B / A) |");
    md.push("|---|---|---|---|---|");
    r.outfits.forEach((o, i) => md.push(`| ${i + 1} | ${mdEscape(o.items)} | ${mdEscape(o.explanation)} | __ / __ | __ / __ |`));
  }
  const base = writeResults("recommendation", `${md.join("\n")}\n`, { generatedAt: new Date().toISOString(), baseUrl: config.baseUrl, aiCalls, results });
  console.log(`\nReport: ${base}.md`);
  process.exitCode = failed.length > 0 ? 1 : 0;
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 2;
});
