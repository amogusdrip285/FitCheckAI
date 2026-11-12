// OWNER: Track C (Experimental FOSTA Luna `gpt-6-luna` 12-Branch Prompt Matrix)
// Adapted from `fitcheck_fosta_vto_v2` integration kit into strict Cloudflare/TypeScript module.
// Back, bottom-only, and top+bottom combinations remain UNVALIDATED_EXPERIMENTAL until live AI quality gates pass.

import { UpstreamDeterministicError } from "./providers";
import type {
  Category,
  FostaGarmentKind,
  ImageView,
  TryOnPresentationMode,
} from "./types";

export const FOSTA_VTO_PROMPT_VERSION = "v2.0";

export type FostaValidationStatus =
  | "POC_VALIDATED_SINGLE_EXAMPLE"
  | "UNVALIDATED_EXPERIMENTAL";

export const FOSTA_EXPERIMENTAL_CAPABILITIES: Readonly<{
  frontTop: FostaValidationStatus;
  studioTop: FostaValidationStatus;
  back: FostaValidationStatus;
  bottomOnly: FostaValidationStatus;
  topBottom: FostaValidationStatus;
}> = Object.freeze({
  frontTop: "POC_VALIDATED_SINGLE_EXAMPLE",
  studioTop: "POC_VALIDATED_SINGLE_EXAMPLE",
  back: "UNVALIDATED_EXPERIMENTAL",
  bottomOnly: "UNVALIDATED_EXPERIMENTAL",
  topBottom: "UNVALIDATED_EXPERIMENTAL",
});

export function mapCategoryToFostaGarmentKind(
  category: Category,
): FostaGarmentKind | null {
  if (category === "TOP") {
    return "TOP";
  }
  if (category === "BOTTOM") {
    return "BOTTOM";
  }
  return null;
}

export interface BuildFostaPromptInput {
  mode?: TryOnPresentationMode;
  view?: ImageView;
  garmentKinds?: readonly FostaGarmentKind[];
}

/**
 * Builds the deterministic versioned prompt across all 12 combinations:
 * - 2 presentation modes: `ORIGINAL_PHOTO` | `STUDIO_WHITE`
 * - 2 views: `FRONT` | `BACK`
 * - 3 garment combos: `TOP` | `BOTTOM` | `TOP+BOTTOM`
 *
 * Reference image numbering is locked to:
 * - `REFERENCE IMAGE 1` = base `PERSON` photograph
 * - `REFERENCE IMAGE 2` = `TOP` (if selected) else `BOTTOM`
 * - `REFERENCE IMAGE 3` = `BOTTOM` (only when both `TOP` and `BOTTOM` are selected)
 */
export function buildFostaTryOnPrompt({
  mode = "ORIGINAL_PHOTO",
  view = "FRONT",
  garmentKinds = ["TOP"],
}: BuildFostaPromptInput = {}): string {
  if (mode !== "ORIGINAL_PHOTO" && mode !== "STUDIO_WHITE") {
    throw new UpstreamDeterministicError(
      "INVALID_REQUEST",
      `Unsupported FOSTA presentation mode: '${String(mode)}'.`,
    );
  }
  if (view !== "FRONT" && view !== "BACK") {
    throw new UpstreamDeterministicError(
      "INVALID_REQUEST",
      `Unsupported FOSTA view: '${String(view)}'.`,
    );
  }

  const uniqueKinds: FostaGarmentKind[] = [];
  for (const kind of garmentKinds) {
    if (kind !== "TOP" && kind !== "BOTTOM") {
      throw new UpstreamDeterministicError(
        "INVALID_REQUEST",
        `Unsupported FOSTA garment kind: '${String(kind)}'. Choose TOP, BOTTOM, or TOP+BOTTOM.`,
      );
    }
    if (!uniqueKinds.includes(kind)) {
      uniqueKinds.push(kind);
    }
  }
  if (uniqueKinds.length === 0) {
    throw new UpstreamDeterministicError(
      "NEEDS_INPUT",
      "At least one garment kind (TOP or BOTTOM) is required for FOSTA Virtual Try-On.",
    );
  }

  const hasTop = uniqueKinds.includes("TOP");
  const hasBottom = uniqueKinds.includes("BOTTOM");
  const combined = hasTop && hasBottom;

  const instructions: string[] = [
    `FITCHECK_VTO_PROMPT_VERSION=${FOSTA_VTO_PROMPT_VERSION}.`,
    "Produce exactly ONE photorealistic virtual try-on image, not text, a product photo, a collage, or a before/after grid.",
    "REFERENCE IMAGE 1 is the base PERSON photograph.",
    "Preserve the same identifiable person as closely as possible: identity, face when visible, hairstyle, skin tone, body proportions and natural anatomy.",
    "Preserve garment colors, silhouettes, neckline/waistline, fabric textures, logos, labels and printed text faithfully; do not invent or alter lettering.",
    "Create believable fit, folds, shadows, occlusion and lighting. Do not add or remove accessories without instruction.",
  ];

  let garmentImageIndex = 2;
  if (hasTop) {
    instructions.push(
      `REFERENCE IMAGE ${garmentImageIndex} is the TOP garment. Replace the person's existing top with that exact item.`,
    );
    garmentImageIndex += 1;
  } else {
    instructions.push(
      "Leave the existing top completely unchanged, including all logos, sleeves, straps, colors and layering.",
    );
  }

  if (hasBottom) {
    instructions.push(
      `REFERENCE IMAGE ${garmentImageIndex} is the BOTTOM garment. Replace the person's existing lower garment with that exact item, preserving waistline, silhouette, length and material.`,
    );
    garmentImageIndex += 1;
  } else {
    instructions.push(
      "Leave the existing lower garment completely unchanged, including its shape, texture and color.",
    );
  }

  if (combined) {
    instructions.push(
      "Apply BOTH referenced garments together in the SAME generated output image. Preserve their individual identities and details. Do not keep the original top or bottom where they must be replaced. Ensure realistic layering at the waist.",
    );
  }

  if (view === "BACK") {
    instructions.push(
      "VIEW=BACK. The PERSON image is a genuine rear-view reference, and all GARMENT images are rear-view references. Show the same person from BEHIND, preserving rear hairstyle, shoulders, silhouette, body orientation and back-specific prints. Do not rotate the person to face the camera, fabricate a visible front face, or copy a front logo onto the back.",
    );
  } else {
    instructions.push(
      "VIEW=FRONT. Show the person from the front, preserving face and front-specific garment details. Do not invent a rear view.",
    );
  }

  if (mode === "ORIGINAL_PHOTO") {
    instructions.push(
      "MODE=ORIGINAL_PHOTO. Keep the original person photograph as the base composition: camera angle, posture, framing, surrounding clothing, background and ambient lighting. Only change the selected garment regions.",
    );
  } else {
    instructions.push(
      "MODE=STUDIO_WHITE. Re-stage the SAME person in an upright full-body standing pose, head and feet visible, arms relaxed naturally along the body and not covering the garments, level camera, clean seamless white/ivory backdrop, soft even professional studio lighting. You may alter the pose/background to achieve studio consistency, but keep the same person and clothing details. This is a fashion visualization, not a body measurement or reliable physical fit prediction.",
    );
    if (view === "BACK") {
      instructions.push(
        "For this studio REAR view, the subject must face AWAY from the camera and the full back of the clothing must be visible.",
      );
    }
  }

  return instructions.join(" ");
}
