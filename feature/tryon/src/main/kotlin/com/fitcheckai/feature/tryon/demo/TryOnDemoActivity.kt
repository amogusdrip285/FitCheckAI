package com.fitcheckai.feature.tryon.demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.feature.tryon.fake.FakeInMemoryMediaStore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.ui.theme.FitCheckTheme
import com.fitcheckai.core.ui.theme.Spacing
import com.fitcheckai.feature.tryon.client.AndroidVirtualTryOnClient
import com.fitcheckai.feature.tryon.client.AtomicFileTryOnLocalJobStore
import com.fitcheckai.feature.tryon.client.KtorTryOnHttpTransport
import com.fitcheckai.feature.tryon.client.LocalDiskTryOnMediaStore
import com.fitcheckai.feature.tryon.client.TryOnImageValidator
import com.fitcheckai.feature.tryon.contract.FeasibilityDecision
import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.MediaScope
import com.fitcheckai.feature.tryon.contract.TryOnCapability
import com.fitcheckai.feature.tryon.contract.TryOnItemSummary
import com.fitcheckai.feature.tryon.contract.TryOnOutfitSnapshot
import com.fitcheckai.feature.tryon.contract.TryOnPersonProfileSnapshot
import com.fitcheckai.feature.tryon.fake.FakeTryOnFixtures
import com.fitcheckai.feature.tryon.fake.FakeTryOnProfilePort
import com.fitcheckai.feature.tryon.fake.FakeTryOnScenario
import com.fitcheckai.feature.tryon.fake.FakeVirtualTryOnService
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import com.fitcheckai.feature.tryon.ui.TryOnRoute
import com.fitcheckai.feature.tryon.ui.TryOnViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object TryOnDemoTags {
    const val DEMO_BANNER = "tryon_demo_banner"
    const val TOGGLE_CONTROLS_BUTTON = "tryon_demo_toggle_controls"
    const val TOGGLE_THEME_BUTTON = "tryon_demo_toggle_theme"
    const val DEMO_CONTROL_PANEL = "tryon_demo_control_panel"
    const val MODE_IN_MEMORY_FAKE = "tryon_demo_mode_in_memory_fake"
    const val MODE_BACKEND_FOSTA = "tryon_demo_mode_backend_fosta"
    const val MODE_BACKEND_OPENAI = "tryon_demo_mode_backend_openai"
    const val REFRESH_BACKEND_BUTTON = "tryon_demo_refresh_backend"
    const val LOAD_REAL_PRIVATE_INPUTS_BUTTON = "tryon_demo_load_real_private_inputs"
    const val OUTFIT_PRESET_WORK = "tryon_demo_outfit_work"
    const val OUTFIT_PRESET_CASUAL = "tryon_demo_outfit_casual"
    const val OUTFIT_PRESET_MISSING_BACK = "tryon_demo_outfit_missing_back"
    const val SCENARIO_DUAL_SUCCESS = "tryon_demo_scenario_dual_success"
    const val SCENARIO_FRONT_ONLY = "tryon_demo_scenario_front_only"
    const val SCENARIO_PARTIAL = "tryon_demo_scenario_partial"
    const val SCENARIO_QUALITY_REJECTED = "tryon_demo_scenario_quality_rejected"
    const val SCENARIO_UNKNOWN_SUBMISSION = "tryon_demo_scenario_unknown"
    const val SCENARIO_TIMEOUT = "tryon_demo_scenario_timeout"
    const val SCENARIO_GATE_BLOCKED = "tryon_demo_scenario_gate_blocked"
    const val LOAD_SAMPLE_LOCAL_PNGS_BUTTON = "tryon_demo_load_sample_pngs"
    const val PICK_PERSON_FRONT_BUTTON = "tryon_demo_pick_person_front"
    const val PICK_PERSON_BACK_BUTTON = "tryon_demo_pick_person_back"
    const val PICK_GARMENT_FRONT_BUTTON = "tryon_demo_pick_garment_front"
    const val PICK_GARMENT_BACK_BUTTON = "tryon_demo_pick_garment_back"
    const val PICK_BOTTOM_FRONT_BUTTON = "tryon_demo_pick_bottom_front"
    const val PICK_BOTTOM_BACK_BUTTON = "tryon_demo_pick_bottom_back"
    const val REVOKE_PRIVACY_BUTTON = "tryon_demo_revoke_privacy"
    const val LOCAL_INPUT_STATUS = "tryon_demo_local_input_status"
}

enum class DemoExecutionMode(val titleVi: String) {
    IN_MEMORY_FAKE("Giả lập nội bộ (Offline Fake)"),
    BACKEND_FOSTA_E2E("Backend FOSTA Luna (10.0.2.2:8788)"),
    BACKEND_OPENAI_E2E("Backend OpenAI E2E (10.0.2.2:8788)"),
}

enum class DemoOutfitPreset(val titleVi: String) {
    WORK_THREE_ITEMS("Bộ 1: Đi làm (Áo + Quần + Giày)"),
    CASUAL_SINGLE_TOP("Bộ 2: Dạo phố (1 Áo Polo)"),
    OUTERWEAR_NO_BACK("Bộ 3: Thiếu ảnh mặt sau"),
}

enum class DemoScenarioPreset(
    val titleVi: String,
    val capability: TryOnCapability,
    val scenario: FakeTryOnScenario,
    val stepDelayMs: Long,
) {
    DUAL_SUCCESS(
        "2 Mặt Thành Công (FAKE)",
        FakeTryOnFixtures.dualCapability,
        FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
        750L,
    ),
    FRONT_ONLY(
        "Chỉ Mặt Trước (FAKE)",
        FakeTryOnFixtures.frontOnlyCapability,
        FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
        650L,
    ),
    PARTIAL_BACK_FAIL(
        "1 Phần — Lỗi Mặt Sau",
        FakeTryOnFixtures.dualCapability,
        FakeTryOnScenario.PARTIAL_BACK_FAILED,
        750L,
    ),
    QUALITY_REJECT(
        "Ẩn Do Lỗi Chất Lượng",
        FakeTryOnFixtures.dualCapability,
        FakeTryOnScenario.QUALITY_REJECTED_SEVERE,
        650L,
    ),
    UNKNOWN_SUBMIT(
        "Chưa Rõ Thu Nhận",
        FakeTryOnFixtures.dualCapability,
        FakeTryOnScenario.UNKNOWN_SUBMISSION,
        450L,
    ),
    TIMEOUT(
        "Quá Thời Gian Chờ",
        FakeTryOnFixtures.dualCapability,
        FakeTryOnScenario.TIMED_OUT,
        650L,
    ),
    GATE_BLOCKED(
        "Cổng Khả Thi Khóa (BLOCKED)",
        FakeTryOnFixtures.coreUnknownCapability,
        FakeTryOnScenario.SUCCEED_REQUESTED_VIEWS,
        10L,
    ),
}

/**
 * Standalone interactive Demo & E2E Workbench Activity owned strictly by Track C (`:feature:tryon`).
 *
 * Supports:
 * 1. `IN_MEMORY_FAKE`: 100% offline in-memory fake scenarios (0 network calls, $0.00).
 * 2. `BACKEND_OPENAI_E2E`: Real HTTP pipeline through `AndroidVirtualTryOnClient` ->
 *    `LocalDiskTryOnMediaStore` -> `http://10.0.2.2:8788` (`tryon_local_server.ts`) ->
 *    `OpenAITryOnProvider` (Offline OpenAI Mock by default with LIVE gate OFF, or real OpenAI
 *    Images Edit API when the user explicitly unlocks `TRYON_LIVE_ENABLED=true`).
 */
class TryOnDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialMode = when (intent?.getStringExtra("tryon_demo_mode")?.lowercase()) {
            "in_memory_fake", "fake", "offline" -> DemoExecutionMode.IN_MEMORY_FAKE
            "backend_openai", "openai", "backend" -> DemoExecutionMode.BACKEND_OPENAI_E2E
            "backend_fosta", "fosta" -> DemoExecutionMode.BACKEND_FOSTA_E2E
            else -> DemoExecutionMode.BACKEND_FOSTA_E2E
        }
        val backendUrl = intent?.getStringExtra("tryon_backend_url")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_EMULATOR_BACKEND_URL
        val localToken = intent?.getStringExtra("tryon_local_token")
            ?.takeIf { it.isNotBlank() }
        val collapseControlsInitially =
            intent?.getBooleanExtra("tryon_collapse_controls", true) != false

        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            FitCheckTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TryOnDemoWorkbench(
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = { isDarkTheme = !isDarkTheme },
                        initialExecutionMode = initialMode,
                        backendBaseUrl = backendUrl,
                        localAuthToken = localToken,
                        initialControlsExpanded = !collapseControlsInitially,
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_EMULATOR_BACKEND_URL = "http://10.0.2.2:8788"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TryOnDemoWorkbench(
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    initialSimulatedDelayOverrideMs: Long? = null,
    initialExecutionMode: DemoExecutionMode = DemoExecutionMode.IN_MEMORY_FAKE,
    backendBaseUrl: String = TryOnDemoActivity.DEFAULT_EMULATOR_BACKEND_URL,
    localAuthToken: String? = null,
    initialControlsExpanded: Boolean = true,
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()

    var controlsExpanded by remember { mutableStateOf(initialControlsExpanded) }
    var executionMode by remember { mutableStateOf(initialExecutionMode) }
    var selectedOutfitPreset by remember { mutableStateOf(DemoOutfitPreset.WORK_THREE_ITEMS) }
    var selectedScenarioPreset by remember { mutableStateOf(DemoScenarioPreset.DUAL_SUCCESS) }
    var sessionNonce by remember { mutableIntStateOf(0) }
    var localInputStatus by remember {
        mutableStateOf(
            when (initialExecutionMode) {
                DemoExecutionMode.BACKEND_FOSTA_E2E ->
                    "Đang kết nối FitCheck Local Backend ($backendBaseUrl • FOSTA gpt-6-luna) — LIVE mặc định OFF."
                DemoExecutionMode.BACKEND_OPENAI_E2E ->
                    "Đang kết nối FitCheck Local Backend ($backendBaseUrl • OpenAI) — LIVE khóa mặc định ($0.00 trừ khi mở cổng)."
                DemoExecutionMode.IN_MEMORY_FAKE ->
                    "Đang dùng dữ liệu mẫu mặc định (FAKE / Offline 100%, 0 byte gửi mạng)."
            },
        )
    }

    // Track custom local MediaRefs picked or generated on the device (supports all 6 slots: Person/Top/Bottom x Front/Back)
    var customPersonFront by remember { mutableStateOf<Pair<MediaRef, ByteArray>?>(null) }
    var customPersonBack by remember { mutableStateOf<Pair<MediaRef, ByteArray>?>(null) }
    var customGarmentFront by remember { mutableStateOf<Pair<MediaRef, ByteArray>?>(null) }
    var customGarmentBack by remember { mutableStateOf<Pair<MediaRef, ByteArray>?>(null) }
    var customBottomFront by remember { mutableStateOf<Pair<MediaRef, ByteArray>?>(null) }
    var customBottomBack by remember { mutableStateOf<Pair<MediaRef, ByteArray>?>(null) }

    val demoEnvironment = remember(
        executionMode,
        backendBaseUrl,
        localAuthToken,
        selectedOutfitPreset,
        selectedScenarioPreset,
        sessionNonce,
    ) {
        buildDemoSessionEnvironment(
            filesDir = context.filesDir,
            cacheDir = context.cacheDir,
            noBackupFilesDir = context.noBackupFilesDir,
            executionMode = executionMode,
            backendBaseUrl = backendBaseUrl,
            localAuthToken = localAuthToken,
            outfitPreset = selectedOutfitPreset,
            scenarioPreset = selectedScenarioPreset,
            delayOverrideMs = initialSimulatedDelayOverrideMs,
            initialAllowSampleBacks = executionMode != DemoExecutionMode.BACKEND_FOSTA_E2E,
            customPersonFront = customPersonFront,
            customPersonBack = customPersonBack,
            customGarmentFront = customGarmentFront,
            customGarmentBack = customGarmentBack,
            customBottomFront = customBottomFront,
            customBottomBack = customBottomBack,
        )
    }

    var fostaPreflightOfflineMock by remember(executionMode, backendBaseUrl, sessionNonce) {
        mutableStateOf<Boolean?>(null)
    }
    val vmUiState by demoEnvironment.viewModel.uiState.collectAsStateWithLifecycle()
    val isFostaMockBackend =
        fostaPreflightOfflineMock == true ||
            vmUiState.capability?.executionMode == "OFFLINE_MOCK_FOSTA"
    val allowSampleBacks =
        executionMode != DemoExecutionMode.BACKEND_FOSTA_E2E || isFostaMockBackend

    LaunchedEffect(
        demoEnvironment,
        allowSampleBacks,
        customPersonFront,
        customPersonBack,
        customGarmentFront,
        customGarmentBack,
        customBottomFront,
        customBottomBack,
    ) {
        demoEnvironment.syncAllInputs(
            allowSampleBacks = allowSampleBacks,
            customPersonFront = customPersonFront,
            customPersonBack = customPersonBack,
            customGarmentFront = customGarmentFront,
            customGarmentBack = customGarmentBack,
            customBottomFront = customBottomFront,
            customBottomBack = customBottomBack,
        )
    }

    DisposableEffect(demoEnvironment) {
        onDispose {
            demoEnvironment.close()
        }
    }

    val pickPersonFrontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            importUriToLocalRef(
                context = context,
                uri = uri,
                slotId = "local-picked-person-front",
                ownerId = "profile-dev-1",
                scope = MediaScope.PERSON,
                view = ImageView.FRONT,
            )?.let { imported ->
                customPersonFront = imported
                localInputStatus = "Đã nạp ảnh Người (Trước) cục bộ: ${imported.first.width}x${imported.first.height}px (${imported.first.byteCount / 1024} KB)."
            }
        }
    }

    val pickPersonBackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            importUriToLocalRef(
                context = context,
                uri = uri,
                slotId = "local-picked-person-back",
                ownerId = "profile-dev-1",
                scope = MediaScope.PERSON,
                view = ImageView.BACK,
            )?.let { imported ->
                customPersonBack = imported
                localInputStatus = "Đã nạp ảnh Người (Sau) cục bộ: ${imported.first.width}x${imported.first.height}px (${imported.first.byteCount / 1024} KB)."
            }
        }
    }

    val pickGarmentFrontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            importUriToLocalRef(
                context = context,
                uri = uri,
                slotId = "local-picked-garment-front",
                ownerId = demoEnvironment.primaryItemId,
                scope = MediaScope.GARMENT,
                view = ImageView.FRONT,
            )?.let { imported ->
                customGarmentFront = imported
                demoEnvironment.syncGarmentInputs(
                    allowSampleBacks = allowSampleBacks,
                    customGarmentFront = imported,
                    customGarmentBack = customGarmentBack,
                    customBottomFront = customBottomFront,
                    customBottomBack = customBottomBack,
                )
                localInputStatus = "Đã nạp ảnh Áo (Trước) cục bộ: ${imported.first.width}x${imported.first.height}px (${imported.first.byteCount / 1024} KB)."
            }
        }
    }

    val pickGarmentBackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            importUriToLocalRef(
                context = context,
                uri = uri,
                slotId = "local-picked-garment-back",
                ownerId = demoEnvironment.primaryItemId,
                scope = MediaScope.GARMENT,
                view = ImageView.BACK,
            )?.let { imported ->
                customGarmentBack = imported
                demoEnvironment.syncGarmentInputs(
                    allowSampleBacks = allowSampleBacks,
                    customGarmentFront = customGarmentFront,
                    customGarmentBack = imported,
                    customBottomFront = customBottomFront,
                    customBottomBack = customBottomBack,
                )
                localInputStatus = "Đã nạp ảnh Áo (Sau) cục bộ: ${imported.first.width}x${imported.first.height}px (${imported.first.byteCount / 1024} KB)."
            }
        }
    }

    val pickBottomFrontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val bottomId = demoEnvironment.secondaryBottomItemId ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            importUriToLocalRef(
                context = context,
                uri = uri,
                slotId = "local-picked-bottom-front",
                ownerId = bottomId,
                scope = MediaScope.GARMENT,
                view = ImageView.FRONT,
            )?.let { imported ->
                customBottomFront = imported
                demoEnvironment.syncGarmentInputs(
                    allowSampleBacks = allowSampleBacks,
                    customGarmentFront = customGarmentFront,
                    customGarmentBack = customGarmentBack,
                    customBottomFront = imported,
                    customBottomBack = customBottomBack,
                )
                localInputStatus = "Đã nạp ảnh Quần (Trước) cục bộ: ${imported.first.width}x${imported.first.height}px (${imported.first.byteCount / 1024} KB)."
            }
        }
    }

    val pickBottomBackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val bottomId = demoEnvironment.secondaryBottomItemId ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            importUriToLocalRef(
                context = context,
                uri = uri,
                slotId = "local-picked-bottom-back",
                ownerId = bottomId,
                scope = MediaScope.GARMENT,
                view = ImageView.BACK,
            )?.let { imported ->
                customBottomBack = imported
                demoEnvironment.syncGarmentInputs(
                    allowSampleBacks = allowSampleBacks,
                    customGarmentFront = customGarmentFront,
                    customGarmentBack = customGarmentBack,
                    customBottomFront = customBottomFront,
                    customBottomBack = imported,
                )
                localInputStatus = "Đã nạp ảnh Quần (Sau) cục bộ: ${imported.first.width}x${imported.first.height}px (${imported.first.byteCount / 1024} KB)."
            }
        }
    }

    val isBackendMode = executionMode == DemoExecutionMode.BACKEND_FOSTA_E2E ||
        executionMode == DemoExecutionMode.BACKEND_OPENAI_E2E

    var preflightBannerSummary by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(executionMode, backendBaseUrl, sessionNonce) {
        if (!isBackendMode) {
            preflightBannerSummary = null
            fostaPreflightOfflineMock = null
            return@LaunchedEffect
        }
        val (summary, detectedOfflineMock) = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$backendBaseUrl/v2/tryon/preflight").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2_500
                    readTimeout = 2_500
                    requestMethod = "GET"
                    setRequestProperty("x-session-id", "demo-ui-preflight")
                    if (!localAuthToken.isNullOrBlank()) {
                        setRequestProperty("x-local-tryon-token", localAuthToken)
                    }
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val provider = json.optString("provider", "fosta").uppercase()
                    val offlineMock = json.optBoolean("offlineMockEnabled", false)
                    val liveEnabled = json.optBoolean("liveEnabled", false)
                    val readyLive = json.optBoolean("readyForLivePocCall", false) ||
                        json.optBoolean("readyForLiveCall", false)
                    val checks = json.optJSONObject("checks")
                    val model = checks?.optString("modelId")
                        ?.takeIf { it.isNotBlank() }
                        ?: json.optString("model", "gpt-6-luna")
                    val text = when {
                        offlineMock ->
                            "Kết nối OK: $provider MOCK (LIVE=OFF • Chi phí $0.00)"
                        readyLive ->
                            "Kết nối OK: $provider LIVE READY ($model • Đã mở cổng LIVE)"
                        else -> {
                            val firstBlocker = json.optJSONArray("blockers")?.optString(0) ?: "LOCKED"
                            "Backend $provider bị khóa: $firstBlocker"
                        }
                    }
                    text to (offlineMock && !liveEnabled)
                } else {
                    "Local Backend phản hồi HTTP ${conn.responseCode} tại $backendBaseUrl" to null
                }
            }.getOrElse {
                "Chưa kết nối được Local Backend ($backendBaseUrl) — Hãy chạy demo_start_mock_backend.sh" to null
            }
        }
        fostaPreflightOfflineMock = detectedOfflineMock
        preflightBannerSummary = summary
        localInputStatus = summary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Persistent top safety & interactive control header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TryOnDemoTags.DEMO_BANNER),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (executionMode) {
                                DemoExecutionMode.BACKEND_FOSTA_E2E ->
                                    "FITCHECK VTO DEMO — BACKEND FOSTA LUNA"
                                DemoExecutionMode.BACKEND_OPENAI_E2E ->
                                    "FITCHECK VTO DEMO — BACKEND OPENAI E2E"
                                DemoExecutionMode.IN_MEMORY_FAKE ->
                                    "FITCHECK VTO DEMO — GIẢ LẬP OFFLINE (FAKE)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = preflightBannerSummary ?: when (executionMode) {
                                DemoExecutionMode.BACKEND_FOSTA_E2E ->
                                    "Pipeline Android -> Local Server -> FOSTA gpt-6-luna • LIVE mặc định OFF"
                                DemoExecutionMode.BACKEND_OPENAI_E2E ->
                                    "Kết nối HTTP tới Local Server • LIVE mặc định OFF"
                                DemoExecutionMode.IN_MEMORY_FAKE ->
                                    "0 gọi mạng • 0 upload ảnh lên provider • Chi phí $0.00"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isBackendMode && !controlsExpanded) {
                            OutlinedButton(
                                onClick = {
                                    sessionNonce += 1
                                    localInputStatus = "Đang kiểm tra lại kết nối tới $backendBaseUrl..."
                                },
                            ) {
                                Text("Làm mới")
                            }
                        }
                        TextButton(
                            onClick = onToggleDarkTheme,
                            modifier = Modifier.testTag(TryOnDemoTags.TOGGLE_THEME_BUTTON),
                        ) {
                            Text(if (isDarkTheme) "Sáng" else "Tối")
                        }
                        OutlinedButton(
                            onClick = { controlsExpanded = !controlsExpanded },
                            modifier = Modifier.testTag(TryOnDemoTags.TOGGLE_CONTROLS_BUTTON),
                        ) {
                            Text(if (controlsExpanded) "Thu gọn" else "Tùy chỉnh")
                        }
                    }
                }

                AnimatedVisibility(visible = controlsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = Spacing.xs)
                            .testTag(TryOnDemoTags.DEMO_CONTROL_PANEL),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 0. Execution Mode Picker (In-Memory Fake vs Backend FOSTA vs Backend OpenAI)
                        Text(
                            text = "0. Chế độ thực thi (Execution Mode):",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            FilterChip(
                                selected = executionMode == DemoExecutionMode.IN_MEMORY_FAKE,
                                onClick = {
                                    executionMode = DemoExecutionMode.IN_MEMORY_FAKE
                                    localInputStatus = "Chuyển sang Giả lập nội bộ (Offline 100%, 0 gọi mạng)."
                                },
                                colors = demoChipColors(),
                                label = { Text(DemoExecutionMode.IN_MEMORY_FAKE.titleVi, modifier = Modifier.padding(vertical = 2.dp)) },
                                modifier = Modifier.testTag(TryOnDemoTags.MODE_IN_MEMORY_FAKE),
                            )
                            FilterChip(
                                selected = executionMode == DemoExecutionMode.BACKEND_FOSTA_E2E,
                                onClick = {
                                    executionMode = DemoExecutionMode.BACKEND_FOSTA_E2E
                                    localInputStatus = "Đã kết nối pipeline Android -> Local Backend FOSTA ($backendBaseUrl)."
                                },
                                colors = demoChipColors(),
                                label = { Text(DemoExecutionMode.BACKEND_FOSTA_E2E.titleVi, modifier = Modifier.padding(vertical = 2.dp)) },
                                modifier = Modifier.testTag(TryOnDemoTags.MODE_BACKEND_FOSTA),
                            )
                            FilterChip(
                                selected = executionMode == DemoExecutionMode.BACKEND_OPENAI_E2E,
                                onClick = {
                                    executionMode = DemoExecutionMode.BACKEND_OPENAI_E2E
                                    localInputStatus = "Đã kết nối pipeline Android -> Local Backend OpenAI ($backendBaseUrl)."
                                },
                                colors = demoChipColors(),
                                label = { Text(DemoExecutionMode.BACKEND_OPENAI_E2E.titleVi, modifier = Modifier.padding(vertical = 2.dp)) },
                                modifier = Modifier.testTag(TryOnDemoTags.MODE_BACKEND_OPENAI),
                            )
                            if (isBackendMode) {
                                OutlinedButton(
                                    onClick = {
                                        sessionNonce += 1
                                        localInputStatus = "Đã tải lại trạng thái capabilities từ $backendBaseUrl."
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .testTag(TryOnDemoTags.REFRESH_BACKEND_BUTTON),
                                ) {
                                    Text("Làm mới kết nối Backend")
                                }
                            }
                        }

                        // A. Outfit Preset Picker
                        Text(
                            text = "A. Chọn bộ trang phục mẫu:",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            DemoOutfitPreset.entries.forEach { preset ->
                                val tag = when (preset) {
                                    DemoOutfitPreset.WORK_THREE_ITEMS -> TryOnDemoTags.OUTFIT_PRESET_WORK
                                    DemoOutfitPreset.CASUAL_SINGLE_TOP -> TryOnDemoTags.OUTFIT_PRESET_CASUAL
                                    DemoOutfitPreset.OUTERWEAR_NO_BACK -> TryOnDemoTags.OUTFIT_PRESET_MISSING_BACK
                                }
                                FilterChip(
                                    selected = selectedOutfitPreset == preset,
                                    onClick = { selectedOutfitPreset = preset },
                                    colors = demoChipColors(),
                                    label = { Text(preset.titleVi, modifier = Modifier.padding(vertical = 2.dp)) },
                                    modifier = Modifier.testTag(tag),
                                )
                            }
                        }

                        // B. Scenario Picker (only shown in IN_MEMORY_FAKE mode)
                        if (executionMode == DemoExecutionMode.IN_MEMORY_FAKE) {
                            Text(
                                text = "B. Chọn kịch bản phản hồi giả lập (FAKE):",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                DemoScenarioPreset.entries.forEach { preset ->
                                    val tag = when (preset) {
                                        DemoScenarioPreset.DUAL_SUCCESS -> TryOnDemoTags.SCENARIO_DUAL_SUCCESS
                                        DemoScenarioPreset.FRONT_ONLY -> TryOnDemoTags.SCENARIO_FRONT_ONLY
                                        DemoScenarioPreset.PARTIAL_BACK_FAIL -> TryOnDemoTags.SCENARIO_PARTIAL
                                        DemoScenarioPreset.QUALITY_REJECT -> TryOnDemoTags.SCENARIO_QUALITY_REJECTED
                                        DemoScenarioPreset.UNKNOWN_SUBMIT -> TryOnDemoTags.SCENARIO_UNKNOWN_SUBMISSION
                                        DemoScenarioPreset.TIMEOUT -> TryOnDemoTags.SCENARIO_TIMEOUT
                                        DemoScenarioPreset.GATE_BLOCKED -> TryOnDemoTags.SCENARIO_GATE_BLOCKED
                                    }
                                    FilterChip(
                                        selected = selectedScenarioPreset == preset,
                                        onClick = { selectedScenarioPreset = preset },
                                        colors = demoChipColors(),
                                        label = { Text(preset.titleVi, modifier = Modifier.padding(vertical = 2.dp)) },
                                        modifier = Modifier.testTag(tag),
                                    )
                                }
                            }
                        }

                        // C. Local Image Input Controls
                        Text(
                            text = "C. Nguồn ảnh người & trang phục cục bộ:",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val loaded = loadRealPrivateInputsFromAppFiles(
                                        filesDir = context.filesDir,
                                        primaryItemId = demoEnvironment.primaryItemId,
                                        secondaryBottomItemId = demoEnvironment.secondaryBottomItemId,
                                    )
                                    if (loaded != null) {
                                        customPersonFront = loaded.personFront
                                        customPersonBack = loaded.personBack
                                        customGarmentFront = loaded.garmentFront
                                        customGarmentBack = loaded.garmentBack
                                        customBottomFront = loaded.bottomFront
                                        customBottomBack = loaded.bottomBack
                                        localInputStatus =
                                            "Đã nạp ảnh thật từ files/vto_real_inputs (Person/Top/Bottom đã tìm thấy sẽ được ưu tiên gửi khi thử đồ)."
                                    } else {
                                        localInputStatus =
                                            "Chưa tìm thấy ảnh hợp lệ trong files/vto_real_inputs (cần ít nhất person_front.* hoặc person_back.* và 1 ảnh trang phục)."
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(TryOnDemoTags.LOAD_REAL_PRIVATE_INPUTS_BUTTON),
                            ) {
                                Text("Nạp từ vto_real_inputs")
                            }
                            OutlinedButton(
                                onClick = {
                                    val generated = generateSampleLocalPngInputs(
                                        filesDir = context.filesDir,
                                        primaryItemId = demoEnvironment.primaryItemId,
                                    )
                                    customPersonFront = generated.personFront
                                    customPersonBack = generated.personBack
                                    customGarmentFront = generated.garmentFront
                                    customGarmentBack = generated.garmentBack
                                    customBottomFront = null
                                    customBottomBack = null
                                    localInputStatus = "Đã tạo & nạp bộ file .png cục bộ (${generated.personFront.first.width}x${generated.personFront.first.height}px) tại bộ nhớ riêng ứng dụng."
                                },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(TryOnDemoTags.LOAD_SAMPLE_LOCAL_PNGS_BUTTON),
                            ) {
                                Text("Nạp ảnh .png cục bộ mẫu")
                            }
                            OutlinedButton(
                                onClick = { pickPersonFrontLauncher.launch("image/*") },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(TryOnDemoTags.PICK_PERSON_FRONT_BUTTON),
                            ) {
                                Text("Chọn Người (Trước)")
                            }
                            OutlinedButton(
                                onClick = { pickPersonBackLauncher.launch("image/*") },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(TryOnDemoTags.PICK_PERSON_BACK_BUTTON),
                            ) {
                                Text("Chọn Người (Sau)")
                            }
                            OutlinedButton(
                                onClick = { pickGarmentFrontLauncher.launch("image/*") },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(TryOnDemoTags.PICK_GARMENT_FRONT_BUTTON),
                            ) {
                                Text("Chọn Áo (Trước)")
                            }
                            OutlinedButton(
                                onClick = { pickGarmentBackLauncher.launch("image/*") },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(TryOnDemoTags.PICK_GARMENT_BACK_BUTTON),
                            ) {
                                Text("Chọn Áo (Sau)")
                            }
                            if (demoEnvironment.secondaryBottomItemId != null) {
                                OutlinedButton(
                                    onClick = { pickBottomFrontLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag(TryOnDemoTags.PICK_BOTTOM_FRONT_BUTTON),
                                ) {
                                    Text("Chọn Quần (Trước)")
                                }
                                OutlinedButton(
                                    onClick = { pickBottomBackLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag(TryOnDemoTags.PICK_BOTTOM_BACK_BUTTON),
                                ) {
                                    Text("Chọn Quần (Sau)")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    uiScope.launch {
                                        demoEnvironment.viewModel.deleteLatestResult()
                                        customPersonFront = null
                                        customPersonBack = null
                                        customGarmentFront = null
                                        customGarmentBack = null
                                        customBottomFront = null
                                        customBottomBack = null
                                        sessionNonce += 1
                                        localInputStatus = "Đã thu hồi Consent & xóa toàn bộ ảnh/kết quả cục bộ (C-210)."
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(TryOnDemoTags.REVOKE_PRIVACY_BUTTON),
                            ) {
                                Text("Xóa ảnh & Rút Consent (C-210)")
                            }
                        }

                        Text(
                            text = localInputStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.testTag(TryOnDemoTags.LOCAL_INPUT_STATUS),
                        )
                    }
                }
            }
        }

        // Main TryOnScreen bound to the current demo session environment
        Box(modifier = Modifier.weight(1f)) {
            key(demoEnvironment.viewModel) {
                TryOnRoute(
                    viewModel = demoEnvironment.viewModel,
                    onNavigateBack = { sessionNonce += 1 },
                    onNavigateToProfilePhotos = {
                        if (!vmUiState.hasPersonFront) {
                            pickPersonFrontLauncher.launch("image/*")
                        } else {
                            pickPersonBackLauncher.launch("image/*")
                        }
                    },
                    onSelectDifferentOutfit = {
                        selectedOutfitPreset = when (selectedOutfitPreset) {
                            DemoOutfitPreset.WORK_THREE_ITEMS -> DemoOutfitPreset.CASUAL_SINGLE_TOP
                            DemoOutfitPreset.CASUAL_SINGLE_TOP -> DemoOutfitPreset.OUTERWEAR_NO_BACK
                            DemoOutfitPreset.OUTERWEAR_NO_BACK -> DemoOutfitPreset.WORK_THREE_ITEMS
                        }
                    },
                    onPickPersonPhoto = { view ->
                        when (view) {
                            ImageView.FRONT -> pickPersonFrontLauncher.launch("image/*")
                            ImageView.BACK -> pickPersonBackLauncher.launch("image/*")
                        }
                    },
                    onPickGarmentPhoto = { itemId, view ->
                        val isBottom = itemId == demoEnvironment.secondaryBottomItemId
                        if (isBottom) {
                            when (view) {
                                ImageView.FRONT -> pickBottomFrontLauncher.launch("image/*")
                                ImageView.BACK -> pickBottomBackLauncher.launch("image/*")
                            }
                        } else {
                            when (view) {
                                ImageView.FRONT -> pickGarmentFrontLauncher.launch("image/*")
                                ImageView.BACK -> pickGarmentBackLauncher.launch("image/*")
                            }
                        }
                    },
                    onRefreshConnection = {
                        sessionNonce += 1
                        localInputStatus = "Đang kiểm tra lại kết nối tới $backendBaseUrl..."
                    },
                )
            }
        }
    }
}

private class DemoSessionEnvironment(
    val viewModel: TryOnViewModel,
    val vmScope: CoroutineScope,
    val primaryItemId: String,
    val secondaryBottomItemId: String? = null,
    private val filesDir: File,
    private val executionMode: DemoExecutionMode,
    private val outfitPreset: DemoOutfitPreset,
    private val baseOutfit: TryOnOutfitSnapshot,
    private val baseGarmentMap: Map<String, ImagePair>,
    private val profilePort: FakeTryOnProfilePort,
    private val inMemoryMediaStore: FakeInMemoryMediaStore? = null,
    private val httpClient: HttpClient? = null,
    initialPersonImages: ImagePair,
) {
    private var lastPersonImages: ImagePair = initialPersonImages

    fun syncGarmentInputs(
        allowSampleBacks: Boolean,
        customGarmentFront: Pair<MediaRef, ByteArray>?,
        customGarmentBack: Pair<MediaRef, ByteArray>?,
        customBottomFront: Pair<MediaRef, ByteArray>?,
        customBottomBack: Pair<MediaRef, ByteArray>?,
    ) {
        val nextGarmentMap = resolveDemoGarmentMap(
            filesDir = filesDir,
            executionMode = executionMode,
            outfitPreset = outfitPreset,
            baseOutfit = baseOutfit,
            baseGarmentMap = baseGarmentMap,
            primaryItemId = primaryItemId,
            secondaryBottomItemId = secondaryBottomItemId,
            allowSampleBacks = allowSampleBacks,
            inMemoryMediaStore = inMemoryMediaStore,
            customGarmentFront = customGarmentFront,
            customGarmentBack = customGarmentBack,
            customBottomFront = customBottomFront,
            customBottomBack = customBottomBack,
        )
        viewModel.updateGarmentImages(nextGarmentMap)
    }

    fun syncAllInputs(
        allowSampleBacks: Boolean,
        customPersonFront: Pair<MediaRef, ByteArray>?,
        customPersonBack: Pair<MediaRef, ByteArray>?,
        customGarmentFront: Pair<MediaRef, ByteArray>?,
        customGarmentBack: Pair<MediaRef, ByteArray>?,
        customBottomFront: Pair<MediaRef, ByteArray>?,
        customBottomBack: Pair<MediaRef, ByteArray>?,
    ) {
        if (inMemoryMediaStore != null) {
            customPersonFront?.let { inMemoryMediaStore.putMedia(it.first, it.second) }
            customPersonBack?.let { inMemoryMediaStore.putMedia(it.first, it.second) }
            attachStampedFakeOutputIfNeeded(
                filesDir = filesDir,
                baseOutfit = baseOutfit,
                mediaStore = inMemoryMediaStore,
                hasCustomLocalFiles = customPersonFront != null ||
                    customPersonBack != null ||
                    customGarmentFront != null ||
                    customGarmentBack != null ||
                    customBottomFront != null ||
                    customBottomBack != null,
            )
        }

        val nextPersonImages = resolveDemoPersonImages(
            filesDir = filesDir,
            executionMode = executionMode,
            primaryItemId = primaryItemId,
            secondaryBottomItemId = secondaryBottomItemId,
            allowSampleBacks = allowSampleBacks,
            customPersonFront = customPersonFront,
            customPersonBack = customPersonBack,
        )
        if (nextPersonImages != lastPersonImages) {
            lastPersonImages = nextPersonImages
            profilePort.update(
                TryOnPersonProfileSnapshot(
                    profileId = "profile-dev-1",
                    revision = 2L,
                    bodyImages = nextPersonImages,
                ),
            )
        }

        syncGarmentInputs(
            allowSampleBacks = allowSampleBacks,
            customGarmentFront = customGarmentFront,
            customGarmentBack = customGarmentBack,
            customBottomFront = customBottomFront,
            customBottomBack = customBottomBack,
        )
    }

    fun close() {
        vmScope.cancel()
        httpClient?.close()
    }
}

private fun attachStampedFakeOutputIfNeeded(
    filesDir: File,
    baseOutfit: TryOnOutfitSnapshot,
    mediaStore: FakeInMemoryMediaStore,
    hasCustomLocalFiles: Boolean,
) {
    if (!hasCustomLocalFiles || mediaStore.customOutputPathProvider != null) return
    val outputDir = File(filesDir, "tryon_demo_outputs").apply { mkdirs() }
    mediaStore.customOutputPathProvider = { jobId: String, view: ImageView, counter: Int, _: ByteArray ->
        val outFile = File(outputDir, "fake_demo_${jobId}_${view.name.lowercase()}_$counter.png")
        renderStampedFakeOutputPng(
            targetFile = outFile,
            view = view,
            outfitLabel = baseOutfit.items.joinToString(" + ") { it.label },
        )
        outFile.absolutePath
    }
}

private fun resolveDemoPersonImages(
    filesDir: File,
    executionMode: DemoExecutionMode,
    primaryItemId: String,
    secondaryBottomItemId: String?,
    allowSampleBacks: Boolean,
    customPersonFront: Pair<MediaRef, ByteArray>?,
    customPersonBack: Pair<MediaRef, ByteArray>?,
): ImagePair {
    if (executionMode == DemoExecutionMode.IN_MEMORY_FAKE) {
        return ImagePair(
            front = customPersonFront?.first ?: FakeTryOnFixtures.personFrontRef,
            back = customPersonBack?.first ?: FakeTryOnFixtures.personBackRef,
        )
    }

    val discoveredRealInputs = loadRealPrivateInputsFromAppFiles(
        filesDir = filesDir,
        primaryItemId = primaryItemId,
        secondaryBottomItemId = secondaryBottomItemId,
    )
    val fallbackDiskSamples by lazy {
        generateSampleLocalPngInputs(
            filesDir = filesDir,
            primaryItemId = primaryItemId,
            includeBack = allowSampleBacks,
        )
    }
    val effectivePersonFront =
        customPersonFront?.first ?: discoveredRealInputs?.personFront?.first ?: fallbackDiskSamples.personFront.first
    val effectivePersonBack = if (allowSampleBacks) {
        customPersonBack?.first ?: discoveredRealInputs?.personBack?.first ?: fallbackDiskSamples.personBack?.first
    } else {
        // In FOSTA LIVE mode, never prefill sample/discovered back photos; require explicit user selection.
        customPersonBack?.first
    }
    return ImagePair(front = effectivePersonFront, back = effectivePersonBack)
}

private fun resolveDemoGarmentMap(
    filesDir: File,
    executionMode: DemoExecutionMode,
    outfitPreset: DemoOutfitPreset,
    baseOutfit: TryOnOutfitSnapshot,
    baseGarmentMap: Map<String, ImagePair>,
    primaryItemId: String,
    secondaryBottomItemId: String?,
    allowSampleBacks: Boolean,
    inMemoryMediaStore: FakeInMemoryMediaStore?,
    customGarmentFront: Pair<MediaRef, ByteArray>?,
    customGarmentBack: Pair<MediaRef, ByteArray>?,
    customBottomFront: Pair<MediaRef, ByteArray>?,
    customBottomBack: Pair<MediaRef, ByteArray>?,
): Map<String, ImagePair> {
    if (executionMode == DemoExecutionMode.IN_MEMORY_FAKE) {
        val updatedGarmentMap = baseGarmentMap.toMutableMap()
        val primaryPair = updatedGarmentMap[primaryItemId] ?: ImagePair()
        val effectiveGarmentFront = customGarmentFront?.first ?: primaryPair.front
        val effectiveGarmentBack = customGarmentBack?.first ?: primaryPair.back
        customGarmentFront?.let { inMemoryMediaStore?.putMedia(it.first, it.second) }
        customGarmentBack?.let { inMemoryMediaStore?.putMedia(it.first, it.second) }
        updatedGarmentMap[primaryItemId] = ImagePair(front = effectiveGarmentFront, back = effectiveGarmentBack)

        if (secondaryBottomItemId != null) {
            val bottomPair = updatedGarmentMap[secondaryBottomItemId] ?: ImagePair()
            val effectiveBottomFront = customBottomFront?.first ?: bottomPair.front
            val effectiveBottomBack = customBottomBack?.first ?: bottomPair.back
            customBottomFront?.let { inMemoryMediaStore?.putMedia(it.first, it.second) }
            customBottomBack?.let { inMemoryMediaStore?.putMedia(it.first, it.second) }
            updatedGarmentMap[secondaryBottomItemId] = ImagePair(front = effectiveBottomFront, back = effectiveBottomBack)
        }
        return updatedGarmentMap
    }

    val discoveredRealInputs = loadRealPrivateInputsFromAppFiles(
        filesDir = filesDir,
        primaryItemId = primaryItemId,
        secondaryBottomItemId = secondaryBottomItemId,
    )
    val fallbackDiskSamples by lazy {
        generateSampleLocalPngInputs(
            filesDir = filesDir,
            primaryItemId = primaryItemId,
            includeBack = allowSampleBacks,
        )
    }

    val effectiveGarmentFront =
        customGarmentFront?.first ?: discoveredRealInputs?.garmentFront?.first ?: fallbackDiskSamples.garmentFront.first
    val effectiveGarmentBack = if (allowSampleBacks) {
        customGarmentBack?.first ?: discoveredRealInputs?.garmentBack?.first ?: fallbackDiskSamples.garmentBack?.first
    } else {
        customGarmentBack?.first
    }

    return baseOutfit.items.associate { item ->
        when (item.itemId) {
            primaryItemId -> {
                item.itemId to ImagePair(front = effectiveGarmentFront, back = effectiveGarmentBack)
            }
            secondaryBottomItemId -> {
                val includeSampleBack = allowSampleBacks && outfitPreset != DemoOutfitPreset.OUTERWEAR_NO_BACK
                val secondaryPair = generateSecondaryGarmentSamplePair(
                    filesDir = filesDir,
                    itemId = item.itemId,
                    label = item.label,
                    includeBack = includeSampleBack,
                )
                val frontRef = customBottomFront?.first
                    ?: discoveredRealInputs?.bottomFront?.first
                    ?: secondaryPair.front
                val backRef = if (!allowSampleBacks) {
                    customBottomBack?.first
                } else if (!includeSampleBack && customBottomBack == null && discoveredRealInputs?.bottomBack == null) {
                    null
                } else {
                    customBottomBack?.first
                        ?: discoveredRealInputs?.bottomBack?.first
                        ?: secondaryPair.back
                }
                item.itemId to ImagePair(front = frontRef, back = backRef)
            }
            else -> {
                val secondaryPair = generateSecondaryGarmentSamplePair(
                    filesDir = filesDir,
                    itemId = item.itemId,
                    label = item.label,
                    includeBack = allowSampleBacks && outfitPreset != DemoOutfitPreset.OUTERWEAR_NO_BACK,
                )
                item.itemId to secondaryPair
            }
        }
    }
}

private fun buildDemoSessionEnvironment(
    filesDir: File,
    cacheDir: File,
    noBackupFilesDir: File,
    executionMode: DemoExecutionMode,
    backendBaseUrl: String,
    localAuthToken: String?,
    outfitPreset: DemoOutfitPreset,
    scenarioPreset: DemoScenarioPreset,
    delayOverrideMs: Long?,
    initialAllowSampleBacks: Boolean,
    customPersonFront: Pair<MediaRef, ByteArray>?,
    customPersonBack: Pair<MediaRef, ByteArray>?,
    customGarmentFront: Pair<MediaRef, ByteArray>?,
    customGarmentBack: Pair<MediaRef, ByteArray>?,
    customBottomFront: Pair<MediaRef, ByteArray>?,
    customBottomBack: Pair<MediaRef, ByteArray>?,
): DemoSessionEnvironment {
    val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val (baseOutfit, baseGarmentMap) = when (outfitPreset) {
        DemoOutfitPreset.WORK_THREE_ITEMS -> {
            FakeTryOnFixtures.editorialWorkOutfit to FakeTryOnFixtures.defaultGarmentImages
        }
        DemoOutfitPreset.CASUAL_SINGLE_TOP -> {
            val poloOutfit = TryOnOutfitSnapshot(
                outfitId = "outfit-demo-casual-02",
                wardrobeSnapshotId = "snap-2026-11",
                explanation = "Áo polo dệt kim xanh navy phom vừa vặn, phù hợp dạo phố cuối tuần.",
                items = listOf(
                    TryOnItemSummary(
                        itemId = "item-top-linen",
                        label = "Áo polo dệt kim xanh navy",
                        category = Category.TOP,
                        colors = listOf("NAVY"),
                        accentHex = "#24364D",
                        revision = 1L,
                    ),
                ),
            )
            poloOutfit to mapOf(
                "item-top-linen" to ImagePair(
                    front = FakeTryOnFixtures.topFrontRef,
                    back = FakeTryOnFixtures.topBackRef,
                ),
            )
        }
        DemoOutfitPreset.OUTERWEAR_NO_BACK -> {
            val blazerOutfit = TryOnOutfitSnapshot(
                outfitId = "outfit-demo-blazer-03",
                wardrobeSnapshotId = "snap-2026-11",
                explanation = "Áo khoác blazer màu be phối quần jeans xanh (quần chưa chụp ảnh mặt sau).",
                items = listOf(
                    TryOnItemSummary(
                        itemId = "item-top-linen",
                        label = "Áo khoác blazer be nhạt",
                        category = Category.OUTERWEAR,
                        colors = listOf("BEIGE"),
                        accentHex = "#CBB89D",
                        revision = 1L,
                    ),
                    TryOnItemSummary(
                        itemId = "item-trouser-noir",
                        label = "Quần jeans xanh cổ điển (thiếu ảnh sau)",
                        category = Category.BOTTOM,
                        colors = listOf("BLUE"),
                        accentHex = "#395675",
                        revision = 1L,
                    ),
                ),
            )
            blazerOutfit to mapOf(
                "item-top-linen" to ImagePair(
                    front = FakeTryOnFixtures.topFrontRef,
                    back = FakeTryOnFixtures.topBackRef,
                ),
                "item-trouser-noir" to ImagePair(
                    front = FakeTryOnFixtures.bottomFrontRef,
                    back = null,
                ),
            )
        }
    }

    val primaryItemId = baseOutfit.items.first().itemId
    val secondaryBottomItemId = baseOutfit.items.firstOrNull { it.category == Category.BOTTOM }?.itemId

    if (
        executionMode == DemoExecutionMode.BACKEND_FOSTA_E2E ||
        executionMode == DemoExecutionMode.BACKEND_OPENAI_E2E
    ) {
        return buildBackendServerEnvironment(
            filesDir = filesDir,
            cacheDir = cacheDir,
            noBackupFilesDir = noBackupFilesDir,
            vmScope = vmScope,
            executionMode = executionMode,
            backendBaseUrl = backendBaseUrl,
            localAuthToken = localAuthToken,
            outfitPreset = outfitPreset,
            baseOutfit = baseOutfit,
            baseGarmentMap = baseGarmentMap,
            primaryItemId = primaryItemId,
            secondaryBottomItemId = secondaryBottomItemId,
            initialAllowSampleBacks = initialAllowSampleBacks,
            customPersonFront = customPersonFront,
            customPersonBack = customPersonBack,
            customGarmentFront = customGarmentFront,
            customGarmentBack = customGarmentBack,
            customBottomFront = customBottomFront,
            customBottomBack = customBottomBack,
        )
    }

    val mediaStore = FakeTryOnFixtures.createPopulatedMediaStore()
    customPersonFront?.let { mediaStore.putMedia(it.first, it.second) }
    customPersonBack?.let { mediaStore.putMedia(it.first, it.second) }

    val initialPersonImages = resolveDemoPersonImages(
        filesDir = filesDir,
        executionMode = executionMode,
        primaryItemId = primaryItemId,
        secondaryBottomItemId = secondaryBottomItemId,
        allowSampleBacks = true,
        customPersonFront = customPersonFront,
        customPersonBack = customPersonBack,
    )
    val updatedGarmentMap = resolveDemoGarmentMap(
        filesDir = filesDir,
        executionMode = executionMode,
        outfitPreset = outfitPreset,
        baseOutfit = baseOutfit,
        baseGarmentMap = baseGarmentMap,
        primaryItemId = primaryItemId,
        secondaryBottomItemId = secondaryBottomItemId,
        allowSampleBacks = true,
        inMemoryMediaStore = mediaStore,
        customGarmentFront = customGarmentFront,
        customGarmentBack = customGarmentBack,
        customBottomFront = customBottomFront,
        customBottomBack = customBottomBack,
    )

    // When custom local files are loaded, generate a real local composite PNG clearly stamped FAKE
    attachStampedFakeOutputIfNeeded(
        filesDir = filesDir,
        baseOutfit = baseOutfit,
        mediaStore = mediaStore,
        hasCustomLocalFiles = customPersonFront != null ||
            customPersonBack != null ||
            customGarmentFront != null ||
            customGarmentBack != null ||
            customBottomFront != null ||
            customBottomBack != null,
    )

    val personSnapshot = TryOnPersonProfileSnapshot(
        profileId = "profile-dev-1",
        revision = 2L,
        bodyImages = initialPersonImages,
    )
    val profilePort = FakeTryOnProfilePort(initial = personSnapshot)
    val privacyCoordinator = TryOnPrivacyCoordinator(mediaStore = mediaStore)

    val vtoService = FakeVirtualTryOnService(
        initialCapability = scenarioPreset.capability,
        initialScenario = scenarioPreset.scenario,
        mediaStore = mediaStore,
        privacyCoordinator = privacyCoordinator,
        stepDelayMs = delayOverrideMs ?: scenarioPreset.stepDelayMs,
        scope = vmScope,
    )

    val vm = TryOnViewModel(
        tryOnService = vtoService,
        profilePort = profilePort,
        privacyCoordinator = privacyCoordinator,
        initialOutfit = baseOutfit,
        initialGarmentImages = updatedGarmentMap,
    )

    return DemoSessionEnvironment(
        viewModel = vm,
        vmScope = vmScope,
        primaryItemId = primaryItemId,
        secondaryBottomItemId = secondaryBottomItemId,
        filesDir = filesDir,
        executionMode = executionMode,
        outfitPreset = outfitPreset,
        baseOutfit = baseOutfit,
        baseGarmentMap = baseGarmentMap,
        profilePort = profilePort,
        inMemoryMediaStore = mediaStore,
        initialPersonImages = initialPersonImages,
    )
}

private fun buildBackendServerEnvironment(
    filesDir: File,
    cacheDir: File,
    noBackupFilesDir: File,
    vmScope: CoroutineScope,
    executionMode: DemoExecutionMode,
    backendBaseUrl: String,
    localAuthToken: String?,
    outfitPreset: DemoOutfitPreset,
    baseOutfit: TryOnOutfitSnapshot,
    baseGarmentMap: Map<String, ImagePair>,
    primaryItemId: String,
    secondaryBottomItemId: String?,
    initialAllowSampleBacks: Boolean,
    customPersonFront: Pair<MediaRef, ByteArray>?,
    customPersonBack: Pair<MediaRef, ByteArray>?,
    customGarmentFront: Pair<MediaRef, ByteArray>?,
    customGarmentBack: Pair<MediaRef, ByteArray>?,
    customBottomFront: Pair<MediaRef, ByteArray>?,
    customBottomBack: Pair<MediaRef, ByteArray>?,
): DemoSessionEnvironment {
    val initialPersonImages = resolveDemoPersonImages(
        filesDir = filesDir,
        executionMode = executionMode,
        primaryItemId = primaryItemId,
        secondaryBottomItemId = secondaryBottomItemId,
        allowSampleBacks = initialAllowSampleBacks,
        customPersonFront = customPersonFront,
        customPersonBack = customPersonBack,
    )
    val updatedGarmentMap = resolveDemoGarmentMap(
        filesDir = filesDir,
        executionMode = executionMode,
        outfitPreset = outfitPreset,
        baseOutfit = baseOutfit,
        baseGarmentMap = baseGarmentMap,
        primaryItemId = primaryItemId,
        secondaryBottomItemId = secondaryBottomItemId,
        allowSampleBacks = initialAllowSampleBacks,
        inMemoryMediaStore = null,
        customGarmentFront = customGarmentFront,
        customGarmentBack = customGarmentBack,
        customBottomFront = customBottomFront,
        customBottomBack = customBottomBack,
    )

    val mediaStore = LocalDiskTryOnMediaStore(
        filesRootDir = filesDir,
        cacheRootDir = cacheDir,
    )
    val localJobStore = AtomicFileTryOnLocalJobStore(
        File(noBackupFilesDir, "tryon_demo_backend_jobs_v2.json"),
    )
    val privacyCoordinator = TryOnPrivacyCoordinator(mediaStore = mediaStore)

    val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(260, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
    }
    val transport = KtorTryOnHttpTransport(
        httpClient = httpClient,
        baseUrl = backendBaseUrl,
        localAuthToken = localAuthToken,
    )

    val fallbackProviderId = if (executionMode == DemoExecutionMode.BACKEND_FOSTA_E2E) "fosta" else "openai"
    val sessionId = if (executionMode == DemoExecutionMode.BACKEND_FOSTA_E2E) {
        "emulator-fosta-session"
    } else {
        "emulator-openai-session"
    }

    val client = AndroidVirtualTryOnClient(
        capabilityProvider = {
            transport.fetchCapabilities(sessionId).getOrElse { err ->
                TryOnCapability(
                    decision = FeasibilityDecision.UNKNOWN,
                    supportedViews = emptySet(),
                    supportedCategories = emptySet(),
                    maxGarmentsPerJob = 0,
                    supportsNativePairedViews = false,
                    runtimeVerifiedViews = emptySet(),
                    providerId = fallbackProviderId,
                    providerConfigVersion = "unreachable",
                    executionMode = "LOCKED",
                    lockedReason = "Chưa kết nối được FitCheck Try-On Local Server tại $backendBaseUrl (${err.message ?: "offline"}). Hãy khởi chạy proxy/scripts/tryon_local_server.ts rồi bấm 'Làm mới kết nối Backend'.",
                )
            }
        },
        transport = transport,
        localJobStore = localJobStore,
        mediaStore = mediaStore,
        privacyCoordinator = privacyCoordinator,
        scope = vmScope,
    )

    val personSnapshot = TryOnPersonProfileSnapshot(
        profileId = "profile-dev-1",
        revision = 2L,
        bodyImages = initialPersonImages,
    )
    val profilePort = FakeTryOnProfilePort(initial = personSnapshot)

    val vm = TryOnViewModel(
        tryOnService = client,
        profilePort = profilePort,
        privacyCoordinator = privacyCoordinator,
        initialOutfit = baseOutfit,
        initialGarmentImages = updatedGarmentMap,
        sessionId = sessionId,
    )

    return DemoSessionEnvironment(
        viewModel = vm,
        vmScope = vmScope,
        primaryItemId = primaryItemId,
        secondaryBottomItemId = secondaryBottomItemId,
        filesDir = filesDir,
        executionMode = executionMode,
        outfitPreset = outfitPreset,
        baseOutfit = baseOutfit,
        baseGarmentMap = baseGarmentMap,
        profilePort = profilePort,
        httpClient = httpClient,
        initialPersonImages = initialPersonImages,
    )
}

private data class GeneratedLocalPngSet(
    val personFront: Pair<MediaRef, ByteArray>,
    val personBack: Pair<MediaRef, ByteArray>?,
    val garmentFront: Pair<MediaRef, ByteArray>,
    val garmentBack: Pair<MediaRef, ByteArray>?,
)

private data class RealPrivateInputSet(
    val personFront: Pair<MediaRef, ByteArray>?,
    val personBack: Pair<MediaRef, ByteArray>?,
    val garmentFront: Pair<MediaRef, ByteArray>?,
    val garmentBack: Pair<MediaRef, ByteArray>?,
    val bottomFront: Pair<MediaRef, ByteArray>?,
    val bottomBack: Pair<MediaRef, ByteArray>?,
)

private fun loadRealPrivateInputsFromAppFiles(
    filesDir: File,
    primaryItemId: String,
    secondaryBottomItemId: String? = null,
): RealPrivateInputSet? {
    val realDir = File(filesDir, "vto_real_inputs")
    if (!realDir.exists() || !realDir.isDirectory) return null

    fun findSlot(prefixes: List<String>, slotId: String, ownerId: String, scope: MediaScope, view: ImageView): Pair<MediaRef, ByteArray>? {
        val extensions = listOf("jpg", "jpeg", "png", "webp")
        val file = prefixes.asSequence().flatMap { prefix ->
            extensions.asSequence().map { ext -> File(realDir, "$prefix.$ext") }
        }.firstOrNull { it.exists() && it.isFile } ?: return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val header = TryOnImageValidator.inspect(bytes).getOrNull() ?: return null
        val ref = MediaRef(
            id = "$slotId-${file.lastModified()}",
            ownerEntityId = ownerId,
            scope = scope,
            view = view,
            localRelativePath = file.absolutePath,
            mimeType = header.mimeType,
            width = header.orientedWidth,
            height = header.orientedHeight,
            byteCount = bytes.size.toLong(),
            contentHash = sha256Hex(bytes),
            revision = 2L,
        )
        return ref to bytes
    }

    val pf = findSlot(listOf("person_front"), "real-person-front", "profile-dev-1", MediaScope.PERSON, ImageView.FRONT)
    val pb = findSlot(listOf("person_back"), "real-person-back", "profile-dev-1", MediaScope.PERSON, ImageView.BACK)
    val gf = findSlot(listOf("top_front", "garment_front"), "real-garment-front", primaryItemId, MediaScope.GARMENT, ImageView.FRONT)
    val gb = findSlot(listOf("top_back", "garment_back"), "real-garment-back", primaryItemId, MediaScope.GARMENT, ImageView.BACK)
    val bottomOwnerId = secondaryBottomItemId ?: "item-trouser-noir"
    val bf = findSlot(listOf("bottom_front"), "real-bottom-front", bottomOwnerId, MediaScope.GARMENT, ImageView.FRONT)
    val bb = findSlot(listOf("bottom_back"), "real-bottom-back", bottomOwnerId, MediaScope.GARMENT, ImageView.BACK)

    val hasAnyPerson = pf != null || pb != null
    val hasAnyGarment = gf != null || gb != null || bf != null || bb != null
    if (!hasAnyPerson || !hasAnyGarment) return null

    return RealPrivateInputSet(
        personFront = pf,
        personBack = pb,
        garmentFront = gf,
        garmentBack = gb,
        bottomFront = bf,
        bottomBack = bb,
    )
}

private fun generateSecondaryGarmentSamplePair(
    filesDir: File,
    itemId: String,
    label: String,
    includeBack: Boolean = true,
): ImagePair {
    val localDir = File(filesDir, "tryon_demo_local_inputs").apply { mkdirs() }
    val safeSlug = itemId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val frontFile = File(localDir, "garment_${safeSlug}_front.png")
    val frontBytes = renderLabeledSamplePng(
        targetFile = frontFile,
        bgColor = AndroidColor.rgb(240, 238, 234),
        accentColor = AndroidColor.rgb(66, 78, 92),
        header = "SAMPLE GARMENT (FRONT)",
        title = label.take(28),
        subtitle = "384x512 px • Local front file",
        isGarmentOnly = true,
    )
    val frontRef = MediaRef(
        id = "local-sample-$safeSlug-front",
        ownerEntityId = itemId,
        scope = MediaScope.GARMENT,
        view = ImageView.FRONT,
        localRelativePath = frontFile.absolutePath,
        mimeType = "image/png",
        width = 384,
        height = 512,
        byteCount = frontBytes.size.toLong(),
        contentHash = sha256Hex(frontBytes),
        revision = 1L,
    )

    val backRef = if (includeBack) {
        val backFile = File(localDir, "garment_${safeSlug}_back.png")
        val backBytes = renderLabeledSamplePng(
            targetFile = backFile,
            bgColor = AndroidColor.rgb(233, 231, 226),
            accentColor = AndroidColor.rgb(52, 64, 80),
            header = "SAMPLE GARMENT (BACK)",
            title = "${label.take(22)} (SAU)",
            subtitle = "384x512 px • Local back file",
            isGarmentOnly = true,
        )
        MediaRef(
            id = "local-sample-$safeSlug-back",
            ownerEntityId = itemId,
            scope = MediaScope.GARMENT,
            view = ImageView.BACK,
            localRelativePath = backFile.absolutePath,
            mimeType = "image/png",
            width = 384,
            height = 512,
            byteCount = backBytes.size.toLong(),
            contentHash = sha256Hex(backBytes),
            revision = 1L,
        )
    } else {
        null
    }

    return ImagePair(front = frontRef, back = backRef)
}

private fun generateSampleLocalPngInputs(
    filesDir: File,
    primaryItemId: String,
    includeBack: Boolean = true,
): GeneratedLocalPngSet {
    val localDir = File(filesDir, "tryon_demo_local_inputs").apply { mkdirs() }

    val pfFile = File(localDir, "person_front_sample.png")
    val pfBytes = renderLabeledSamplePng(
        targetFile = pfFile,
        bgColor = AndroidColor.rgb(240, 234, 225),
        accentColor = AndroidColor.rgb(92, 74, 62),
        header = "SAMPLE PERSON INPUT (LOCAL)",
        title = "ANH NGUOI - MAT TRUOC",
        subtitle = "384x512 px • Luu cuc bo tren may ao",
        isGarmentOnly = false,
    )
    val personBackPair = if (includeBack) {
        val pbFile = File(localDir, "person_back_sample.png")
        val pbBytes = renderLabeledSamplePng(
            targetFile = pbFile,
            bgColor = AndroidColor.rgb(235, 229, 220),
            accentColor = AndroidColor.rgb(74, 90, 108),
            header = "SAMPLE PERSON INPUT (LOCAL)",
            title = "ANH NGUOI - MAT SAU",
            subtitle = "384x512 px • Luu cuc bo tren may ao",
            isGarmentOnly = false,
        )
        MediaRef(
            id = "local-sample-person-back",
            ownerEntityId = "profile-dev-1",
            scope = MediaScope.PERSON,
            view = ImageView.BACK,
            localRelativePath = pbFile.absolutePath,
            mimeType = "image/png",
            width = 384,
            height = 512,
            byteCount = pbBytes.size.toLong(),
            contentHash = sha256Hex(pbBytes),
            revision = 2L,
        ) to pbBytes
    } else {
        null
    }
    val gfFile = File(localDir, "garment_front_sample.png")
    val gfBytes = renderLabeledSamplePng(
        targetFile = gfFile,
        bgColor = AndroidColor.rgb(245, 241, 235),
        accentColor = AndroidColor.rgb(168, 92, 64),
        header = "SAMPLE GARMENT INPUT (LOCAL)",
        title = "AO MAU - MAT TRUOC",
        subtitle = "384x512 px • Luu cuc bo tren may ao",
        isGarmentOnly = true,
    )
    val garmentBackPair = if (includeBack) {
        val gbFile = File(localDir, "garment_back_sample.png")
        val gbBytes = renderLabeledSamplePng(
            targetFile = gbFile,
            bgColor = AndroidColor.rgb(242, 238, 231),
            accentColor = AndroidColor.rgb(140, 78, 54),
            header = "SAMPLE GARMENT INPUT (LOCAL)",
            title = "AO MAU - MAT SAU",
            subtitle = "384x512 px • Luu cuc bo tren may ao",
            isGarmentOnly = true,
        )
        MediaRef(
            id = "local-sample-garment-back",
            ownerEntityId = primaryItemId,
            scope = MediaScope.GARMENT,
            view = ImageView.BACK,
            localRelativePath = gbFile.absolutePath,
            mimeType = "image/png",
            width = 384,
            height = 512,
            byteCount = gbBytes.size.toLong(),
            contentHash = sha256Hex(gbBytes),
            revision = 1L,
        ) to gbBytes
    } else {
        null
    }

    return GeneratedLocalPngSet(
        personFront = MediaRef(
            id = "local-sample-person-front",
            ownerEntityId = "profile-dev-1",
            scope = MediaScope.PERSON,
            view = ImageView.FRONT,
            localRelativePath = pfFile.absolutePath,
            mimeType = "image/png",
            width = 384,
            height = 512,
            byteCount = pfBytes.size.toLong(),
            contentHash = sha256Hex(pfBytes),
            revision = 2L,
        ) to pfBytes,
        personBack = personBackPair,
        garmentFront = MediaRef(
            id = "local-sample-garment-front",
            ownerEntityId = primaryItemId,
            scope = MediaScope.GARMENT,
            view = ImageView.FRONT,
            localRelativePath = gfFile.absolutePath,
            mimeType = "image/png",
            width = 384,
            height = 512,
            byteCount = gfBytes.size.toLong(),
            contentHash = sha256Hex(gfBytes),
            revision = 1L,
        ) to gfBytes,
        garmentBack = garmentBackPair,
    )
}

private fun renderLabeledSamplePng(
    targetFile: File,
    bgColor: Int,
    accentColor: Int,
    header: String,
    title: String,
    subtitle: String,
    isGarmentOnly: Boolean,
): ByteArray {
    val width = 384
    val height = 512
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(bgColor)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = accentColor
    paint.style = Paint.Style.FILL

    if (!isGarmentOnly) {
        // Head + torso + trousers silhouette
        canvas.drawCircle(width * 0.5f, height * 0.22f, 34f, paint)
        canvas.drawRoundRect(RectF(width * 0.28f, height * 0.31f, width * 0.72f, height * 0.58f), 18f, 18f, paint)
        paint.alpha = 180
        canvas.drawRoundRect(RectF(width * 0.33f, height * 0.59f, width * 0.67f, height * 0.82f), 12f, 12f, paint)
    } else {
        // Garment card silhouette
        canvas.drawRoundRect(RectF(width * 0.22f, height * 0.24f, width * 0.78f, height * 0.72f), 22f, 22f, paint)
    }

    // Centered watermark ribbon (avoids top/bottom Compose badge areas)
    paint.alpha = 235
    paint.color = AndroidColor.rgb(38, 33, 30)
    val ribbonTop = height * 0.40f
    val ribbonBottom = height * 0.56f
    canvas.drawRoundRect(RectF(20f, ribbonTop, width - 20f, ribbonBottom), 12f, 12f, paint)

    paint.color = AndroidColor.WHITE
    paint.textSize = 15f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(header, width * 0.5f, ribbonTop + 26f, paint)
    paint.textSize = 16f
    paint.color = AndroidColor.rgb(235, 215, 195)
    canvas.drawText(title, width * 0.5f, ribbonTop + 48f, paint)
    paint.textSize = 13f
    canvas.drawText(subtitle, width * 0.5f, ribbonTop + 68f, paint)

    val bos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
    bmp.recycle()
    val bytes = bos.toByteArray()
    FileOutputStream(targetFile).use { it.write(bytes) }
    return bytes
}

private fun renderStampedFakeOutputPng(
    targetFile: File,
    view: ImageView,
    outfitLabel: String,
) {
    val viewText = if (view == ImageView.FRONT) "MAT TRUOC (FRONT)" else "MAT SAU (BACK)"
    renderLabeledSamplePng(
        targetFile = targetFile,
        bgColor = AndroidColor.rgb(236, 228, 216),
        accentColor = AndroidColor.rgb(156, 81, 53),
        header = "KET QUA GIA LAP (FAKE - KHONG QUA AI)",
        title = "MO PHONG CUC BO: $viewText",
        subtitle = outfitLabel.take(36),
        isGarmentOnly = false,
    )
}

private val pickedMediaSeq = AtomicLong(0L)

private fun importUriToLocalRef(
    context: android.content.Context,
    uri: Uri,
    slotId: String,
    ownerId: String,
    scope: MediaScope,
    view: ImageView,
): Pair<MediaRef, ByteArray>? {
    return runCatching {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        // Allow up to 20 MiB raw camera photos from gallery; LocalDiskTryOnMediaStore downscales/normalizes EXIF on upload
        if (rawBytes.isEmpty() || rawBytes.size > 20 * 1024 * 1024) return null

        val inspected = TryOnImageValidator.inspect(rawBytes).getOrNull()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val width = inspected?.orientedWidth ?: bounds.outWidth.takeIf { it > 0 } ?: 768
        val height = inspected?.orientedHeight ?: bounds.outHeight.takeIf { it > 0 } ?: 1024
        val mime = inspected?.mimeType ?: bounds.outMimeType ?: "image/jpeg"

        val localDir = File(context.cacheDir, "tryon_demo_picked").apply { mkdirs() }
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        listOf("jpg", "png", "webp").filter { it != ext }.forEach { staleExt ->
            File(localDir, "$slotId.$staleExt").delete()
        }
        val outFile = File(localDir, "$slotId.$ext")
        FileOutputStream(outFile).use { it.write(rawBytes) }

        val ref = MediaRef(
            id = "$slotId-${System.currentTimeMillis()}-${pickedMediaSeq.incrementAndGet()}",
            ownerEntityId = ownerId,
            scope = scope,
            view = view,
            localRelativePath = outFile.absolutePath,
            mimeType = mime,
            width = width,
            height = height,
            byteCount = rawBytes.size.toLong(),
            contentHash = sha256Hex(rawBytes),
            revision = 2L,
        )
        ref to rawBytes
    }.getOrNull()
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return "sha256:" + digest.joinToString("") { "%02x".format(it) }.take(32)
}

@Composable
private fun demoChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.background,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)
