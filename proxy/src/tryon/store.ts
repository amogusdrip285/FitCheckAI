// OWNER: Track C (C-207 — Durable job store contract & test/dev stores)

import type {
  DurableTryOnJobStore,
  IdempotencyReservationResult,
  LiveSpendReservationParams,
  LiveSpendReservationResult,
  StoredTryOnJobRecord,
} from "./types";

const ACTIVE_STATUSES = new Set(["QUEUED", "RUNNING", "UNKNOWN_SUBMISSION"]);

function cloneRecord(record: StoredTryOnJobRecord): StoredTryOnJobRecord {
  return JSON.parse(JSON.stringify(record)) as StoredTryOnJobRecord;
}

/**
 * In-memory job store with single-flight async lock to guarantee atomic idempotency
 * reservation under concurrent requests within an isolate.
 *
 * IMPORTANT (C-207): Cloudflare Worker memory is ephemeral across isolates/restarts.
 * Therefore `isDurable` defaults to `false` so `AI_LIVE` mode refuses to run without
 * a real durable store binding (e.g. Durable Object / transactional KV-SQL adapter).
 */
export class InMemoryTryOnJobStore implements DurableTryOnJobStore {
  public readonly isDurable: boolean;
  private readonly jobsById = new Map<string, StoredTryOnJobRecord>();
  private readonly jobIdByIdempotencyKey = new Map<string, string>();
  private usedBudgetUnits = 0;
  private usedLiveRequests = 0;
  private usedEstimatedUsd = 0;
  private mutexChain: Promise<void> = Promise.resolve();

  constructor(options?: { simulateDurableForTests?: boolean }) {
    this.isDurable = options?.simulateDurableForTests ?? false;
  }

  private async withLock<T>(fn: () => T | Promise<T>): Promise<T> {
    const previous = this.mutexChain;
    let release!: () => void;
    this.mutexChain = new Promise<void>((resolve) => {
      release = resolve;
    });
    await previous;
    try {
      return await fn();
    } finally {
      release();
    }
  }

  async reserveJobAtomically(
    candidate: StoredTryOnJobRecord,
  ): Promise<IdempotencyReservationResult> {
    return this.withLock(() => {
      const existingJobId = this.jobIdByIdempotencyKey.get(
        candidate.idempotencyScopeKey,
      );
      if (existingJobId !== undefined) {
        const existing = this.jobsById.get(existingJobId);
        if (existing) {
          if (existing.payloadHash === candidate.payloadHash) {
            return {
              kind: "EXISTING_SAME_PAYLOAD",
              record: cloneRecord(existing),
            };
          }
          return {
            kind: "CONFLICT_DIFFERENT_PAYLOAD",
            record: cloneRecord(existing),
          };
        }
      }

      // CT-07: Enforce at most 1 active VTO job per session at a time.
      for (const job of this.jobsById.values()) {
        if (
          job.sessionId === candidate.sessionId &&
          ACTIVE_STATUSES.has(job.status)
        ) {
          return {
            kind: "ACTIVE_JOB_EXISTS",
            record: cloneRecord(job),
          };
        }
      }

      const stored = cloneRecord(candidate);
      this.jobsById.set(stored.jobId, stored);
      this.jobIdByIdempotencyKey.set(stored.idempotencyScopeKey, stored.jobId);
      return {
        kind: "CREATED",
        record: cloneRecord(stored),
      };
    });
  }

  async getJob(jobId: string): Promise<StoredTryOnJobRecord | null> {
    return this.withLock(() => {
      const record = this.jobsById.get(jobId);
      return record ? cloneRecord(record) : null;
    });
  }

  async updateJob(
    jobId: string,
    mutator: (current: StoredTryOnJobRecord) => StoredTryOnJobRecord,
  ): Promise<StoredTryOnJobRecord | null> {
    return this.withLock(() => {
      const current = this.jobsById.get(jobId);
      if (!current) {
        return null;
      }
      const updated = cloneRecord(mutator(cloneRecord(current)));
      this.jobsById.set(jobId, updated);
      return cloneRecord(updated);
    });
  }

  async reserveBudgetUnits(
    units: number,
    hardCapUnits: number,
  ): Promise<{ allowed: boolean; usedUnits: number }> {
    return this.withLock(() => {
      if (units <= 0) {
        return { allowed: true, usedUnits: this.usedBudgetUnits };
      }
      if (this.usedBudgetUnits + units > hardCapUnits) {
        return { allowed: false, usedUnits: this.usedBudgetUnits };
      }
      this.usedBudgetUnits += units;
      return { allowed: true, usedUnits: this.usedBudgetUnits };
    });
  }

  async reserveLiveSpendAtomically(
    params: LiveSpendReservationParams,
  ): Promise<LiveSpendReservationResult> {
    return this.withLock(() => {
      if (params.requestUnits <= 0 && params.estimatedUsd <= 0) {
        return {
          allowed: true,
          usedRequests: this.usedLiveRequests,
          usedEstimatedUsd: this.usedEstimatedUsd,
        };
      }
      if (this.usedLiveRequests + params.requestUnits > params.maxRequests) {
        return {
          allowed: false,
          reason: "MAX_REQUESTS",
          usedRequests: this.usedLiveRequests,
          usedEstimatedUsd: this.usedEstimatedUsd,
        };
      }
      const nextUsd = Number((this.usedEstimatedUsd + params.estimatedUsd).toFixed(6));
      if (nextUsd > params.maxEstimatedUsd + 1e-9) {
        return {
          allowed: false,
          reason: "MAX_USD",
          usedRequests: this.usedLiveRequests,
          usedEstimatedUsd: this.usedEstimatedUsd,
        };
      }
      this.usedLiveRequests += params.requestUnits;
      this.usedEstimatedUsd = nextUsd;
      return {
        allowed: true,
        usedRequests: this.usedLiveRequests,
        usedEstimatedUsd: this.usedEstimatedUsd,
      };
    });
  }
}

export interface PersistedTryOnLedgerSnapshot {
  schemaVersion: 1;
  usedBudgetUnits: number;
  usedLiveRequests: number;
  usedEstimatedUsd: number;
  jobsById: Record<string, StoredTryOnJobRecord>;
  jobIdByIdempotencyKey: Record<string, string>;
}

export interface JsonLedgerStorageBackend {
  read(): string | null | Promise<string | null>;
  write(serializedJson: string): void | Promise<void>;
}

/**
 * Durable JSON-ledger job & spend store backed by a pluggable atomic storage backend
 * (e.g. atomic local file write+rename for local POC server, or transactional KV/DO storage).
 * Survives process restarts so idempotency keys and `TRYON_LIVE_MAX_REQUESTS` cannot be reset accidentally.
 */
export class JsonLedgerTryOnJobStore implements DurableTryOnJobStore {
  public readonly isDurable = true;
  private readonly backend: JsonLedgerStorageBackend;
  private mutexChain: Promise<void> = Promise.resolve();

  constructor(backend: JsonLedgerStorageBackend) {
    this.backend = backend;
  }

  private emptyLedger(): PersistedTryOnLedgerSnapshot {
    return {
      schemaVersion: 1,
      usedBudgetUnits: 0,
      usedLiveRequests: 0,
      usedEstimatedUsd: 0,
      jobsById: {},
      jobIdByIdempotencyKey: {},
    };
  }

  private async readLedger(): Promise<PersistedTryOnLedgerSnapshot> {
    try {
      const raw = await this.backend.read();
      if (!raw) return this.emptyLedger();
      const parsed = JSON.parse(raw) as Partial<PersistedTryOnLedgerSnapshot>;
      if (parsed.schemaVersion !== 1 || !parsed.jobsById || !parsed.jobIdByIdempotencyKey) {
        return this.emptyLedger();
      }
      return {
        schemaVersion: 1,
        usedBudgetUnits: parsed.usedBudgetUnits ?? 0,
        usedLiveRequests: parsed.usedLiveRequests ?? 0,
        usedEstimatedUsd: parsed.usedEstimatedUsd ?? 0,
        jobsById: parsed.jobsById,
        jobIdByIdempotencyKey: parsed.jobIdByIdempotencyKey,
      };
    } catch {
      return this.emptyLedger();
    }
  }

  private async writeLedger(ledger: PersistedTryOnLedgerSnapshot): Promise<void> {
    await this.backend.write(JSON.stringify(ledger, null, 2));
  }

  private async withLock<T>(fn: () => T | Promise<T>): Promise<T> {
    const prev = this.mutexChain;
    let release!: () => void;
    this.mutexChain = new Promise<void>((resolve) => {
      release = resolve;
    });
    await prev;
    try {
      return await fn();
    } finally {
      release();
    }
  }

  async reserveJobAtomically(
    candidate: StoredTryOnJobRecord,
  ): Promise<IdempotencyReservationResult> {
    return this.withLock(async () => {
      const ledger = await this.readLedger();
      const existingJobId = ledger.jobIdByIdempotencyKey[candidate.idempotencyScopeKey];
      if (existingJobId) {
        const existing = ledger.jobsById[existingJobId];
        if (existing) {
          if (existing.payloadHash === candidate.payloadHash) {
            return { kind: "EXISTING_SAME_PAYLOAD", record: cloneRecord(existing) };
          }
          return { kind: "CONFLICT_DIFFERENT_PAYLOAD", record: cloneRecord(existing) };
        }
      }

      for (const job of Object.values(ledger.jobsById)) {
        if (job.sessionId === candidate.sessionId && ACTIVE_STATUSES.has(job.status)) {
          return { kind: "ACTIVE_JOB_EXISTS", record: cloneRecord(job) };
        }
      }

      const stored = cloneRecord(candidate);
      ledger.jobsById[stored.jobId] = stored;
      ledger.jobIdByIdempotencyKey[stored.idempotencyScopeKey] = stored.jobId;
      await this.writeLedger(ledger);
      return { kind: "CREATED", record: cloneRecord(stored) };
    });
  }

  async getJob(jobId: string): Promise<StoredTryOnJobRecord | null> {
    return this.withLock(async () => {
      const ledger = await this.readLedger();
      const rec = ledger.jobsById[jobId];
      return rec ? cloneRecord(rec) : null;
    });
  }

  async updateJob(
    jobId: string,
    mutator: (current: StoredTryOnJobRecord) => StoredTryOnJobRecord,
  ): Promise<StoredTryOnJobRecord | null> {
    return this.withLock(async () => {
      const ledger = await this.readLedger();
      const current = ledger.jobsById[jobId];
      if (!current) return null;
      const updated = cloneRecord(mutator(cloneRecord(current)));
      ledger.jobsById[jobId] = updated;
      await this.writeLedger(ledger);
      return cloneRecord(updated);
    });
  }

  async reserveBudgetUnits(
    units: number,
    hardCapUnits: number,
  ): Promise<{ allowed: boolean; usedUnits: number }> {
    return this.withLock(async () => {
      const ledger = await this.readLedger();
      if (units <= 0) {
        return { allowed: true, usedUnits: ledger.usedBudgetUnits };
      }
      if (ledger.usedBudgetUnits + units > hardCapUnits) {
        return { allowed: false, usedUnits: ledger.usedBudgetUnits };
      }
      ledger.usedBudgetUnits += units;
      await this.writeLedger(ledger);
      return { allowed: true, usedUnits: ledger.usedBudgetUnits };
    });
  }

  async reserveLiveSpendAtomically(
    params: LiveSpendReservationParams,
  ): Promise<LiveSpendReservationResult> {
    return this.withLock(async () => {
      const ledger = await this.readLedger();
      if (params.requestUnits <= 0 && params.estimatedUsd <= 0) {
        return {
          allowed: true,
          usedRequests: ledger.usedLiveRequests,
          usedEstimatedUsd: ledger.usedEstimatedUsd,
        };
      }
      if (ledger.usedLiveRequests + params.requestUnits > params.maxRequests) {
        return {
          allowed: false,
          reason: "MAX_REQUESTS",
          usedRequests: ledger.usedLiveRequests,
          usedEstimatedUsd: ledger.usedEstimatedUsd,
        };
      }
      const nextUsd = Number((ledger.usedEstimatedUsd + params.estimatedUsd).toFixed(6));
      if (nextUsd > params.maxEstimatedUsd + 1e-9) {
        return {
          allowed: false,
          reason: "MAX_USD",
          usedRequests: ledger.usedLiveRequests,
          usedEstimatedUsd: ledger.usedEstimatedUsd,
        };
      }
      ledger.usedLiveRequests += params.requestUnits;
      ledger.usedEstimatedUsd = nextUsd;
      await this.writeLedger(ledger);
      return {
        allowed: true,
        usedRequests: ledger.usedLiveRequests,
        usedEstimatedUsd: ledger.usedEstimatedUsd,
      };
    });
  }
}
