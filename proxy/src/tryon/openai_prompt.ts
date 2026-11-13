// OWNER: Track C (Section 6 — Versioned OpenAI Virtual Try-On Prompt Template & Evaluation Rubric)

import type { Category, FostaGarmentKind, ImageView } from "./types";

export const OPENAI_VTO_PROMPT_VERSION = "openai-vto-edit-v1.1";

export interface OpenAiTryOnPromptGarment {
  category: Category;
  slot?: FostaGarmentKind;
  colors?: readonly string[];
}

export interface OpenAiTryOnPromptInput {
  view: ImageView;
  category?: Category;
  garments?: readonly OpenAiTryOnPromptGarment[];
  /** Optional sanitized color tokens from Track A/B wardrobe enum (e.g. ["NAVY", "WHITE"]). */
  colors?: readonly string[];
  /** True when the input JPEG/WebP carried a non-1 EXIF orientation flag before server intake. */
  personExifOrientation?: number;
  garmentExifOrientation?: number;
}

export interface OpenAiBuiltPrompt {
  promptVersion: string;
  category: Category;
  view: ImageView;
  prompt: string;
}

const SAFE_COLOR_TOKEN_PATTERN = /^[A-Za-z0-9_-]{2,24}$/;

interface ReplacementScope {
  slotKey: "TOP" | "BOTTOM" | "DRESS" | "HAT" | "SHOES";
  garmentRole: string;
  replaceRegion: string;
  cutDetailFocus: string;
}

function describeReplacementScope(
  category: Category,
  slotOverride?: FostaGarmentKind,
  imageNumber = 2,
): ReplacementScope {
  const imgRef = `Image ${imageNumber}`;
  if (slotOverride === "HAT" || category === "ACCESSORY") {
    return {
      slotKey: "HAT",
      garmentRole: "hat / headwear item",
      replaceRegion: `place the exact hat/headwear shown in ${imgRef} naturally on the person's head, keeping face and unoccluded hair visible`,
      cutDetailFocus: "crown shape, brim curvature, stitching, emblem, and front/rear silhouette",
    };
  }
  if (slotOverride === "SHOES" || category === "SHOES") {
    return {
      slotKey: "SHOES",
      garmentRole: "shoes / footwear pair",
      replaceRegion: `replace ONLY the shoes/footwear worn by the person in Image 1 with the exact footwear shown in ${imgRef}, keeping stance and ground contact natural`,
      cutDetailFocus: "toe shape, sole thickness, lacing/closure, heel height, and panel details",
    };
  }
  switch (category) {
    case "TOP":
      return {
        slotKey: "TOP",
        garmentRole: "upper-body top garment",
        replaceRegion: `replace the upper-body top worn by the person in Image 1 with the exact top shown in ${imgRef}`,
        cutDetailFocus: "collar shape, neckline, shoulder seam, sleeve length, cuff finish, hem drape, buttons/zippers",
      };
    case "OUTERWEAR":
      return {
        slotKey: "TOP",
        garmentRole: "outerwear jacket/coat/blazer",
        replaceRegion: `replace the outer layer on the upper body of the person in Image 1 with the exact outerwear garment shown in ${imgRef}`,
        cutDetailFocus: "collar/lapel shape, sleeve length, button/zipper closure, pocket placement, structured silhouette",
      };
    case "BOTTOM":
      return {
        slotKey: "BOTTOM",
        garmentRole: "lower-body bottom garment (trousers/jeans/skirt/shorts)",
        replaceRegion: `replace the lower-body bottom garment worn by the person in Image 1 with the exact bottom garment shown in ${imgRef}`,
        cutDetailFocus: "waistband placement, leg cut/width, inseam/hem length, pleats/seams, pocket stitching",
      };
    case "DRESS":
      return {
        slotKey: "DRESS",
        garmentRole: "one-piece dress/jumpsuit",
        replaceRegion: `replace the main torso-and-lower-body outfit on the person in Image 1 with the exact one-piece dress shown in ${imgRef}`,
        cutDetailFocus: "neckline/collar shape, sleeve length, waist cut, skirt/hem length, drape",
      };
    default:
      throw new Error(`Unsupported garment category '${category}' for OpenAI VTO prompt.`);
  }
}

export function buildOpenAiTryOnPrompt(input: OpenAiTryOnPromptInput): OpenAiBuiltPrompt {
  const effectiveGarments: readonly OpenAiTryOnPromptGarment[] =
    input.garments && input.garments.length > 0
      ? input.garments
      : input.category
        ? [{ category: input.category, colors: input.colors }]
        : [];
  if (effectiveGarments.length === 0) {
    throw new Error("At least one garment is required for OpenAI VTO prompt.");
  }

  const scopes = effectiveGarments.map((g, idx) => ({
    garment: g,
    imageNum: idx + 2,
    scope: describeReplacementScope(g.category, g.slot, idx + 2),
  }));

  const selectedKeys = new Set(scopes.map((s) => s.scope.slotKey));
  const unchangedItems: string[] = [];
  if (!selectedKeys.has("TOP") && !selectedKeys.has("DRESS")) {
    unchangedItems.push("upper-body top");
  }
  if (!selectedKeys.has("BOTTOM") && !selectedKeys.has("DRESS")) {
    unchangedItems.push("trousers/skirt/shorts");
  }
  if (!selectedKeys.has("HAT")) {
    unchangedItems.push("headwear/hair accessories");
  }
  if (!selectedKeys.has("SHOES")) {
    unchangedItems.push("footwear");
  }
  unchangedItems.push("belt, bags, watch, jewelry, and all other unselected items");

  const viewDescription =
    input.view === "BACK"
      ? "rear-facing full-body view (back of the person and back of the garment)"
      : "front-facing full-body view (front of the person and front of the garment)";

  const roleLines = [
    `- Image 1 (first attached image): Base photograph of the real person (${viewDescription}). Use Image 1 as the base image.`,
    ...scopes.map(
      (s) =>
        `- Image ${s.imageNum}: Reference photograph of the ${s.scope.garmentRole} (${viewDescription}). Use Image ${s.imageNum} strictly as the item reference; ignore any hanger, hand, flat-lay surface, or background clutter.`,
    ),
  ];

  const colorLines = scopes
    .map((s) => {
      const tokens = (s.garment.colors ?? input.colors ?? [])
        .map((c) => c.trim().toUpperCase())
        .filter((c) => SAFE_COLOR_TOKEN_PATTERN.test(c))
        .slice(0, 4);
      return tokens.length > 0
        ? `- Match Image ${s.imageNum} palette (${tokens.join(", ")}) faithfully.`
        : `- Match the exact color and tonal shading visible in Image ${s.imageNum}.`;
    });

  const cutFocusCombined = Array.from(new Set(scopes.map((s) => s.scope.cutDetailFocus))).join("; ");

  const lines = [
    `[PROMPT_VERSION: ${OPENAI_VTO_PROMPT_VERSION}]`,
    "Task: Photorealistic 2D Virtual Try-On image edit.",
    "",
    "Reference Image Roles:",
    ...roleLines,
    "",
    "Editing Instructions:",
    ...scopes.map((s) => `- ${s.scope.replaceRegion}, fitting naturally onto the person's body posture in Image 1.`),
    ...(input.view === "FRONT"
      ? ["- Preserve the person's exact identity and facial appearance from Image 1."]
      : ["- Preserve the person's exact head shape, rear hair appearance, and identity cues from Image 1 without turning the person around."]),
    "- Preserve the person's natural body proportions, height-to-width ratio, and anatomy.",
    "- Preserve the exact pose, arm/hand positions, leg stance, and camera framing of Image 1.",
    "- Preserve hairstyle and hair color from Image 1.",
    "- Preserve natural skin tone and skin texture from Image 1.",
    "- Preserve the original scene background, floor, shadows, and ambient lighting of Image 1 as much as possible.",
    "",
    "Garment Fidelity Requirements:",
    ...colorLines,
    `- Preserve exact ${cutFocusCombined}.`,
    "- Preserve every visible logo, brand mark, emblem, and badge in its exact shape, scale, and placement.",
    "- Preserve every visible printed text, typography, lettering, and graphic print without misspelling or inventing characters.",
    "- Preserve stripes, checks, embroidery, knit/weave texture, and patterns.",
    "",
    "Strict Negative Constraints:",
    "- Do NOT create a different person or alter facial features.",
    `- Do NOT modify items that are not selected (${unchangedItems.join(", ")}).`,
    "- Do NOT add unrelated extra items, sunglasses, jewelry, bags, or extra layers.",
    "- Output a single, clean, natural photorealistic photograph with realistic contact shadows.",
  ];

  return {
    promptVersion: OPENAI_VTO_PROMPT_VERSION,
    category: scopes[0]!.garment.category,
    view: input.view,
    prompt: lines.join("\n"),
  };
}

/**
 * Explicit technical disclosure of general image-editing model risks (Section 3 & Section 6).
 * OpenAI GPT Image models are general-purpose image editors, NOT dedicated 3D/garment-warping VTO engines.
 */
export const OPENAI_VTO_KNOWN_LIMITATIONS: readonly string[] = [
  "LOGO_DRIFT_RISK: General image-editing models may subtly redraw or shift small chest/sleeve logos.",
  "PRINTED_TEXT_FIDELITY_RISK: Fine or curved printed lettering on shirts may suffer character distortion.",
  "TEXTURE_APPROXIMATION_RISK: Micro-weave or sheer fabric textures may be re-synthesized rather than pixel-copied.",
  "SILHOUETTE_DRIFT_RISK: Body proportions or fit looseness are visually estimated and NEVER represent physical sizing.",
  "IDENTITY_AND_BACKGROUND_DRIFT_RISK: Subtle lighting, background detail, or facial micro-feature shifts can occur and require human review.",
] as const;

export interface OpenAiVtoEvaluationRubric {
  readonly promptVersion: string;
  readonly status: "UNREVIEWED";
  readonly scale: "1_TO_5_PASS_GE_4";
  readonly criteria: {
    readonly identityPreservation: null;
    readonly garmentPreservation: null;
    readonly logoPreservation: null;
    readonly textFidelity: null;
    readonly anatomicalConsistency: null;
    readonly photorealism: null;
  };
}

export function createUnreviewedOpenAiRubric(): OpenAiVtoEvaluationRubric {
  return {
    promptVersion: OPENAI_VTO_PROMPT_VERSION,
    status: "UNREVIEWED",
    scale: "1_TO_5_PASS_GE_4",
    criteria: {
      identityPreservation: null,
      garmentPreservation: null,
      logoPreservation: null,
      textFidelity: null,
      anatomicalConsistency: null,
      photorealism: null,
    },
  };
}
