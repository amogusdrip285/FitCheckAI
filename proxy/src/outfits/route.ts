// OWNER: Track B. POST /v2/outfits — recommendation 1–5 outfit có giải thích (B-002).
import { ApiError } from "../shared/errors";
import { json, parseOrThrow } from "../shared/http";
import { LIMITS } from "../shared/domain";
import type { RouteDef } from "../shared/registry";
import {
  OUTFITS_PROMPT_VERSION,
  OUTFITS_SYSTEM_PROMPT,
  buildItemAliases,
  buildOutfitsUserText,
  resolveItemAliases,
} from "./prompt";
import {
  ModelOutfitsSchema,
  type OutfitDto,
  type OutfitRequest,
  OutfitRequestSchema,
  type RecommendationResultDto,
} from "./schema";
import { hasAvailableOutfit, selectValidOutfits, wardrobeCanFormOutfit } from "./validate";

export const OUTFITS_MAX_TOKENS = 8_000;

function checkSemantics(request: OutfitRequest): void {
  const ids = new Set<string>();
  for (const item of request.items) {
    if (ids.has(item.id)) throw new ApiError("INVALID_REQUEST", "items có id trùng lặp");
    ids.add(item.id);
  }
  if (request.focusItemId !== null) {
    const focus = request.items.find((i) => i.id === request.focusItemId);
    if (focus === undefined) throw new ApiError("INVALID_REQUEST", "focusItemId không thuộc snapshot");
    if (focus.attributes.category === "UNKNOWN") {
      throw new ApiError("UNSUPPORTED_CAPABILITY", "Chưa hỗ trợ phối quanh món chưa rõ loại");
    }
  }
}

export const outfitsRoute: RouteDef = {
  method: "POST",
  path: "/v2/outfits",
  service: "outfits",
  maxBodyBytes: LIMITS.outfitsBodyBytes,

  async handle(body, ctx) {
    const request = parseOrThrow(OutfitRequestSchema, body);
    checkSemantics(request);

    // Tủ không đủ ⇒ trả lỗi có kiểu, không tốn tiền gọi AI.
    if (!wardrobeCanFormOutfit(request.items, request.focusItemId)) {
      throw new ApiError("INSUFFICIENT_WARDROBE", "Tủ đồ chưa đủ món để tạo một bộ hợp lệ (cần TOP+BOTTOM hoặc DRESS)");
    }
    // Phối lại (C-102) khi mọi tổ hợp còn lại đều đã bị loại ⇒ báo "không còn lựa chọn khác", không gọi AI.
    if (!hasAvailableOutfit(request.items, request.focusItemId, request.excludeOutfitSignatures)) {
      throw new ApiError("INSUFFICIENT_WARDROBE", "Không còn bộ hợp lệ nào khác ngoài các bộ đã loại");
    }

    const model = ctx.deps.modelFor(ctx.env, "outfits");
    if (model === null) throw new ApiError("UNSUPPORTED_CAPABILITY", "AI provider chưa được cấu hình", { status: 503 });

    ctx.reserveUpstreamCall();
    ctx.annotate({ promptVersion: OUTFITS_PROMPT_VERSION, model: model.model });

    const aliases = buildItemAliases(request.items);
    const outcome = await model.generate({
      system: OUTFITS_SYSTEM_PROMPT,
      content: [{ type: "text", text: buildOutfitsUserText(request, aliases) }],
      schema: ModelOutfitsSchema,
      maxTokens: OUTFITS_MAX_TOKENS,
      effort: "medium",
      signal: ctx.signal,
    });
    if (!outcome.ok) {
      ctx.annotate({ errorCode: `${outcome.code}:${outcome.reason}` });
      throw new ApiError(outcome.code, "AI không trả được kết quả hợp lệ", { retryable: outcome.retryable });
    }
    ctx.annotate({
      model: outcome.meta.model,
      inputTokens: outcome.meta.inputTokens,
      outputTokens: outcome.meta.outputTokens,
    });

    const { accepted, rejected } = selectValidOutfits(resolveItemAliases(outcome.data, aliases), request);
    ctx.annotate({ accepted: accepted.length, rejected });

    if (accepted.length === 0) {
      if (outcome.data.outfits.length === 0) {
        throw new ApiError("INSUFFICIENT_WARDROBE", "Không tạo được bộ hợp lệ với các ràng buộc hiện tại");
      }
      throw new ApiError("INVALID_AI_OUTPUT", "AI trả về bộ không hợp lệ", { retryable: true });
    }

    const createdAt = ctx.deps.now();
    const revisions = new Map(request.items.map((item) => [item.id, item.revision] as const));
    const outfits: OutfitDto[] = accepted.map((outfit) => ({
      id: ctx.deps.uuid(),
      requestId: request.requestId,
      wardrobeSnapshotId: request.wardrobeSnapshotId,
      items: outfit.itemIds.map((itemId) => ({ itemId, revision: revisions.get(itemId) as number })),
      explanation: outfit.explanation,
      source: model.source,
      createdAt,
      signature: outfit.signature,
    }));

    // Trả ít hơn requestedCount luôn có lý do. Nếu thiếu vì validator loại bộ sai, nói thẳng điều đó.
    const anyRejected = Object.values(rejected).some((n) => (n ?? 0) > 0);
    const shortfallReason =
      outfits.length < request.requestedCount
        ? (outcome.data.shortfallReason ?? (anyRejected ? "INVALID_OUTFITS_REMOVED" : "NOT_ENOUGH_DISTINCT_COMBINATIONS"))
        : null;

    const result: RecommendationResultDto = { requestId: request.requestId, outfits, shortfallReason };
    return json(result);
  },
};
