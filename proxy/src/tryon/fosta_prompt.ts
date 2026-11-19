// OWNER: Track C (Experimental FOSTA Luna `gpt-6-luna` Composable 4-Slot Prompt Matrix v2.3)
// Adapted into strict TypeScript module supporting TOP, BOTTOM, HAT, and SHOES
// (15 non-empty subsets x 2 modes x [1 FRONT + optional HAT/SHOES rear-inference combinations in BACK] = 100 prompts <= 620 chars).
// Back, bottom-only, hat, shoes, and multi-item combinations remain UNVALIDATED_EXPERIMENTAL until live AI quality gates pass.

import { UpstreamDeterministicError } from "./providers";
import type {
  Category,
  FostaGarmentKind,
  ImageView,
  TryOnPresentationMode,
} from "./types";

export const FOSTA_VTO_PROMPT_VERSION = "v2.3";
export const MAX_FOSTA_PROMPT_LENGTH = 620;

export const CANONICAL_FOSTA_GARMENT_ORDER: readonly FostaGarmentKind[] = [
  "TOP",
  "BOTTOM",
  "HAT",
  "SHOES",
] as const;

export type FostaValidationStatus =
  | "POC_VALIDATED_SINGLE_EXAMPLE"
  | "UNVALIDATED_EXPERIMENTAL";

export const FOSTA_EXPERIMENTAL_CAPABILITIES: Readonly<{
  frontTop: FostaValidationStatus;
  studioTop: FostaValidationStatus;
  back: FostaValidationStatus;
  bottomOnly: FostaValidationStatus;
  topBottom: FostaValidationStatus;
  hat: FostaValidationStatus;
  shoes: FostaValidationStatus;
  multiItem: FostaValidationStatus;
}> = Object.freeze({
  frontTop: "POC_VALIDATED_SINGLE_EXAMPLE",
  studioTop: "POC_VALIDATED_SINGLE_EXAMPLE",
  back: "UNVALIDATED_EXPERIMENTAL",
  bottomOnly: "UNVALIDATED_EXPERIMENTAL",
  topBottom: "UNVALIDATED_EXPERIMENTAL",
  hat: "UNVALIDATED_EXPERIMENTAL",
  shoes: "UNVALIDATED_EXPERIMENTAL",
  multiItem: "UNVALIDATED_EXPERIMENTAL",
});

const GARMENT_NOUN: Record<FostaGarmentKind, string> = {
  TOP: "top",
  BOTTOM: "bottom",
  HAT: "hat",
  SHOES: "shoes",
};

const KEEP_NOUN: Record<FostaGarmentKind, string> = {
  TOP: "top",
  BOTTOM: "bottom",
  HAT: "headwear",
  SHOES: "shoes",
};

export function mapCategoryToFostaGarmentKind(
  category: Category | string,
  slotOverride?: FostaGarmentKind | string | null,
): FostaGarmentKind | null {
  if (
    slotOverride === "TOP" ||
    slotOverride === "BOTTOM" ||
    slotOverride === "HAT" ||
    slotOverride === "SHOES"
  ) {
    return slotOverride;
  }
  switch (category) {
    case "TOP":
    case "OUTERWEAR":
      return "TOP";
    case "BOTTOM":
      return "BOTTOM";
    case "HAT":
    case "ACCESSORY":
      return "HAT";
    case "SHOES":
      return "SHOES";
    default:
      return null;
  }
}

export type OptionalRearGarmentKind = "HAT" | "SHOES";

export interface BuildFostaPromptInput {
  mode?: TryOnPresentationMode;
  view?: ImageView;
  garmentKinds?: readonly FostaGarmentKind[];
  inferredRearKinds?: readonly FostaGarmentKind[];
}

/**
 * Composes the deterministic `v2.3` FOSTA prompt across all 100 valid combinations:
 * - 2 presentation modes: `ORIGINAL_PHOTO` | `STUDIO_WHITE`
 * - 15 non-empty subsets of `[TOP, BOTTOM, HAT, SHOES]`
 * - `FRONT` (30) + `BACK` with optional rear-view inference for `HAT` / `SHOES` (70) = 100 prompts
 *
 * Canonical reference image numbering:
 * - `Image 1` = base `PERSON` photograph
 * - `image 2..N` = selected items actually sent in strict `TOP -> BOTTOM -> HAT -> SHOES` order
 *
 * Every generated prompt is guaranteed <= 620 characters to avoid upstream length/timeout issues.
 */
export function buildFostaTryOnPrompt({
  mode = "ORIGINAL_PHOTO",
  view = "FRONT",
  garmentKinds = ["TOP"],
  inferredRearKinds = [],
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

  const seen = new Set<FostaGarmentKind>();
  for (const kind of garmentKinds) {
    if (
      kind !== "TOP" &&
      kind !== "BOTTOM" &&
      kind !== "HAT" &&
      kind !== "SHOES"
    ) {
      throw new UpstreamDeterministicError(
        "INVALID_REQUEST",
        `Unsupported FOSTA garment kind: '${String(kind)}'. Choose TOP, BOTTOM, HAT, or SHOES.`,
      );
    }
    seen.add(kind);
  }
  if (seen.size === 0) {
    throw new UpstreamDeterministicError(
      "NEEDS_INPUT",
      "At least one garment kind (TOP, BOTTOM, HAT, or SHOES) is required for FOSTA Virtual Try-On.",
    );
  }

  const inferredSet = new Set<OptionalRearGarmentKind>();
  if (view === "BACK") {
    for (const infKind of inferredRearKinds) {
      if (infKind === "TOP" || infKind === "BOTTOM") {
        throw new UpstreamDeterministicError(
          "NEEDS_INPUT",
          `${infKind} requires a real rear photo in BACK view.`,
        );
      }
      if ((infKind === "HAT" || infKind === "SHOES") && seen.has(infKind)) {
        inferredSet.add(infKind);
      }
    }
  }

  const active = CANONICAL_FOSTA_GARMENT_ORDER.filter((k) => seen.has(k));
  const hasTop = seen.has("TOP");
  const hasBottom = seen.has("BOTTOM");
  const hasHat = seen.has("HAT");
  const hasShoes = seen.has("SHOES");
  const compact = inferredSet.size > 0;

  const viewLabel = view === "BACK" ? "back-view" : "front-view";
  const personDesc =
    view === "BACK"
      ? compact
        ? "the person from behind"
        : "the person from behind (back view)"
      : "the person (front view)";

  const imgClauses = active.map((k, idx) =>
    idx === 0
      ? `image 2 is the reference ${GARMENT_NOUN[k]}`
      : `image ${idx + 2} is the ${GARMENT_NOUN[k]}`,
  );

  const actions: string[] = [];
  if (hasTop && hasBottom) {
    actions.push(
      compact
        ? "wear the top and bottom naturally"
        : "wear the top and bottom with natural waist layering",
    );
  } else if (hasTop) {
    actions.push("wear the reference top");
  } else if (hasBottom) {
    actions.push("wear the reference bottom");
  }

  if (hasHat) {
    actions.push(
      compact
        ? "wear the hat naturally with unoccluded hair"
        : view === "BACK"
          ? "wear the hat naturally on the head with unoccluded hair visible"
          : "wear the hat naturally on the head, keeping face and unoccluded hair visible",
    );
  }

  if (hasShoes) {
    actions.push(
      compact
        ? "replace only the shoes naturally"
        : "replace only the shoes, keeping stance and ground natural",
    );
  }

  const keepUnchanged = CANONICAL_FOSTA_GARMENT_ORDER.filter(
    (k) => !seen.has(k),
  ).map((k) => KEEP_NOUN[k]);
  const keepClause =
    keepUnchanged.length > 0
      ? ` Keep existing ${keepUnchanged.join("/")} unchanged.`
      : "";

  const inferredNouns = (["HAT", "SHOES"] as const)
    .filter((k) => inferredSet.has(k))
    .map((k) => GARMENT_NOUN[k]);
  const inferClause =
    inferredNouns.length > 0
      ? ` No rear photo for the ${inferredNouns.join("/")}: infer a plausible rear view consistent with the front photo.`
      : "";

  const detailDir = view === "BACK" ? "rear" : "front";
  const poseClause =
    view === "BACK"
      ? compact
        ? "Keep the person facing away, preserving identity, hair, skin, body, pose"
        : "Keep the person facing away, preserving identity, hair, skin tone, body, pose"
      : "Preserve identity, face, hair, skin tone, body proportions, pose";
  const bgClause =
    mode === "STUDIO_WHITE"
      ? compact
        ? " on a white studio background with soft lighting."
        : " on a clean white studio background with soft even lighting."
      : ", lighting, and original background.";

  const prompt =
    `Create one photorealistic ${viewLabel} virtual try-on image. ` +
    `Image 1 is ${personDesc}; ${imgClauses.join(", ")}. ` +
    `Dress the person to ${actions.join("; ")}, matching exact colors, textures, logos, and ${detailDir} details.${inferClause}${keepClause} ` +
    `${poseClause}${bgClause}`;

  if (prompt.length > MAX_FOSTA_PROMPT_LENGTH) {
    throw new UpstreamDeterministicError(
      "INVALID_REQUEST",
      `FOSTA prompt exceeded safety ceiling (${prompt.length} > ${MAX_FOSTA_PROMPT_LENGTH}).`,
    );
  }
  return prompt;
}
