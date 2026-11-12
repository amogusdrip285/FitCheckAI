// OWNER: Track B. POST /v2/recognition — nhận diện thuộc tính từ ảnh trước (bắt buộc) / sau (tùy chọn) (BACK-004).
import { recognitionViews } from "../env";
import { LIMITS } from "../shared/domain";
import { ApiError } from "../shared/errors";
import { json, parseOrThrow } from "../shared/http";
import type { ContentPart } from "../shared/model";
import type { RouteDef } from "../shared/registry";
import { checkImage } from "./images";
import { RECOGNITION_PROMPT_VERSION, RECOGNITION_SYSTEM_PROMPT, buildRecognitionUserText } from "./prompt";
import { ModelRecognitionSchema, type RecognitionRequest, RecognitionRequestSchema } from "./schema";
import { buildRecognitionResult } from "./validate";

export const RECOGNITION_MAX_TOKENS = 4_000;

function checkConsent(request: RecognitionRequest): void {
  const grant = request.consentGrant;
  const mediaIds = [request.front.mediaId, ...(request.back ? [request.back.mediaId] : [])];
  const valid =
    grant !== null &&
    grant.purpose === "RECOGNITION" &&
    grant.userConfirmed &&
    grant.revokedAt === null &&
    mediaIds.every((id) => grant.mediaIds.includes(id));
  if (!valid) throw new ApiError("CONSENT_REQUIRED", "Cần người dùng đồng ý gửi đúng các ảnh này để nhận diện");
}

export const recognitionRoute: RouteDef = {
  method: "POST",
  path: "/v2/recognition",
  service: "recognition",
  maxBodyBytes: LIMITS.recognitionBodyBytes,

  async handle(body, ctx) {
    const request = parseOrThrow(RecognitionRequestSchema, body);

    if (request.front.view !== "FRONT") throw new ApiError("INVALID_REQUEST", "front.view phải là FRONT");
    if (request.back !== null) {
      if (request.back.view !== "BACK") throw new ApiError("INVALID_REQUEST", "back.view phải là BACK");
      if (request.back.mediaId === request.front.mediaId) {
        throw new ApiError("INVALID_REQUEST", "Ảnh sau phải là ảnh khác ảnh trước");
      }
    }
    checkConsent(request);

    // Capability FRONT_ONLY: chặn cặp trước khi tính tiền, không âm thầm bỏ ảnh sau.
    if (request.back !== null && recognitionViews(ctx.env) === "FRONT_ONLY") {
      throw new ApiError("UNSUPPORTED_CAPABILITY", "Server hiện chỉ nhận diện ảnh mặt trước");
    }

    const front = await checkImage(request.front, LIMITS.recognitionImageBytes);
    const back = request.back === null ? null : await checkImage(request.back, LIMITS.recognitionImageBytes);
    if (back !== null && back.sha256 === front.sha256) {
      throw new ApiError("INVALID_REQUEST", "Ảnh sau trùng ảnh trước; không nhân đôi ảnh trước làm ảnh sau");
    }

    const model = ctx.deps.modelFor(ctx.env, "recognition");
    if (model === null) throw new ApiError("UNSUPPORTED_CAPABILITY", "AI provider chưa được cấu hình", { status: 503 });

    ctx.reserveUpstreamCall();
    ctx.annotate({ promptVersion: RECOGNITION_PROMPT_VERSION, model: model.model });

    const content: ContentPart[] = [
      { type: "text", text: "FRONT:" },
      { type: "image", mediaType: front.mediaType, base64: front.base64 },
    ];
    if (back !== null) {
      content.push({ type: "text", text: "BACK:" }, { type: "image", mediaType: back.mediaType, base64: back.base64 });
    }
    content.push({ type: "text", text: buildRecognitionUserText(back !== null) });

    const outcome = await model.generate({
      system: RECOGNITION_SYSTEM_PROMPT,
      content,
      schema: ModelRecognitionSchema,
      maxTokens: RECOGNITION_MAX_TOKENS,
      effort: "low",
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

    return json(
      buildRecognitionResult(outcome.data, {
        requestId: request.requestId,
        itemId: request.itemId,
        inputRevision: request.inputRevision,
        hasBack: back !== null,
        source: model.source,
      }),
    );
  },
};
