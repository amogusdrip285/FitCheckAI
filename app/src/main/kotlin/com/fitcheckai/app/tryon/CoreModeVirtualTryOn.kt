package com.fitcheckai.app.tryon

import com.fitcheckai.feature.tryon.contract.CancellationReceipt
import com.fitcheckai.feature.tryon.contract.DeletionReceipt
import com.fitcheckai.feature.tryon.contract.FeasibilityDecision
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnJobHandle
import com.fitcheckai.feature.tryon.contract.TryOnRequest
import com.fitcheckai.feature.tryon.contract.TryOnResult
import com.fitcheckai.feature.tryon.contract.VirtualTryOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * D-007 release mode CORE: the C-205 gate is BLOCKED, so the app ships with try-on honestly
 * unavailable. Capability is UNKNOWN with no views, so TryOnViewModel never enables submit;
 * every job call fails without touching the network or uploading any photo.
 * Swap this binding for AndroidVirtualTryOnClient only after C-205 records GO_* and C-212 passes.
 */
internal class CoreModeVirtualTryOn : VirtualTryOn {
    override suspend fun capabilities(): Result<TryOnCapability> = Result.success(CORE_CAPABILITY)

    override suspend fun submit(request: TryOnRequest): Result<TryOnJobHandle> = unavailable()

    override fun observe(jobId: String): Flow<TryOnResult> = emptyFlow()

    override suspend fun cancel(jobId: String): Result<CancellationReceipt> = unavailable()

    override suspend fun delete(jobId: String): Result<DeletionReceipt> = unavailable()

    private fun <T> unavailable(): Result<T> = Result.failure(IllegalStateException(LOCKED_REASON))

    companion object {
        const val LOCKED_REASON =
            "Thử đồ ảo chưa được bật trong bản này: nhóm chưa hoàn tất kiểm định chất lượng và quyền riêng tư (C-205)."

        val CORE_CAPABILITY = TryOnCapability(
            decision = FeasibilityDecision.UNKNOWN,
            supportedViews = emptySet(),
            supportedCategories = emptySet(),
            maxGarmentsPerJob = 0,
            supportsNativePairedViews = false,
            runtimeVerifiedViews = emptySet(),
            providerId = "none",
            providerConfigVersion = "core-mode",
            executionMode = "CORE_DISABLED",
            lockedReason = LOCKED_REASON,
        )
    }
}
