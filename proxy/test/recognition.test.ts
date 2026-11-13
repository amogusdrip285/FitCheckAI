import { describe, expect, it } from "vitest";
import { RECOGNITION_PROMPT_VERSION, RECOGNITION_SYSTEM_PROMPT } from "../src/recognition/prompt";

describe("prompt recognition-v2.2", () => {
  it("tag đầu là kiểu món theo dấu hiệu nhìn thấy, sơ mi không bị gọi là áo thun", () => {
    expect(RECOGNITION_PROMPT_VERSION).toBe("recognition-v2.2");
    expect(RECOGNITION_SYSTEM_PROMPT).toContain('Tag ĐẦU TIÊN là kiểu món');
    expect(RECOGNITION_SYSTEM_PROMPT).toContain('"áo sơ mi" (kể cả vải flannel/kẻ caro');
    expect(RECOGNITION_SYSTEM_PROMPT).toContain('Không bao giờ gọi áo sơ mi là "áo thun"/"áo phông"');
  });
});
import { sha256Hex, sniffImageType } from "../src/recognition/images";
import type { RecognitionResultDto } from "../src/recognition/validate";
import modelRecognition from "./fixtures/v2/model-recognition.json";
import { fakeImage, setup } from "./helpers";

const FRONT = fakeImage("jpeg", 1);
const BACK = fakeImage("jpeg", 2);

function recognitionRequest(overrides: Record<string, unknown> = {}, withBack = false) {
  return {
    schemaVersion: 2,
    requestId: "rec-1",
    itemId: "item-1",
    inputRevision: 3,
    front: { view: "FRONT", mediaId: "m-front", mimeType: "image/jpeg", dataBase64: FRONT },
    back: withBack ? { view: "BACK", mediaId: "m-back", mimeType: "image/jpeg", dataBase64: BACK } : null,
    consentGrant: {
      grantId: "g1",
      purpose: "RECOGNITION",
      mediaIds: withBack ? ["m-front", "m-back"] : ["m-front"],
      issuedAt: 1,
      revokedAt: null,
      userConfirmed: true,
    },
    ...overrides,
  };
}

describe("image checks", () => {
  it("nhận diện magic bytes", () => {
    const decode = (b64: string) => Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    expect(sniffImageType(decode(fakeImage("png", 1)))).toBe("image/png");
    expect(sniffImageType(decode(fakeImage("jpeg", 1)))).toBe("image/jpeg");
    expect(sniffImageType(decode(fakeImage("webp", 1)))).toBe("image/webp");
    expect(sniffImageType(new Uint8Array([1, 2, 3, 4]))).toBeNull();
  });
});

describe("POST /v2/recognition", () => {
  it("chỉ ảnh trước: vẫn tag, bỏ evidence BACK không có thật, gửi đúng 1 ảnh", async () => {
    const t = setup();
    t.model.respond(modelRecognition.frontOnly);
    const res = await t.post("/v2/recognition", recognitionRequest());
    expect(res.status).toBe(200);
    const body = (await res.json()) as RecognitionResultDto;
    expect(body).toMatchObject({
      requestId: "rec-1",
      itemId: "item-1",
      inputRevision: 3,
      source: "AI_LIVE",
      warnings: [],
      suggestedAttributes: { category: "TOP", colors: ["WHITE"], styleTags: ["tối giản", "cơ bản"], material: null },
      needsReviewFields: ["material"],
    });
    expect(body.viewEvidence).toEqual({ category: ["FRONT"], colors: ["FRONT"] });
    expect(body.suggestedAttributes).not.toHaveProperty("garmentType");
    const call = t.model.calls[0]!;
    expect(call.content.filter((p) => p.type === "image")).toHaveLength(1);
    expect(call.effort).toBe("low");
  });

  it.each(["SHIRT", "POLO", "T_SHIRT"])("gợi ý %s với bằng chứng ảnh và giữ nhóm TOP", async (garmentType) => {
    const t = setup();
    t.model.respond({
      ...modelRecognition.frontOnly,
      attributes: { ...modelRecognition.frontOnly.attributes, garmentType },
      evidence: [{ field: "garmentType", views: ["FRONT"] }],
    });
    const res = await t.post("/v2/recognition", recognitionRequest({ garmentTypeVersion: 1 }));
    expect(res.status).toBe(200);
    const body = (await res.json()) as RecognitionResultDto;
    expect(body.suggestedAttributes).toMatchObject({ category: "TOP", garmentType });
    expect(body.viewEvidence.garmentType).toEqual(["FRONT"]);
    expect(t.model.calls[0]!.system).toContain("SHIRT (TOP) · Áo sơ mi");
    expect(t.model.calls[0]!.system).toContain("POLO (TOP) · Áo polo");
  });

  it.each([
    { garmentType: "JEANS", view: "FRONT" },
    { garmentType: "SHIRT", view: "BACK" },
  ])("bỏ nhãn sai nhóm hoặc không có bằng chứng trong ảnh gửi: $garmentType/$view", async ({ garmentType, view }) => {
    const t = setup();
    t.model.respond({
      ...modelRecognition.frontOnly,
      attributes: { ...modelRecognition.frontOnly.attributes, garmentType },
      evidence: [{ field: "garmentType", views: [view] }],
    });
    const res = await t.post("/v2/recognition", recognitionRequest({ garmentTypeVersion: 1 }));
    const body = (await res.json()) as RecognitionResultDto;
    expect(body.suggestedAttributes.garmentType).toBeNull();
    expect(body.needsReviewFields).toContain("garmentType");
  });

  it("cặp khớp: gửi FRONT rồi BACK theo đúng thứ tự", async () => {
    const t = setup();
    t.model.respond(modelRecognition.matchedPair);
    const res = await t.post("/v2/recognition", recognitionRequest({}, true));
    expect(res.status).toBe(200);
    const body = (await res.json()) as RecognitionResultDto;
    expect(body.needsReviewFields).toEqual([]);
    expect(body.viewEvidence.category).toEqual(["FRONT", "BACK"]);
    const parts = t.model.calls[0]!.content;
    expect(parts.map((p) => (p.type === "image" ? `img:${p.base64 === FRONT ? "F" : "B"}` : p.text.slice(0, 6)))).toEqual([
      "FRONT:",
      "img:F",
      "BACK:",
      "img:B",
      expect.any(String),
    ]);
  });

  it("seasonTags là enum SPRING…WINTER (khớp ClothingAttributes của core/model), bỏ trùng, thứ tự cố định", async () => {
    const t = setup();
    t.model.respond({ ...modelRecognition.matchedPair, attributes: { ...modelRecognition.matchedPair.attributes, seasonTags: ["WINTER", "AUTUMN", "WINTER"] } });
    const body = (await (await t.post("/v2/recognition", recognitionRequest({}, true))).json()) as RecognitionResultDto;
    expect(body.suggestedAttributes.seasonTags).toEqual(["AUTUMN", "WINTER"]);
  });

  it("cặp mâu thuẫn ⇒ field cần review, không last-write-wins", async () => {
    const t = setup();
    t.model.respond(modelRecognition.conflictingPair);
    const body = (await (await t.post("/v2/recognition", recognitionRequest({}, true))).json()) as RecognitionResultDto;
    expect(body.needsReviewFields).toEqual(["pattern"]);
  });

  it("hai ảnh có vẻ khác món ⇒ cảnh báo và review toàn bộ", async () => {
    const t = setup();
    t.model.respond(modelRecognition.differentItems);
    const body = (await (await t.post("/v2/recognition", recognitionRequest({}, true))).json()) as RecognitionResultDto;
    expect(body.warnings).toContain("VIEWS_LOOK_LIKE_DIFFERENT_ITEMS");
    expect(body.needsReviewFields).toHaveLength(7);
  });

  it("output lộn xộn được chuẩn hóa: màu ≤3 không trùng, tag ≤5, formality ngoài 1–5 thành null", async () => {
    const t = setup();
    t.model.respond(modelRecognition.messy);
    const body = (await (await t.post("/v2/recognition", recognitionRequest())).json()) as RecognitionResultDto;
    expect(body.suggestedAttributes.colors).toEqual(["RED", "BLUE", "GREEN"]);
    expect(body.suggestedAttributes.styleTags).toHaveLength(5);
    expect(body.suggestedAttributes.formality).toBeNull();
    expect(body.suggestedAttributes.material).toBeNull();
    expect(body.warnings).toContain("CATEGORY_UNKNOWN");
    expect(body.needsReviewFields).toEqual(["category", "colors", "pattern", "styleTags", "material", "formality"]);
  });

  describe("chặn trước khi gọi AI", () => {
    it.each([
      ["không có consent", { consentGrant: null }],
      ["consent đã rút", { consentGrant: { grantId: "g1", purpose: "RECOGNITION", mediaIds: ["m-front"], issuedAt: 1, revokedAt: 5, userConfirmed: true } }],
      ["consent sai mục đích", { consentGrant: { grantId: "g1", purpose: "TRYON", mediaIds: ["m-front"], issuedAt: 1, revokedAt: null, userConfirmed: true } }],
      ["consent chưa xác nhận", { consentGrant: { grantId: "g1", purpose: "RECOGNITION", mediaIds: ["m-front"], issuedAt: 1, revokedAt: null, userConfirmed: false } }],
      ["consent không phủ ảnh", { consentGrant: { grantId: "g1", purpose: "RECOGNITION", mediaIds: ["other"], issuedAt: 1, revokedAt: null, userConfirmed: true } }],
    ])("%s ⇒ 403 CONSENT_REQUIRED", async (_label, overrides) => {
      const t = setup();
      const res = await t.post("/v2/recognition", recognitionRequest(overrides));
      expect(res.status).toBe(403);
      expect(await res.json()).toMatchObject({ error: { code: "CONSENT_REQUIRED" } });
      expect(t.model.calls).toHaveLength(0);
    });

    it("capability FRONT_ONLY + có ảnh sau ⇒ 400 UNSUPPORTED_CAPABILITY", async () => {
      const t = setup({ RECOGNITION_VIEWS: "FRONT_ONLY" });
      const res = await t.post("/v2/recognition", recognitionRequest({}, true));
      expect(res.status).toBe(400);
      expect(await res.json()).toMatchObject({ error: { code: "UNSUPPORTED_CAPABILITY" } });
      expect(t.model.calls).toHaveLength(0);
    });

    it("ảnh vượt 1 MiB ⇒ 413", async () => {
      const t = setup();
      const big = fakeImage("jpeg", 3, 1024 * 1024 + 10);
      const res = await t.post("/v2/recognition", recognitionRequest({ front: { view: "FRONT", mediaId: "m-front", mimeType: "image/jpeg", dataBase64: big } }));
      expect(res.status).toBe(413);
      expect(t.model.calls).toHaveLength(0);
    });

    it("mimeType không khớp bytes ⇒ 400", async () => {
      const t = setup();
      const res = await t.post("/v2/recognition", recognitionRequest({ front: { view: "FRONT", mediaId: "m-front", mimeType: "image/png", dataBase64: FRONT } }));
      expect(res.status).toBe(400);
      expect(t.model.calls).toHaveLength(0);
    });

    it("sha256 không khớp ⇒ 400; khớp ⇒ 200", async () => {
      const t = setup();
      const bad = await t.post("/v2/recognition", recognitionRequest({ front: { view: "FRONT", mediaId: "m-front", mimeType: "image/jpeg", dataBase64: FRONT, sha256: "0".repeat(64) } }));
      expect(bad.status).toBe(400);
      const bytes = Uint8Array.from(atob(FRONT), (c) => c.charCodeAt(0));
      t.model.respond(modelRecognition.frontOnly);
      const good = await t.post("/v2/recognition", recognitionRequest({ front: { view: "FRONT", mediaId: "m-front", mimeType: "image/jpeg", dataBase64: FRONT, sha256: await sha256Hex(bytes) } }));
      expect(good.status).toBe(200);
    });

    it("ảnh sau là bản sao ảnh trước ⇒ 400", async () => {
      const t = setup();
      const req = recognitionRequest({}, true);
      const res = await t.post("/v2/recognition", { ...req, back: { ...(req.back as object), dataBase64: FRONT } });
      expect(res.status).toBe(400);
      expect(t.model.calls).toHaveLength(0);
    });

    it("base64 hỏng ⇒ 400", async () => {
      const t = setup();
      const res = await t.post("/v2/recognition", recognitionRequest({ front: { view: "FRONT", mediaId: "m-front", mimeType: "image/jpeg", dataBase64: "@@@notbase64" } }));
      expect(res.status).toBe(400);
    });

    it("view sai slot ⇒ 400", async () => {
      const t = setup();
      const res = await t.post("/v2/recognition", recognitionRequest({ front: { view: "BACK", mediaId: "m-front", mimeType: "image/jpeg", dataBase64: FRONT } }));
      expect(res.status).toBe(400);
    });

    it("URL ảnh thay vì bytes ⇒ 400", async () => {
      const t = setup();
      const res = await t.post("/v2/recognition", recognitionRequest({ front: { view: "FRONT", mediaId: "m-front", mimeType: "image/jpeg", url: "https://evil.example/x.jpg" } }));
      expect(res.status).toBe(400);
    });

    it("kill switch recognition tắt ⇒ 503", async () => {
      const t = setup({ SERVICE_RECOGNITION: "off" });
      const res = await t.post("/v2/recognition", recognitionRequest());
      expect(res.status).toBe(503);
      expect(t.model.calls).toHaveLength(0);
    });
  });

  it("log không chứa base64 ảnh", async () => {
    const t = setup();
    t.model.respond(modelRecognition.frontOnly);
    await t.post("/v2/recognition", recognitionRequest());
    expect(JSON.stringify(t.logs)).not.toContain(FRONT.slice(0, 20));
  });
});
