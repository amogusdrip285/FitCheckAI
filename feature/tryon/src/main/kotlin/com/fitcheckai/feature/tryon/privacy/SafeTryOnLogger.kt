package com.fitcheckai.feature.tryon.privacy

import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus

data class SafeTryOnLogRecord(
    val event: String,
    val requestId: String,
    val jobId: String? = null,
    val status: TryOnJobStatus? = null,
    val errorCode: TryOnErrorCode? = null,
    val latencyMs: Long? = null,
)

/**
 * Redacted operational logger for Virtual Try-On (`CT-07` & `C-210`).
 * Never logs base64 image payloads, local file paths, remote URLs, API keys, or profile measurements.
 */
class SafeTryOnLogger(
    private val sink: (SafeTryOnLogRecord) -> Unit = {},
) {
    private val recorded = mutableListOf<SafeTryOnLogRecord>()

    val entries: List<SafeTryOnLogRecord>
        @Synchronized get() = recorded.toList()

    @Synchronized
    fun log(
        event: String,
        requestId: String,
        jobId: String? = null,
        status: TryOnJobStatus? = null,
        errorCode: TryOnErrorCode? = null,
        latencyMs: Long? = null,
    ) {
        val safeEntry = SafeTryOnLogRecord(
            event = sanitizeIdentifier(event, fallback = "tryon.unknown"),
            requestId = sanitizeIdentifier(requestId, fallback = "req-redacted"),
            jobId = jobId?.let { sanitizeIdentifier(it, fallback = "job-redacted") },
            status = status,
            errorCode = errorCode,
            latencyMs = latencyMs?.coerceAtLeast(0L),
        )
        recorded += safeEntry
        sink(safeEntry)
    }

    companion object {
        private val SAFE_ID_REGEX = Regex("^[A-Za-z0-9._:-]{1,80}$")
        private val SUSPICIOUS_PREFIX = Regex("^(?:https?://|file://|content://|/home/|/data/|/storage/)", RegexOption.IGNORE_CASE)

        fun sanitizeIdentifier(raw: String, fallback: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || SUSPICIOUS_PREFIX.containsMatchIn(trimmed)) {
                return fallback
            }
            return if (SAFE_ID_REGEX.matches(trimmed)) trimmed else fallback
        }
    }
}
