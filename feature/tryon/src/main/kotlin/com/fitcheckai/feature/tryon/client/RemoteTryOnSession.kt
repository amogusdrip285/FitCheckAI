package com.fitcheckai.feature.tryon.client

import com.fitcheckai.feature.tryon.BuildConfig
import com.fitcheckai.feature.tryon.contract.FeasibilityDecision
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnMediaStorePort
import com.fitcheckai.feature.tryon.contract.VirtualTryOn
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Backend chosen at build time from FITCHECK_TRYON_URL / FITCHECK_TRYON_TOKEN in the root `.env`. */
object TryOnBackendConfig {
    val baseUrl: String get() = BuildConfig.TRYON_BACKEND_URL
    val authToken: String? get() = BuildConfig.TRYON_BACKEND_TOKEN.ifBlank { null }
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
}

/**
 * A real try-on client plus the resources it owns. Close it when the owning screen goes away
 * (e.g. `viewModel.addCloseable(session)`): stops polling and releases the HTTP connection pool.
 */
class RemoteTryOnSession private constructor(
    val service: VirtualTryOn,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
) : AutoCloseable {

    override fun close() {
        scope.cancel()
        httpClient.close()
    }

    companion object {
        const val SESSION_ID = "android-local-session"
        private const val JOB_STORE_FILE = "tryon_app_jobs_v2.json"
        private const val CONNECT_TIMEOUT_S = 10L
        private const val READ_TIMEOUT_S = 260L // FOSTA/OpenAI can take minutes; client deadlines still apply
        private const val WRITE_TIMEOUT_S = 60L

        /** [mediaStore] and [privacyCoordinator] must be the same instances the TryOnViewModel uses. */
        fun create(
            baseUrl: String,
            authToken: String?,
            jobStoreDir: File,
            mediaStore: TryOnMediaStorePort,
            privacyCoordinator: TryOnPrivacyCoordinator,
        ): RemoteTryOnSession {
            val httpClient = HttpClient(OkHttp) {
                engine {
                    config {
                        connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                        readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                        writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                    }
                }
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val transport = KtorTryOnHttpTransport(httpClient, baseUrl, authToken)
            val client = AndroidVirtualTryOnClient(
                capabilityProvider = {
                    transport.fetchCapabilities(SESSION_ID).getOrElse { error -> unreachable(baseUrl, error) }
                },
                transport = transport,
                localJobStore = AtomicFileTryOnLocalJobStore(File(jobStoreDir, JOB_STORE_FILE)),
                mediaStore = mediaStore,
                privacyCoordinator = privacyCoordinator,
                scope = scope,
            )
            return RemoteTryOnSession(client, httpClient, scope)
        }

        private fun unreachable(baseUrl: String, error: Throwable) = TryOnCapability(
            decision = FeasibilityDecision.UNKNOWN,
            supportedViews = emptySet(),
            supportedCategories = emptySet(),
            maxGarmentsPerJob = 0,
            supportsNativePairedViews = false,
            runtimeVerifiedViews = emptySet(),
            providerId = "unreachable",
            providerConfigVersion = "unreachable",
            executionMode = "LOCKED",
            lockedReason = "Chưa kết nối được máy chủ thử đồ tại $baseUrl (${error.message ?: "offline"}). " +
                "Hãy chạy proxy/scripts/tryon_local_server.ts rồi mở lại màn hình này.",
        )
    }
}
