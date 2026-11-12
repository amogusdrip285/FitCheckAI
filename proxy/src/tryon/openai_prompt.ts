// OWNER: Track C (Section 6 — Versioned OpenAI Virtual Try-On Prompt Template & Evaluation Rubric)

import type { Category, ImageView } from "./types";

export const OPENAI_VTO_PROMPT_VERSION = "openai-vto-edit-v1.0";

export interface OpenAiTryOnPromptInput {
  view: ImageView;
  category: Category;
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

function describeReplacementScope(category: Category): {
  garmentRole: string;
  replaceRegion: string;
  keepUnchangedRegion: string;
  cutDetailFocus: string;
} {
  switch (category) {
    case "TOP":
      return {
        garmentRole: "upper-body top garment",
        replaceRegion:
          "ONLY the upper-body top worn by the person in Image 1 with the exact top shown in Image 2",
        keepUnchangedRegion:
          "trousers/skirt/shorts, footwear, belt, bags, watch, jewelry, and all other unselected clothing items",
        cutDetailFocus:
          "collar shape, neckline, shoulder seam, sleeve length, cuff finish, hem drape, buttons/zippers",
      };
    case "OUTERWEAR":
      return {
        garmentRole: "outerwear jacket/coat/blazer",
        replaceRegion:
          "ONLY the outer layer on the upper body of the person in Image 1 with the exact outerwear garment shown in Image 2",
        keepUnchangedRegion:
          "trousers/skirt, footwear, inner base garment where naturally visible, and all unselected items",
        cutDetailFocus:
          "collar/lapel shape, sleeve length, button/zipper closure, pocket placement, structured silhouette",
      };
    case "BOTTOM":
      return {
        garmentRole: "lower-body bottom garment (trousers/jeans/skirt/shorts)",
        replaceRegion:
          "ONLY the lower-body bottom garment worn by the person in Image 1 with the exact bottom garment shown in Image 2",
        keepUnchangedRegion:
          "upper-body shirt/top/jacket, footwear, socks, and all other unselected clothing items",
        cutDetailFocus:
          "waistband placement, leg cut/width, inseam/hem length, pleats/seams, pocket stitching",
      };
    case "DRESS":
      return {
        garmentRole: "one-piece dress/jumpsuit",
        replaceRegion:
          "ONLY the main torso-and-lower-body outfit on the person in Image 1 with the exact one-piece dress shown in Image 2",
        keepUnchangedRegion:
          "footwear, hair, and any unselected accessories already present in Image 1",
        cutDetailFocus:
          "neckline/collar shape, sleeve length, waist cut, skirt/hem length, drape",
      };
    default:
      throw new Error(`Unsupported garment category '${category}' for OpenAI VTO prompt.`);
  }
}

export function buildOpenAiTryOnPrompt(input: OpenAiTryOnPromptInput): OpenAiBuiltPrompt {
  const scope = describeReplacementScope(input.category);
  const viewDescription =
    input.view === "BACK"
      ? "rear-facing full-body view (back of the person and back of the garment)"
      : "front-facing full-body view (front of the person and front of the garment)";

  const sanitizedColors = (input.colors ?? [])
    .map((c) => c.trim().toUpperCase())
    .filter((c) => SAFE_COLOR_TOKEN_PATTERN.test(c))
    .slice(0, 4);

  const colorHintLine =
    sanitizedColors.length > 0
      ? `- Expected primary garment color palette from catalog metadata: ${sanitizedColors.join(", ")} (match Image 2 faithfully).`
      : "- Match the exact garment color and tonal shading visible in Image 2.";

  const lines = [
    `[PROMPT_VERSION: ${OPENAI_VTO_PROMPT_VERSION}]`,
    "Task: Photorealistic 2D Virtual Try-On image edit.",
    "",
    "Reference Image Roles:",
    `- Image 1 (first attached image): Base photograph of the real person (${viewDescription}). Use Image 1 as the base image.`,
    `- Image 2 (second attached image): Reference photograph of the ${scope.garmentRole} (${viewDescription}). Use Image 2 strictly as the clothing reference; ignore any hanger, hand holding the clothes, flat-lay surface, or background clutter inside Image 2.`,
    "",
    "Editing Instructions:",
    `- Replace ${scope.replaceRegion}, fitting it naturally onto the person's existing body posture in Image 1.`,
    ...(input.view === "FRONT"
      ? ["- Preserve the person's exact identity and facial appearance from Image 1."]
      : ["- Preserve the person's exact head shape, rear hair appearance, and identity cues from Image 1 without turning the person around."]),
    "- Preserve the person's natural body proportions, height-to-width ratio, and anatomy.",
    "- Preserve the exact pose, arm/hand positions, leg stance, and camera framing of Image 1.",
    "- Preserve hairstyle and hair color from Image 1.",
    "- Preserve natural skin tone and skin texture from Image 1.",
    "- Preserve the original scene background, floor, shadows, and ambient lighting of Image 1 as much as possible.",
    "",
    "Garment Fidelity Requirements (from Image 2):",
    colorHintLine,
    `- Preserve exact ${scope.cutDetailFocus}.`,
    "- Preserve every visible logo, brand mark, emblem, and badge in its exact shape, scale, and placement.",
    "- Preserve every visible printed text, typography, lettering, and graphic print without misspelling or inventing characters.",
    "- Preserve stripes, checks, embroidery, knit/weave texture, and garment patterns.",
    "",
    "Strict Negative Constraints:",
    "- Do NOT create a different person or alter facial features.",
    `- Do NOT modify clothing items that are not selected (${scope.keepUnchangedRegion}).`,
    "- Do NOT add unrelated accessories, hats, sunglasses, jewelry, bags, or extra layers.",
    "- Output a single, clean, natural photorealistic photograph with realistic fabric folds and contact shadows.",
  ];

  return {
    promptVersion: OPENAI_VTO_PROMPT_VERSION,
    category: input.category,
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
