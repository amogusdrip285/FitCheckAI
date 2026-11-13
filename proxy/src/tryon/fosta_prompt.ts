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

export const FOSTA_VTO_PROMPT_VERSION = "v2.1";

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

  const totalImages = 1 + (hasTop ? 1 : 0) + (hasBottom ? 1 : 0);
  const viewTag = view === "FRONT" ? "front-view" : "back-view";

  const parts: string[] = [
    `Create exactly one photorealistic ${viewTag} virtual try-on image using these ${totalImages} images.`,
  ];

  if (view === "FRONT") {
    const imageRefs: string[] = ["Image 1 is the person (front view)"];
    let idx = 2;
    if (hasTop) {
      imageRefs.push(`image ${idx} is the reference top`);
      idx += 1;
    }
    if (hasBottom) {
      imageRefs.push(`image ${idx} is the reference bottom`);
    }
    parts.push(`${imageRefs.join("; ")}.`);
  } else {
    const imageRefs: string[] = ["Image 1 is the person from behind (back view)"];
    let idx = 2;
    if (hasTop) {
      imageRefs.push(`image ${idx} is the reference top (back view)`);
      idx += 1;
    }
    if (hasBottom) {
      imageRefs.push(`image ${idx} is the reference bottom (back view)`);
    }
    parts.push(`${imageRefs.join("; ")}.`);
  }

  if (combined) {
    parts.push(
      "Edit the person to wear both the reference top and reference bottom with natural waist layering.",
    );
  } else if (hasTop) {
    parts.push(
      "Edit the person to wear only the reference top, keeping their existing bottom unchanged.",
    );
  } else {
    parts.push(
      "Edit the person to wear only the reference bottom, keeping their existing top unchanged.",
    );
  }

  const personPreservation =
    view === "FRONT"
      ? "Preserve the person's face, hairstyle, skin tone, body proportions, pose"
      : "Keep the person facing away, preserving their identity, face when visible, hairstyle, skin tone, body proportions, pose";

  if (mode === "ORIGINAL_PHOTO") {
    parts.push(
      `${personPreservation}, shoes, accessories, lighting and original background.`,
    );
  } else {
    parts.push(
      `${personPreservation}, shoes and accessories on a clean white studio background with soft even lighting.`,
    );
  }

  const garmentNoun = combined ? "garments'" : hasTop ? "top's" : "bottom's";
  parts.push(
    `Preserve the ${garmentNoun} color, cut, fabric, logo, printed text and patterns faithfully.`,
  );

  return parts.join(" ");
}
