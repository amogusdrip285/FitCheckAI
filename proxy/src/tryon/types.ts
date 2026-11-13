// OWNER: Track C (C-207 — Server adapter VTO & job idempotency)
// Contracts: docs/execution/CONTRACTS.md (CT-06, CT-07)

export type ImageView = "FRONT" | "BACK";

export type Category =
  | "TOP"
  | "BOTTOM"
  | "DRESS"
  | "OUTERWEAR"
  | "SHOES"
  | "ACCESSORY"
  | "UNKNOWN";

export type FeasibilityDecision =
  | "UNKNOWN"
  | "NO_GO"
  | "GO_FRONT_ONLY"
  | "GO_DUAL";

export type TryOnSource = "AI_LIVE" | "FAKE";

export type TryOnPresentationMode = "ORIGINAL_PHOTO" | "STUDIO_WHITE";

export type FostaGarmentKind = "TOP" | "BOTTOM";

export type ViewProcessState =
  | "NOT_REQUESTED"
  | "QUEUED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "EXPIRED"
  | "CANCELLED";

export type QualityReviewState = "UNREVIEWED" | "ACCEPTED" | "REJECTED";

export type TryOnJobStatus =
  | "QUEUED"
  | "RUNNING"
  | "PARTIAL"
  | "SUCCEEDED"
  | "FAILED"
  | "TIMED_OUT"
  | "UNKNOWN_SUBMISSION"
  | "CANCELLED"
  | "DELETED";

export type RemoteDeletionState =
  | "NOT_UPLOADED"
  | "BASE64_EPHEMERAL_60M"
  | "EXPIRES_AUTOMATICALLY_72H"
  | "PENDING_BEST_EFFORT"
  | "UNSUPPORTED"
  | "UNKNOWN";

export type TryOnErrorCode =
  | "INVALID_REQUEST"
  | "CONSENT_REQUIRED"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "PAYLOAD_TOO_LARGE"
  | "RATE_LIMITED"
  | "INSUFFICIENT_QUOTA"
  | "BUDGET_EXCEEDED"
  | "STALE_INPUT"
  | "CONFLICT"
  | "NEEDS_INPUT"
  | "UNSUPPORTED_CAPABILITY"
  | "INVALID_AI_OUTPUT"
  | "NETWORK"
  | "UPSTREAM_TIMEOUT"
  | "UPSTREAM_ERROR"
  | "IO"
  | "CANCELLED"
  | "EXPIRED";

export type SupportedTryOnMime = "image/jpeg" | "image/png" | "image/webp";

export interface TryOnTypedError {
  code: TryOnErrorCode;
  message: string;
  requestId: string;
  retryable: boolean;
}

export interface TryOnErrorEnvelope {
  error: TryOnTypedError;
}

export interface WireImageInput {
  mediaId: string;
  view: ImageView;
  mimeType: SupportedTryOnMime;
  width: number;
  height: number;
  contentHash: string;
  revision: number;
  /** Raw base64 bytes (no data: URL prefix or remote URL). */
  base64Data: string;
}

export interface WireImagePair {
  front?: WireImageInput | null;
  back?: WireImageInput | null;
}

export interface OutfitItemSummary {
  itemId: string;
  revision: number;
  category: Category;
  colors?: string[];
}

export interface OutfitSnapshotWire {
  outfitId: string;
  wardrobeSnapshotId: string;
  items: OutfitItemSummary[];
}

export interface ConsentGrantWire {
  id: string;
  purpose: "RECOGNITION" | "TRYON";
  providerId: string;
  providerConfigVersion: string;
  selectedMediaIds: string[];
  allowedViews: ImageView[];
  requestGeneration: number;
  confirmedByUser: boolean;
  issuedAtEpochMillis: number;
  revokedAtEpochMillis?: number | null;
}

export interface TryOnSubmitWireRequest {
  schemaVersion: number;
  requestId: string;
  idempotencyKey: string;
  outfitSnapshot: OutfitSnapshotWire;
  profileRevision: number;
  personImages: WireImagePair;
  garmentImages: Record<string, WireImagePair>;
  requestedViews: ImageView[];
  selectedItemIds: string[];
  presentationMode?: TryOnPresentationMode;
  consentGrant: ConsentGrantWire;
  inputGeneration: number;
  providerConfigVersion: string;
}

export interface ViewOutputPayload {
  mimeType: SupportedTryOnMime;
  base64Data: string;
  contentHash: string;
  width: number;
  height: number;
}

export interface ViewResultWire {
  view: ImageView;
  state: ViewProcessState;
  output?: ViewOutputPayload | null;
  error?: TryOnTypedError | null;
  qualityReview: QualityReviewState;
}

export interface TryOnJobSubmitResponse {
  jobId: string;
  requestId: string;
  status: TryOnJobStatus;
}

export interface TryOnResultWire {
  jobId: string;
  requestId: string;
  outfitId: string;
  inputGeneration: number;
  inputRevisions: Record<string, number>;
  status: TryOnJobStatus;
  views: Partial<Record<ImageView, ViewResultWire>>;
  appliedItemIds: string[];
  unappliedItemIds: string[];
  presentationMode?: TryOnPresentationMode;
  providerId?: string;
  providerConfigVersion: string;
  createdAt: number;
  expiresAt: number | null;
  source: TryOnSource;
  warnings: string[];
  remoteDeletionState: RemoteDeletionState;
}

export interface CancellationReceiptWire {
  jobId: string;
  status: TryOnJobStatus;
  cancelledAt: number;
  upstreamCancelAttempted: boolean;
  upstreamCancelConfirmed: boolean;
  refundGuaranteed: false;
  message: string;
}

export interface DeletionReceiptWire {
  jobId: string;
  status: "DELETED";
  deletedAt: number;
  localOutputsDeleted: boolean;
  lateCompletionSuppressed: boolean;
  remoteDeletionState: RemoteDeletionState;
  message: string;
}

export interface StoredChildViewJob {
  view: ImageView;
  state: ViewProcessState;
  upstreamPredictionId: string | null;
  output: ViewOutputPayload | null;
  error: TryOnTypedError | null;
  qualityReview: QualityReviewState;
}

export interface StoredTryOnJobRecord {
  jobId: string;
  sessionId: string;
  requestId: string;
  idempotencyScopeKey: string;
  payloadHash: string;
  outfitId: string;
  inputGeneration: number;
  inputRevisions: Record<string, number>;
  status: TryOnJobStatus;
  requestedViews: ImageView[];
  views: Partial<Record<ImageView, StoredChildViewJob>>;
  appliedItemIds: string[];
  unappliedItemIds: string[];
  presentationMode?: TryOnPresentationMode;
  providerId?: string;
  providerConfigVersion: string;
  createdAt: number;
  deadlineAt: number;
  expiresAt: number | null;
  deletedAt: number | null;
  cancelledAt: number | null;
  source: TryOnSource;
  warnings: string[];
  remoteDeletionState: RemoteDeletionState;
}

export interface IdempotencyReservationResult {
  kind: "CREATED" | "EXISTING_SAME_PAYLOAD" | "CONFLICT_DIFFERENT_PAYLOAD" | "ACTIVE_JOB_EXISTS";
  record: StoredTryOnJobRecord;
}

export interface LiveSpendReservationParams {
  requestUnits: number;
  maxRequests: number;
  estimatedUsd: number;
  maxEstimatedUsd: number;
}

export interface LiveSpendReservationResult {
  allowed: boolean;
  reason?: "MAX_REQUESTS" | "MAX_USD";
  usedRequests: number;
  usedEstimatedUsd: number;
}

export interface DurableTryOnJobStore {
  /**
   * True ONLY when backed by a real cross-isolate durable store (e.g. Cloudflare Durable Object / transactional DB,
   * or an atomic local disk ledger for single-node local POC).
   * Ephemeral Worker memory stores MUST set isDurable = false so AI_LIVE mode rejects unreliable deployment.
   */
  readonly isDurable: boolean;
  reserveJobAtomically(record: StoredTryOnJobRecord): Promise<IdempotencyReservationResult>;
  getJob(jobId: string): Promise<StoredTryOnJobRecord | null>;
  updateJob(
    jobId: string,
    mutator: (current: StoredTryOnJobRecord) => StoredTryOnJobRecord,
  ): Promise<StoredTryOnJobRecord | null>;
  reserveBudgetUnits(units: number, hardCapUnits: number): Promise<{ allowed: boolean; usedUnits: number }>;
  reserveLiveSpendAtomically?(params: LiveSpendReservationParams): Promise<LiveSpendReservationResult>;
}

export interface OpenAiUsageReport {
  modelId: string;
  promptVersion: string;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  imageInputTokens?: number;
  textInputTokens?: number;
  estimatedCostUsd: number | null;
}

export interface UpstreamViewSubmitInput {
  jobId: string;
  requestId: string;
  view: ImageView;
  presentationMode?: TryOnPresentationMode;
  personImage: WireImageInput;
  garments: Array<{
    itemId: string;
    category: Category;
    colors?: string[];
    image: WireImageInput;
  }>;
}

export interface UpstreamViewSubmitOutput {
  upstreamPredictionId: string;
  initialState: "QUEUED" | "RUNNING" | "SUCCEEDED";
  immediateOutput?: ViewOutputPayload;
  remoteDeletionState: RemoteDeletionState;
  usageReport?: OpenAiUsageReport;
}

export interface UpstreamViewPollOutput {
  state: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  output?: ViewOutputPayload;
  errorCode?: TryOnErrorCode;
  errorMessage?: string;
  retryable?: boolean;
}

export interface UpstreamVtoProvider {
  readonly providerId: string;
  readonly source: TryOnSource;
  readonly supportsMultiGarment: boolean;
  readonly maxGarmentsPerJob: number;
  readonly supportedCategories: ReadonlySet<Category>;
  validateViewInput?(input: UpstreamViewSubmitInput): void;
  submitView(input: UpstreamViewSubmitInput): Promise<UpstreamViewSubmitOutput>;
  pollView(upstreamPredictionId: string, requestId: string): Promise<UpstreamViewPollOutput>;
  cancelView(upstreamPredictionId: string): Promise<{ attempted: boolean; confirmed: boolean }>;
}

export interface FostaIncompleteDiagnostic {
  status: string;
  incompleteReason: string | null;
  errorCode: string | null;
  outputItemTypes: string[];
}

export interface SafeTryOnLogEntry {
  event:
    | "tryon.submit.accepted"
    | "tryon.submit.replayed"
    | "tryon.submit.rejected"
    | "tryon.submit.ambiguous"
    | "tryon.upstream.incomplete"
    | "tryon.poll"
    | "tryon.cancel"
    | "tryon.delete";
  service: "tryon-v2";
  requestId: string;
  jobId?: string;
  providerId?: string;
  modelId?: string;
  mode?: "FAKE" | "OFFLINE_MOCK_OPENAI" | "OFFLINE_MOCK_FOSTA" | "AI_LIVE";
  status?: TryOnJobStatus;
  errorCode?: TryOnErrorCode;
  incompleteDiagnostic?: FostaIncompleteDiagnostic;
  estimatedCostUsd?: number;
  inputTokens?: number;
  outputTokens?: number;
  latencyMs: number;
}

export type SafeTryOnLogger = (entry: SafeTryOnLogEntry) => void;

export interface TryOnEnvConfig {
  /** Legacy/shared mode flag ("FAKE" | "AI_LIVE" | "DISABLED"). */
  VTO_MODE?: "FAKE" | "AI_LIVE" | "DISABLED";
  VTO_KILL_SWITCH?: string;
  VTO_FEASIBILITY_DECISION?: FeasibilityDecision;
  VTO_PROVIDER_CONFIG_VERSION?: string;
  VTO_BUDGET_CAP_UNITS?: string;
  VTO_OUTPUT_TTL_MS?: string;
  FASHN_API_KEY?: string;

  /** Primary VTO provider selector ("openai" | "fosta" | "fake" | "fashn"). */
  TRYON_PROVIDER?: string;
  /** Hard LIVE inference gate — MUST default to "false" (OFF). */
  TRYON_LIVE_ENABLED?: string;
  /** Explicit offline OpenAI-schema mock mode ("true" runs OpenAITryOnProvider against local mock with source="FAKE"). */
  TRYON_OFFLINE_MOCK_OPENAI?: string;
  /** Optional deterministic fault-injection scenario for offline OpenAI mock testing. */
  TRYON_MOCK_SCENARIO?: string;
  /** Explicit offline FOSTA-schema mock mode ("true" runs FostaTryOnProvider against local mock with source="FAKE"). */
  TRYON_OFFLINE_MOCK_FOSTA?: string;
  /** Optional deterministic fault-injection scenario for offline FOSTA mock testing. */
  TRYON_FOSTA_MOCK_SCENARIO?: string;
  /** Explicit human confirmation that paid API budget is authorized ("true" required for OpenAI LIVE). */
  TRYON_BUDGET_CONFIRMED?: string;
  /** Explicit override allowing a custom model ID whose static cost cannot be estimated ("true" required if unpriced). */
  TRYON_ALLOW_UNPRICED_MODEL?: string;
  /** Hard request count cap for LIVE sessions (defaults to 1 for first live POC). */
  TRYON_LIVE_MAX_REQUESTS?: string;
  /** Hard cumulative estimated USD ceiling for OpenAI LIVE sessions (must be > 0 when OpenAI LIVE is enabled). */
  TRYON_LIVE_MAX_ESTIMATED_USD?: string;

  /** OpenAI Images Edit API configuration (server-side secrets/env ONLY; never exposed to Android). */
  OPENAI_API_KEY?: string;
  OPENAI_IMAGE_MODEL?: string;
  OPENAI_API_BASE_URL?: string;
  OPENAI_ORG_ID?: string;
  OPENAI_PROJECT_ID?: string;
  OPENAI_IMAGE_SIZE?: string;
  OPENAI_IMAGE_QUALITY?: string;
  OPENAI_OUTPUT_FORMAT?: string;
  OPENAI_INPUT_FIDELITY?: string;
  OPENAI_ALLOW_CUSTOM_MODEL?: string;
  OPENAI_TIMEOUT_MS?: string;

  /** Experimental FOSTA Luna Responses API configuration (server-side secrets/env ONLY; never exposed to Android). */
  FOSTA_API_KEY?: string;
  FOSTA_ENDPOINT?: string;
  FOSTA_MODEL?: string;
  FOSTA_TIMEOUT_MS?: string;
  /** Separate explicit quota approval for FOSTA third-party proxy ("true" required for FOSTA LIVE). */
  FOSTA_APPROVE_QUOTA?: string;
}

export interface TryOnRouteContext {
  sessionId: string | null;
}
