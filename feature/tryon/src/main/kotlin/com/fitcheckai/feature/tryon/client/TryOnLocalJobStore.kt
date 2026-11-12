package com.fitcheckai.feature.tryon.client

import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.TryOnResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
enum class SubmissionPhase {
    DISPATCHING_POST,
    SUBMITTED_KNOWN_JOB,
    AMBIGUOUS_LOST_RESPONSE,
}

@Serializable
data class PersistedTryOnJobRecord(
    val jobId: String,
    val remoteJobId: String? = null,
    val sessionId: String,
    val requestId: String,
    val idempotencyKey: String,
    val payloadFingerprint: String,
    val outfitId: String,
    val inputGeneration: Long,
    val inputRevisions: Map<String, Long>,
    val requestedViews: Set<ImageView>,
    val selectedItemIds: List<String>,
    val unappliedItemIds: List<String>,
    val providerConfigVersion: String,
    val submissionPhase: SubmissionPhase,
    val createdAtEpochMillis: Long,
    val deadlineEpochMillis: Long,
    val latestResult: TryOnResult,
    val viewsHydrated: Boolean = false,
    val remoteReportedTerminalOnSubmit: Boolean = false,
)

@Serializable
private data class PersistedJobDatabase(
    val schemaVersion: Int = 2,
    val records: List<PersistedTryOnJobRecord> = emptyList(),
)

interface TryOnLocalJobStore {
    suspend fun loadAll(): List<PersistedTryOnJobRecord>
    suspend fun getByJobId(jobId: String): PersistedTryOnJobRecord?
    suspend fun findByIdempotency(
        sessionId: String,
        inputGeneration: Long,
        idempotencyKey: String,
    ): PersistedTryOnJobRecord?
    suspend fun upsert(record: PersistedTryOnJobRecord)
    suspend fun remove(jobId: String)
}

class InMemoryTryOnLocalJobStore(
    initialRecords: List<PersistedTryOnJobRecord> = emptyList(),
) : TryOnLocalJobStore {
    private val mutex = Mutex()
    private val map = LinkedHashMap<String, PersistedTryOnJobRecord>().apply {
        initialRecords.forEach { put(it.jobId, it) }
    }

    override suspend fun loadAll(): List<PersistedTryOnJobRecord> = mutex.withLock {
        map.values.toList()
    }

    override suspend fun getByJobId(jobId: String): PersistedTryOnJobRecord? = mutex.withLock {
        map[jobId] ?: map.values.firstOrNull { it.remoteJobId == jobId }
    }

    override suspend fun findByIdempotency(
        sessionId: String,
        inputGeneration: Long,
        idempotencyKey: String,
    ): PersistedTryOnJobRecord? = mutex.withLock {
        map.values.firstOrNull {
            it.sessionId == sessionId &&
                it.inputGeneration == inputGeneration &&
                it.idempotencyKey == idempotencyKey
        }
    }

    override suspend fun upsert(record: PersistedTryOnJobRecord) {
        mutex.withLock {
            map[record.jobId] = record
        }
    }

    override suspend fun remove(jobId: String) {
        mutex.withLock {
            val targetKey = map.entries.firstOrNull {
                it.key == jobId || it.value.remoteJobId == jobId
            }?.key
            if (targetKey != null) {
                map.remove(targetKey)
            }
        }
    }
}

/**
 * Atomic file-backed local job metadata store (`C-208`).
 * Recommended location: `File(context.noBackupFilesDir, "tryon_jobs_v2.json")` so Android
 * Cloud Backup & Device-to-Device transfer automatically exclude job metadata.
 */
class AtomicFileTryOnLocalJobStore(
    private val file: File,
) : TryOnLocalJobStore {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun loadAll(): List<PersistedTryOnJobRecord> = mutex.withLock {
        readLocked()
    }

    override suspend fun getByJobId(jobId: String): PersistedTryOnJobRecord? = mutex.withLock {
        readLocked().firstOrNull { it.jobId == jobId || it.remoteJobId == jobId }
    }

    override suspend fun findByIdempotency(
        sessionId: String,
        inputGeneration: Long,
        idempotencyKey: String,
    ): PersistedTryOnJobRecord? = mutex.withLock {
        readLocked().firstOrNull {
            it.sessionId == sessionId &&
                it.inputGeneration == inputGeneration &&
                it.idempotencyKey == idempotencyKey
        }
    }

    override suspend fun upsert(record: PersistedTryOnJobRecord) {
        mutex.withLock {
            val existing = readLocked().toMutableList()
            val idx = existing.indexOfFirst { it.jobId == record.jobId }
            if (idx >= 0) {
                existing[idx] = record
            } else {
                existing.add(record)
            }
            writeAtomicallyLocked(existing)
        }
    }

    override suspend fun remove(jobId: String) {
        mutex.withLock {
            val filtered = readLocked().filterNot { it.jobId == jobId || it.remoteJobId == jobId }
            writeAtomicallyLocked(filtered)
        }
    }

    private fun readLocked(): List<PersistedTryOnJobRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val raw = file.readText(Charsets.UTF_8)
            json.decodeFromString<PersistedJobDatabase>(raw).records
        }.getOrDefault(emptyList())
    }

    private fun writeAtomicallyLocked(records: List<PersistedTryOnJobRecord>) {
        file.parentFile?.mkdirs()
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        val encoded = json.encodeToString(PersistedJobDatabase(records = records))
        FileOutputStream(tmpFile).use { fos ->
            fos.write(encoded.toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }
        Files.move(
            tmpFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
