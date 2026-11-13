package com.fitcheckai.feature.tryon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.ui.component.EmptyState
import com.fitcheckai.core.ui.theme.Spacing
import com.fitcheckai.feature.tryon.contract.FeasibilityDecision
import com.fitcheckai.feature.tryon.contract.ImagePair
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.MediaRef
import com.fitcheckai.feature.tryon.contract.QualityReviewState
import com.fitcheckai.feature.tryon.contract.TryOnErrorCode
import com.fitcheckai.feature.tryon.contract.TryOnItemSummary
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.contract.TryOnPresentationMode
import com.fitcheckai.feature.tryon.contract.TryOnResult
import com.fitcheckai.feature.tryon.contract.TryOnSource
import com.fitcheckai.feature.tryon.contract.ViewProcessState
import com.fitcheckai.feature.tryon.contract.ViewResult
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyDisclosure
import java.io.File

object TryOnTestTags {
    const val SCREEN_ROOT = "tryon_screen_root"
    const val FAKE_MODE_BADGE = "tryon_fake_mode_badge"
    const val CAPABILITY_UNAVAILABLE_CARD = "tryon_capability_unavailable"
    const val MODE_FRONT_ONLY = "tryon_mode_front_only"
    const val MODE_BACK_ONLY = "tryon_mode_back_only"
    const val MODE_DUAL_VIEW = "tryon_mode_dual_view"
    const val PRESENTATION_ORIGINAL_PHOTO = "tryon_presentation_original_photo"
    const val PRESENTATION_STUDIO_WHITE = "tryon_presentation_studio_white"
    const val GARMENT_COMBO_TOP = "tryon_garment_combo_top"
    const val GARMENT_COMBO_BOTTOM = "tryon_garment_combo_bottom"
    const val GARMENT_COMBO_TOP_BOTTOM = "tryon_garment_combo_top_bottom"
    const val EXPERIMENTAL_NOTICE = "tryon_experimental_notice"
    const val CONSENT_CHECKBOX_ROW = "tryon_consent_checkbox_row"
    const val SUBMIT_BUTTON = "tryon_submit_button"
    const val CANCEL_BUTTON = "tryon_cancel_button"
    const val RESULT_SECTION = "tryon_result_section"
    const val PARTIAL_BADGE = "tryon_partial_badge"
    const val TAB_FRONT = "tryon_tab_front"
    const val TAB_BACK = "tryon_tab_back"
    const val COMPARE_ORIGINAL_TOGGLE = "tryon_compare_original_toggle"
    const val DELETE_RESULT_BUTTON = "tryon_delete_result_button"
    const val DISCLAIMER_TEXT = "tryon_disclaimer_text"
    const val PROFILE_PHOTO_CTA = "tryon_profile_photo_cta"
    const val PICK_PERSON_FRONT_INLINE = "tryon_pick_person_front_inline"
    const val PICK_PERSON_BACK_INLINE = "tryon_pick_person_back_inline"
    const val PICK_GARMENT_TOP_INLINE = "tryon_pick_garment_top_inline"
    const val PICK_GARMENT_TOP_BACK_INLINE = "tryon_pick_garment_top_back_inline"
    const val PICK_GARMENT_BOTTOM_INLINE = "tryon_pick_garment_bottom_inline"
    const val PICK_GARMENT_BOTTOM_BACK_INLINE = "tryon_pick_garment_bottom_back_inline"
    const val PREVIEW_PERSON_FRONT = "tryon_preview_person_front"
    const val PREVIEW_PERSON_BACK = "tryon_preview_person_back"
    const val PREVIEW_GARMENT_TOP_FRONT = "tryon_preview_garment_top_front"
    const val PREVIEW_GARMENT_TOP_BACK = "tryon_preview_garment_top_back"
    const val PREVIEW_GARMENT_BOTTOM_FRONT = "tryon_preview_garment_bottom_front"
    const val PREVIEW_GARMENT_BOTTOM_BACK = "tryon_preview_garment_bottom_back"
    const val REFRESH_CONNECTION_BUTTON = "tryon_refresh_connection_button"
}

// Track A-aligned shape & surface tokens (matching WardrobeDesign.kt without cross-module internal imports)
private val PanelCornerShape = RoundedCornerShape(22.dp)
private val PhotoFrameCornerShape = RoundedCornerShape(20.dp)
private val ThumbnailCornerShape = RoundedCornerShape(16.dp)
private val ActionButtonCornerShape = RoundedCornerShape(16.dp)
private val NoticeCornerShape = RoundedCornerShape(14.dp)
private val PillChipShape = RoundedCornerShape(50)

@Composable
private fun editorialPanelContainerColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    return if (isDark) Color(0xFF22251F) else Color(0xFFFFFEFB)
}

@Composable
private fun TryOnEditorialPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = editorialPanelContainerColor(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelCornerShape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun TryOnRoute(
    viewModel: TryOnViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfilePhotos: () -> Unit,
    onSelectDifferentOutfit: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier,
    onPickPersonPhoto: ((ImageView) -> Unit)? = null,
    onPickGarmentPhoto: ((String, ImageView) -> Unit)? = null,
    onRefreshConnection: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TryOnScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onNavigateToProfilePhotos = onNavigateToProfilePhotos,
        onSelectDifferentOutfit = onSelectDifferentOutfit,
        onSelectViewMode = viewModel::selectViewMode,
        onSelectPresentationMode = viewModel::selectPresentationMode,
        onSelectGarmentCombo = viewModel::selectGarmentCombo,
        onToggleItem = viewModel::toggleSelectedItem,
        onConsentChanged = viewModel::setConsentConfirmed,
        onSubmit = viewModel::submitTryOn,
        onCancelJob = viewModel::cancelActiveJob,
        onSelectResultTab = viewModel::selectResultTab,
        onToggleCompareOriginal = viewModel::setCompareWithOriginal,
        onDeleteResult = viewModel::deleteLatestResult,
        onPrepareFreshAttempt = viewModel::prepareFreshAttempt,
        modifier = modifier,
        onPickPersonPhoto = onPickPersonPhoto,
        onPickGarmentPhoto = onPickGarmentPhoto,
        onRefreshConnection = onRefreshConnection,
    )
}

@Composable
fun TryOnScreen(
    state: TryOnUiState,
    onNavigateBack: () -> Unit,
    onNavigateToProfilePhotos: () -> Unit,
    onSelectDifferentOutfit: () -> Unit,
    onSelectViewMode: (TryOnViewMode) -> Unit,
    onToggleItem: (String) -> Unit,
    onConsentChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancelJob: () -> Unit,
    onSelectResultTab: (ImageView) -> Unit,
    onToggleCompareOriginal: (Boolean) -> Unit,
    onDeleteResult: () -> Unit,
    onPrepareFreshAttempt: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectPresentationMode: (TryOnPresentationMode) -> Unit = {},
    onSelectGarmentCombo: (TryOnGarmentCombo) -> Unit = {},
    onPickPersonPhoto: ((ImageView) -> Unit)? = null,
    onPickGarmentPhoto: ((String, ImageView) -> Unit)? = null,
    onRefreshConnection: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(TryOnTestTags.SCREEN_ROOT),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            state.isLoadingInitial -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.outfitSnapshot == null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    EditorialTopBar(
                        isFakeEnvironment = false,
                        isFostaLiveEnvironment = false,
                        onNavigateBack = onNavigateBack,
                    )
                    EmptyState(
                        title = "Chưa chọn trang phục",
                        message = "Hãy chọn một bộ gợi ý từ màn Hôm nay để bắt đầu thử đồ 2D.",
                        actionLabel = "Quay lại chọn bộ đồ",
                        onAction = onSelectDifferentOutfit,
                    )
                }
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isTwoPane = maxWidth >= 600.dp
                    if (isTwoPane) {
                        TwoPaneEditorialLayout(
                            state = state,
                            onNavigateBack = onNavigateBack,
                            onNavigateToProfilePhotos = onNavigateToProfilePhotos,
                            onSelectDifferentOutfit = onSelectDifferentOutfit,
                            onSelectViewMode = onSelectViewMode,
                            onSelectPresentationMode = onSelectPresentationMode,
                            onSelectGarmentCombo = onSelectGarmentCombo,
                            onToggleItem = onToggleItem,
                            onConsentChanged = onConsentChanged,
                            onSubmit = onSubmit,
                            onCancelJob = onCancelJob,
                            onSelectResultTab = onSelectResultTab,
                            onToggleCompareOriginal = onToggleCompareOriginal,
                            onDeleteResult = onDeleteResult,
                            onPrepareFreshAttempt = onPrepareFreshAttempt,
                            onPickPersonPhoto = onPickPersonPhoto,
                            onPickGarmentPhoto = onPickGarmentPhoto,
                            onRefreshConnection = onRefreshConnection,
                        )
                    } else {
                        CompactSingleColumnLayout(
                            state = state,
                            onNavigateBack = onNavigateBack,
                            onNavigateToProfilePhotos = onNavigateToProfilePhotos,
                            onSelectDifferentOutfit = onSelectDifferentOutfit,
                            onSelectViewMode = onSelectViewMode,
                            onSelectPresentationMode = onSelectPresentationMode,
                            onSelectGarmentCombo = onSelectGarmentCombo,
                            onToggleItem = onToggleItem,
                            onConsentChanged = onConsentChanged,
                            onSubmit = onSubmit,
                            onCancelJob = onCancelJob,
                            onSelectResultTab = onSelectResultTab,
                            onToggleCompareOriginal = onToggleCompareOriginal,
                            onDeleteResult = onDeleteResult,
                            onPrepareFreshAttempt = onPrepareFreshAttempt,
                            onPickPersonPhoto = onPickPersonPhoto,
                            onPickGarmentPhoto = onPickGarmentPhoto,
                            onRefreshConnection = onRefreshConnection,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSingleColumnLayout(
    state: TryOnUiState,
    onNavigateBack: () -> Unit,
    onNavigateToProfilePhotos: () -> Unit,
    onSelectDifferentOutfit: () -> Unit,
    onSelectViewMode: (TryOnViewMode) -> Unit,
    onSelectPresentationMode: (TryOnPresentationMode) -> Unit,
    onSelectGarmentCombo: (TryOnGarmentCombo) -> Unit,
    onToggleItem: (String) -> Unit,
    onConsentChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancelJob: () -> Unit,
    onSelectResultTab: (ImageView) -> Unit,
    onToggleCompareOriginal: (Boolean) -> Unit,
    onDeleteResult: () -> Unit,
    onPrepareFreshAttempt: () -> Unit,
    onPickPersonPhoto: ((ImageView) -> Unit)? = null,
    onPickGarmentPhoto: ((String, ImageView) -> Unit)? = null,
    onRefreshConnection: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.activeOrLatestResult?.jobId, state.activeOrLatestResult?.status) {
        if (state.activeOrLatestResult != null) {
            scrollState.scrollTo(0)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        EditorialTopBar(
            isFakeEnvironment = state.isFakeEnvironment && state.isCapabilityAvailable,
            isFostaLiveEnvironment = !state.isFakeEnvironment && state.isFostaProvider && state.isCapabilityAvailable,
            onNavigateBack = onNavigateBack,
        )

        state.bannerError?.let { error ->
            ErrorBannerBlock(
                message = error.message,
                isStale = error.code == TryOnErrorCode.STALE_INPUT,
                onSelectDifferentOutfit = onSelectDifferentOutfit,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }

        state.statusNotice?.let { notice ->
            StatusNoticeBlock(
                notice = notice,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }

        if (!state.isCapabilityAvailable) {
            CapabilityUnavailableSection(
                state = state,
                onSelectDifferentOutfit = onSelectDifferentOutfit,
                onRefreshConnection = onRefreshConnection,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        } else {
            val currentResult = state.activeOrLatestResult
            if (currentResult != null) {
                if (state.isJobActive) {
                    ProcessingStageSection(
                        result = currentResult,
                        onCancelJob = onCancelJob,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = Spacing.md),
                    )
                } else {
                    ResultShowcaseSection(
                        state = state,
                        result = currentResult,
                        onSelectResultTab = onSelectResultTab,
                        onToggleCompareOriginal = onToggleCompareOriginal,
                        onDeleteResult = onDeleteResult,
                        onPrepareFreshAttempt = onPrepareFreshAttempt,
                        onSelectDifferentOutfit = onSelectDifferentOutfit,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = Spacing.md),
                    )
                }
            }

            // Image-First Priority Order (Section 3.B):
            // 1. Ảnh người dùng
            // 2. Ảnh trang phục đã chọn
            // 3. Chọn áo / quần / cả hai
            // 4. Chọn chế độ Original Photo / Studio White
            // 5. Chọn Front / Back
            // 6. Xác nhận quyền xử lý ảnh
            // 7. Nút tạo ảnh
            if (currentResult == null) {
                PersonPhotosPanel(
                    state = state,
                    onNavigateToProfilePhotos = onNavigateToProfilePhotos,
                    onPickPersonPhoto = onPickPersonPhoto,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            SelectedGarmentsPanel(
                state = state,
                onSelectGarmentCombo = onSelectGarmentCombo,
                onToggleItem = onToggleItem,
                onPickGarmentPhoto = onPickGarmentPhoto,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )

            if (currentResult == null) {
                TryOnConfigurationPanel(
                    state = state,
                    onSelectPresentationMode = onSelectPresentationMode,
                    onSelectViewMode = onSelectViewMode,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )

                PrivacyAndConsentSection(
                    state = state,
                    onConsentChanged = onConsentChanged,
                    onSubmit = onSubmit,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun TwoPaneEditorialLayout(
    state: TryOnUiState,
    onNavigateBack: () -> Unit,
    onNavigateToProfilePhotos: () -> Unit,
    onSelectDifferentOutfit: () -> Unit,
    onSelectViewMode: (TryOnViewMode) -> Unit,
    onSelectPresentationMode: (TryOnPresentationMode) -> Unit,
    onSelectGarmentCombo: (TryOnGarmentCombo) -> Unit,
    onToggleItem: (String) -> Unit,
    onConsentChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancelJob: () -> Unit,
    onSelectResultTab: (ImageView) -> Unit,
    onToggleCompareOriginal: (Boolean) -> Unit,
    onDeleteResult: () -> Unit,
    onPrepareFreshAttempt: () -> Unit,
    onPickPersonPhoto: ((ImageView) -> Unit)? = null,
    onPickGarmentPhoto: ((String, ImageView) -> Unit)? = null,
    onRefreshConnection: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EditorialTopBar(
            isFakeEnvironment = state.isFakeEnvironment && state.isCapabilityAvailable,
            isFostaLiveEnvironment = !state.isFakeEnvironment && state.isFostaProvider && state.isCapabilityAvailable,
            onNavigateBack = onNavigateBack,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Left Pane: Image-First Inputs (1. Person -> 2-3. Garments & Combo -> 4-5. Mode & View -> 6-7. Consent & Submit)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                state.bannerError?.let { error ->
                    ErrorBannerBlock(
                        message = error.message,
                        isStale = error.code == TryOnErrorCode.STALE_INPUT,
                        onSelectDifferentOutfit = onSelectDifferentOutfit,
                    )
                }
                state.statusNotice?.let { notice ->
                    StatusNoticeBlock(notice = notice)
                }
                if (state.isCapabilityAvailable) {
                    PersonPhotosPanel(
                        state = state,
                        onNavigateToProfilePhotos = onNavigateToProfilePhotos,
                        onPickPersonPhoto = onPickPersonPhoto,
                    )
                }
                SelectedGarmentsPanel(
                    state = state,
                    onSelectGarmentCombo = onSelectGarmentCombo,
                    onToggleItem = onToggleItem,
                    onPickGarmentPhoto = onPickGarmentPhoto,
                )
                if (state.isCapabilityAvailable) {
                    TryOnConfigurationPanel(
                        state = state,
                        onSelectPresentationMode = onSelectPresentationMode,
                        onSelectViewMode = onSelectViewMode,
                    )
                    if (state.activeOrLatestResult == null) {
                        PrivacyAndConsentSection(
                            state = state,
                            onConsentChanged = onConsentChanged,
                            onSubmit = onSubmit,
                        )
                    }
                }
            }

            // Right Pane: Lookbook Stage / Processing / Result / Unavailable Fallback
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (!state.isCapabilityAvailable) {
                    CapabilityUnavailableSection(
                        state = state,
                        onSelectDifferentOutfit = onSelectDifferentOutfit,
                        onRefreshConnection = onRefreshConnection,
                    )
                } else {
                    val result = state.activeOrLatestResult
                    when {
                        result == null -> {
                            IdleLookbookStagePlaceholder(state = state)
                        }

                        state.isJobActive -> {
                            ProcessingStageSection(
                                result = result,
                                onCancelJob = onCancelJob,
                            )
                        }

                        else -> {
                            ResultShowcaseSection(
                                state = state,
                                result = result,
                                onSelectResultTab = onSelectResultTab,
                                onToggleCompareOriginal = onToggleCompareOriginal,
                                onDeleteResult = onDeleteResult,
                                onPrepareFreshAttempt = onPrepareFreshAttempt,
                                onSelectDifferentOutfit = onSelectDifferentOutfit,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialTopBar(
    isFakeEnvironment: Boolean,
    isFostaLiveEnvironment: Boolean = false,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PHÒNG THỬ ĐỒ 2D",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "Thử đồ ảo",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (isFakeEnvironment) {
            Surface(
                modifier = Modifier.testTag(TryOnTestTags.FAKE_MODE_BADGE),
                shape = PillChipShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            ) {
                Text(
                    text = "CHẾ ĐỘ GIẢ LẬP DEV (FAKE — KHÔNG GỌI AI THẬT)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        } else if (isFostaLiveEnvironment) {
            Surface(
                shape = PillChipShape,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
            ) {
                Text(
                    text = "AI LIVE — FOSTA LUNA (PROXY THỬ NGHIỆM)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CapabilityUnavailableSection(
    state: TryOnUiState,
    onSelectDifferentOutfit: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshConnection: (() -> Unit)? = null,
) {
    val decisionLabel = when (state.capability?.decision) {
        FeasibilityDecision.NO_GO -> "Đã khóa theo kết quả đánh giá chất lượng (NO_GO)"
        else -> "Chưa mở mô phỏng trực tiếp (Cổng kiểm định: UNKNOWN / CORE)"
    }

    TryOnEditorialPanel(
        modifier = modifier.testTag(TryOnTestTags.CAPABILITY_UNAVAILABLE_CARD),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = decisionLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = state.capability?.lockedReason?.takeIf { it.isNotBlank() }
                ?: "Mô phỏng ảnh người thật 2D chỉ kích hoạt khi dịch vụ xử lý ảnh sẵn sàng — ứng dụng không hiển thị ảnh giả lập như kết quả AI thật.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val outfit = state.outfitSnapshot
        if (outfit != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "CÁC MÓN TRONG BỘ GỢI Ý",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = outfit.explanation,
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                outfit.items.forEach { item ->
                    OutfitItemSwatchRow(item = item, statusLabel = categoryLabelVi(item.category))
                }
            }
        }

        if (onRefreshConnection != null) {
            Button(
                onClick = onRefreshConnection,
                shape = ActionButtonCornerShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(TryOnTestTags.REFRESH_CONNECTION_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text("Thử kết nối lại Backend")
            }
        }

        OutlinedButton(
            onClick = onSelectDifferentOutfit,
            shape = ActionButtonCornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Quay lại danh sách gợi ý Hôm nay")
        }
    }
}

/**
 * Priority 1: Ảnh người dùng (Front & Back person reference cards with 3:4 portrait frames).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonPhotosPanel(
    state: TryOnUiState,
    onNavigateToProfilePhotos: () -> Unit,
    modifier: Modifier = Modifier,
    onPickPersonPhoto: ((ImageView) -> Unit)? = null,
) {
    TryOnEditorialPanel(modifier = modifier) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "ẢNH NGƯỜI DÙNG",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = if (state.hasPersonFront && state.hasPersonBack) "Đủ 2 góc nhìn" else "Cần cho góc nhìn chọn",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PersonReferenceSlotCard(
                title = "Mặt trước",
                mediaRef = state.personProfile?.bodyImages?.front,
                missingLabel = "Thiếu ảnh mặt trước",
                isActiveView = ImageView.FRONT in state.requestedViews,
                buttonLabel = "Chọn Trước",
                onPickPhoto = onPickPersonPhoto?.let { picker -> { picker(ImageView.FRONT) } },
                pickButtonTag = TryOnTestTags.PICK_PERSON_FRONT_INLINE,
                previewTag = TryOnTestTags.PREVIEW_PERSON_FRONT,
                modifier = Modifier.weight(1f),
            )
            PersonReferenceSlotCard(
                title = "Mặt sau",
                mediaRef = state.personProfile?.bodyImages?.back,
                missingLabel = "Chưa có ảnh mặt sau",
                isActiveView = ImageView.BACK in state.requestedViews,
                buttonLabel = "Chọn Sau",
                onPickPhoto = onPickPersonPhoto?.let { picker -> { picker(ImageView.BACK) } },
                pickButtonTag = TryOnTestTags.PICK_PERSON_BACK_INLINE,
                previewTag = TryOnTestTags.PREVIEW_PERSON_BACK,
                modifier = Modifier.weight(1f),
            )
        }

        if (!state.hasPersonFront || !state.hasPersonBack) {
            OutlinedButton(
                onClick = onNavigateToProfilePhotos,
                shape = ActionButtonCornerShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(TryOnTestTags.PROFILE_PHOTO_CTA),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = if (!state.hasPersonFront) {
                        "Thêm ảnh toàn thân mặt trước tại Hồ sơ"
                    } else {
                        "Bổ sung ảnh mặt sau tại Hồ sơ"
                    },
                )
            }
        }
    }
}

@Composable
private fun PersonReferenceSlotCard(
    title: String,
    mediaRef: MediaRef?,
    missingLabel: String,
    isActiveView: Boolean,
    buttonLabel: String,
    modifier: Modifier = Modifier,
    onPickPhoto: (() -> Unit)? = null,
    pickButtonTag: String? = null,
    previewTag: String? = null,
) {
    val borderColor = if (isActiveView && mediaRef != null) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = modifier
            .clip(ThumbnailCornerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, borderColor, ThumbnailCornerShape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .widthIn(max = 148.dp)
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .let { mod -> if (previewTag != null) mod.testTag(previewTag) else mod }
                .let { mod -> if (onPickPhoto != null) mod.clickable(onClick = onPickPhoto) else mod },
            contentAlignment = Alignment.Center,
        ) {
            if (mediaRef == null) {
                Text(
                    text = missingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Spacing.xs),
                )
            } else {
                TryOnMediaPreviewSurface(
                    mediaRef = mediaRef,
                    caption = title,
                    isFakeOutput = false,
                    compactThumbnail = true,
                )
            }
        }
        Text(
            text = if (mediaRef != null) "Đã sẵn sàng" else "Chưa có ảnh",
            style = MaterialTheme.typography.labelSmall,
            color = if (mediaRef != null) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
        if (onPickPhoto != null) {
            OutlinedButton(
                onClick = onPickPhoto,
                shape = PillChipShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                    .let { mod -> if (pickButtonTag != null) mod.testTag(pickButtonTag) else mod },
            ) {
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Priority 2 & 3: Ảnh trang phục đã chọn (Real garment thumbnail cards) + Chọn áo / quần / cả hai.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedGarmentsPanel(
    state: TryOnUiState,
    onSelectGarmentCombo: (TryOnGarmentCombo) -> Unit,
    onToggleItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPickGarmentPhoto: ((String, ImageView) -> Unit)? = null,
) {
    val outfit = state.outfitSnapshot ?: return
    val cap = state.capability

    TryOnEditorialPanel(modifier = modifier) {
        Text(
            text = "TRANG PHỤC ĐÃ CHỌN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = outfit.explanation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Visual Garment Thumbnail Cards (Priority 2) — 2-slot Front | Back per item
        if (state.supportedOutfitItems.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.supportedOutfitItems.forEach { item ->
                    val selected = item.itemId in state.selectedItemIds
                    val pair = state.garmentImages[item.itemId]
                    val isPrimaryTop = item.itemId == state.availableTopItem?.itemId
                    val isPrimaryBottom = item.itemId == state.availableBottomItem?.itemId
                    val frontPickTag = when {
                        isPrimaryBottom -> TryOnTestTags.PICK_GARMENT_BOTTOM_INLINE
                        isPrimaryTop -> TryOnTestTags.PICK_GARMENT_TOP_INLINE
                        else -> null
                    }
                    val backPickTag = when {
                        isPrimaryBottom -> TryOnTestTags.PICK_GARMENT_BOTTOM_BACK_INLINE
                        isPrimaryTop -> TryOnTestTags.PICK_GARMENT_TOP_BACK_INLINE
                        else -> null
                    }
                    val frontPreviewTag = when {
                        isPrimaryBottom -> TryOnTestTags.PREVIEW_GARMENT_BOTTOM_FRONT
                        isPrimaryTop -> TryOnTestTags.PREVIEW_GARMENT_TOP_FRONT
                        else -> null
                    }
                    val backPreviewTag = when {
                        isPrimaryBottom -> TryOnTestTags.PREVIEW_GARMENT_BOTTOM_BACK
                        isPrimaryTop -> TryOnTestTags.PREVIEW_GARMENT_TOP_BACK
                        else -> null
                    }
                    GarmentThumbnailCard(
                        item = item,
                        imagePair = pair,
                        requestedViews = state.requestedViews,
                        selected = selected,
                        enabled = !state.isJobActive && !state.isSubmitting,
                        onClick = { onToggleItem(item.itemId) },
                        onPickFrontPhoto = onPickGarmentPhoto?.let { picker ->
                            { picker(item.itemId, ImageView.FRONT) }
                        },
                        onPickBackPhoto = onPickGarmentPhoto?.let { picker ->
                            { picker(item.itemId, ImageView.BACK) }
                        },
                        frontPickButtonTag = frontPickTag,
                        backPickButtonTag = backPickTag,
                        frontPreviewTag = frontPreviewTag,
                        backPreviewTag = backPreviewTag,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Priority 3: Chọn áo / quần / cả hai
        if (state.canChooseGarmentCombos) {
            Text(
                text = "Tổ hợp thay đồ",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = state.activeGarmentCombo == TryOnGarmentCombo.TOP_ONLY,
                    onClick = { onSelectGarmentCombo(TryOnGarmentCombo.TOP_ONLY) },
                    enabled = !state.isJobActive && !state.isSubmitting,
                    shape = PillChipShape,
                    colors = editorialFilterChipColors(),
                    label = {
                        Text(
                            text = "Chỉ thay Áo (TOP)",
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TryOnTestTags.GARMENT_COMBO_TOP),
                )
                FilterChip(
                    selected = state.activeGarmentCombo == TryOnGarmentCombo.BOTTOM_ONLY,
                    onClick = { onSelectGarmentCombo(TryOnGarmentCombo.BOTTOM_ONLY) },
                    enabled = !state.isJobActive && !state.isSubmitting,
                    shape = PillChipShape,
                    colors = editorialFilterChipColors(),
                    label = {
                        Text(
                            text = "Chỉ thay Quần (BOTTOM)",
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TryOnTestTags.GARMENT_COMBO_BOTTOM),
                )
                FilterChip(
                    selected = state.activeGarmentCombo == TryOnGarmentCombo.TOP_AND_BOTTOM,
                    onClick = { onSelectGarmentCombo(TryOnGarmentCombo.TOP_AND_BOTTOM) },
                    enabled = !state.isJobActive && !state.isSubmitting,
                    shape = PillChipShape,
                    colors = editorialFilterChipColors(),
                    label = {
                        Text(
                            text = "Cả Áo + Quần (TOP+BOTTOM)",
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TryOnTestTags.GARMENT_COMBO_TOP_BOTTOM),
                )
            }
        }

        if (state.isSubsetSimulation) {
            Surface(
                shape = NoticeCornerShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ) {
                Text(
                    text = "Đang áp dụng ${state.selectedItemIds.size}/${outfit.items.size} món (các món còn lại giữ nguyên trên ảnh gốc).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (state.hasExperimentalSelection) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TryOnTestTags.EXPERIMENTAL_NOTICE),
                shape = NoticeCornerShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            ) {
                Text(
                    text = "THỬ NGHIỆM (EXPERIMENTAL): Tổ hợp BACK / BOTTOM / TOP+BOTTOM hiện mới kiểm thử logic và mock, chưa xác minh chất lượng AI thực tế.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (state.excludedOutfitItems.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Giữ nguyên trên ảnh người:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.excludedOutfitItems.forEach { excluded ->
                val reason = if (cap != null && excluded.category !in cap.supportedCategories) {
                    "Danh mục ${categoryLabelVi(excluded.category)} chưa hỗ trợ"
                } else {
                    "Không chọn thay"
                }
                OutfitItemSwatchRow(item = excluded, statusLabel = reason)
            }
        }
    }
}

@Composable
private fun GarmentThumbnailCard(
    item: TryOnItemSummary,
    imagePair: ImagePair?,
    requestedViews: Set<ImageView>,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPickFrontPhoto: (() -> Unit)? = null,
    onPickBackPhoto: (() -> Unit)? = null,
    frontPickButtonTag: String? = null,
    backPickButtonTag: String? = null,
    frontPreviewTag: String? = null,
    backPreviewTag: String? = null,
) {
    val dotColor = parseSafeHex(item.accentHex) ?: MaterialTheme.colorScheme.secondary
    val borderStroke = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Surface(
        modifier = modifier
            .clip(ThumbnailCornerShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = ThumbnailCornerShape,
        color = containerColor,
        border = borderStroke,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = categoryLabelVi(item.category),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (imagePair?.front != null && imagePair.back != null) "Đủ 2 mặt" else "Cần cho góc chọn",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Đang chọn",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(16.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                GarmentReferenceSlotCard(
                    title = "Trước",
                    mediaRef = imagePair?.front,
                    missingLabel = "Chưa có ảnh trước",
                    caption = "${item.label} (Trước)",
                    isActiveView = selected && ImageView.FRONT in requestedViews,
                    buttonLabel = "Chọn Trước",
                    enabled = enabled,
                    onPickPhoto = onPickFrontPhoto,
                    pickButtonTag = frontPickButtonTag,
                    previewTag = frontPreviewTag,
                    modifier = Modifier.weight(1f),
                )
                GarmentReferenceSlotCard(
                    title = "Sau",
                    mediaRef = imagePair?.back,
                    missingLabel = "Chưa có ảnh sau",
                    caption = "${item.label} (Sau)",
                    isActiveView = selected && ImageView.BACK in requestedViews,
                    buttonLabel = "Chọn Sau",
                    enabled = enabled,
                    onPickPhoto = onPickBackPhoto,
                    pickButtonTag = backPickButtonTag,
                    previewTag = backPreviewTag,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GarmentReferenceSlotCard(
    title: String,
    mediaRef: MediaRef?,
    missingLabel: String,
    caption: String,
    isActiveView: Boolean,
    buttonLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPickPhoto: (() -> Unit)? = null,
    pickButtonTag: String? = null,
    previewTag: String? = null,
) {
    val borderColor = if (isActiveView && mediaRef != null) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = modifier
            .clip(ThumbnailCornerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, borderColor, ThumbnailCornerShape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .widthIn(max = 128.dp)
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .let { mod -> if (previewTag != null) mod.testTag(previewTag) else mod }
                .let { mod ->
                    if (onPickPhoto != null) mod.clickable(enabled = enabled, onClick = onPickPhoto) else mod
                },
            contentAlignment = Alignment.Center,
        ) {
            if (mediaRef == null) {
                Text(
                    text = missingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Spacing.xs),
                )
            } else {
                TryOnMediaPreviewSurface(
                    mediaRef = mediaRef,
                    caption = caption,
                    isFakeOutput = false,
                    compactThumbnail = true,
                )
            }
        }
        Text(
            text = if (mediaRef != null) "Đã sẵn sàng" else "Chưa có ảnh",
            style = MaterialTheme.typography.labelSmall,
            color = if (mediaRef != null) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
        if (onPickPhoto != null) {
            OutlinedButton(
                onClick = onPickPhoto,
                enabled = enabled,
                shape = PillChipShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                    .let { mod -> if (pickButtonTag != null) mod.testTag(pickButtonTag) else mod },
            ) {
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun OutfitItemSwatchRow(
    item: TryOnItemSummary,
    statusLabel: String,
) {
    val dotColor = parseSafeHex(item.accentHex) ?: MaterialTheme.colorScheme.secondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Priority 4 & 5: Chọn chế độ Original Photo / Studio White + Chọn góc nhìn Front / Back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TryOnConfigurationPanel(
    state: TryOnUiState,
    onSelectPresentationMode: (TryOnPresentationMode) -> Unit,
    onSelectViewMode: (TryOnViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    TryOnEditorialPanel(modifier = modifier) {
        Text(
            text = "CHẾ ĐỘ KHUNG HÌNH & GÓC NHÌN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )

        // Priority 4: Chọn chế độ Original Photo / Studio White
        if (state.canSelectStudioWhite || state.isFostaProvider) {
            Text(
                text = "Chế độ khung hình",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = state.selectedPresentationMode == TryOnPresentationMode.ORIGINAL_PHOTO,
                    onClick = { onSelectPresentationMode(TryOnPresentationMode.ORIGINAL_PHOTO) },
                    enabled = !state.isJobActive && !state.isSubmitting,
                    shape = PillChipShape,
                    colors = editorialFilterChipColors(),
                    label = {
                        Text(
                            text = "Ảnh gốc (ORIGINAL_PHOTO)",
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TryOnTestTags.PRESENTATION_ORIGINAL_PHOTO),
                )
                FilterChip(
                    selected = state.selectedPresentationMode == TryOnPresentationMode.STUDIO_WHITE,
                    onClick = { onSelectPresentationMode(TryOnPresentationMode.STUDIO_WHITE) },
                    enabled = state.canSelectStudioWhite && !state.isJobActive && !state.isSubmitting,
                    shape = PillChipShape,
                    colors = editorialFilterChipColors(),
                    label = {
                        Text(
                            text = "Nền trắng Studio (STUDIO_WHITE)",
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TryOnTestTags.PRESENTATION_STUDIO_WHITE),
                )
            }
            if (state.selectedPresentationMode == TryOnPresentationMode.STUDIO_WHITE) {
                Text(
                    text = "Lưu ý Studio: AI dựng lại tư thế đứng thẳng toàn thân trên nền trắng sáng đều (không dùng để đo kích cỡ).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // Priority 5: Chọn Front / Back
        Text(
            text = "Góc nhìn mô phỏng",
            style = MaterialTheme.typography.labelLarge,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FilterChip(
                selected = state.selectedViewMode == TryOnViewMode.FRONT_ONLY,
                onClick = { onSelectViewMode(TryOnViewMode.FRONT_ONLY) },
                enabled = !state.isJobActive && !state.isSubmitting,
                shape = PillChipShape,
                colors = editorialFilterChipColors(),
                label = { Text("Chỉ mặt trước", modifier = Modifier.padding(vertical = 4.dp)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(TryOnTestTags.MODE_FRONT_ONLY),
            )
            FilterChip(
                selected = state.selectedViewMode == TryOnViewMode.BACK_ONLY,
                onClick = { onSelectViewMode(TryOnViewMode.BACK_ONLY) },
                enabled = state.canSelectBackView && !state.isJobActive && !state.isSubmitting,
                shape = PillChipShape,
                colors = editorialFilterChipColors(),
                label = { Text("Chỉ mặt sau", modifier = Modifier.padding(vertical = 4.dp)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(TryOnTestTags.MODE_BACK_ONLY),
            )
            if (!state.isFostaProvider) {
                FilterChip(
                    selected = state.selectedViewMode == TryOnViewMode.FRONT_AND_BACK,
                    onClick = { onSelectViewMode(TryOnViewMode.FRONT_AND_BACK) },
                    enabled = state.canSelectDualView && !state.isJobActive && !state.isSubmitting,
                    shape = PillChipShape,
                    colors = editorialFilterChipColors(),
                    label = { Text("Mặt trước & Mặt sau", modifier = Modifier.padding(vertical = 4.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TryOnTestTags.MODE_DUAL_VIEW),
                )
            }
        }

        state.backViewUnavailableReason?.let { reason ->
            val selectedSet = state.selectedItemIds.toSet()
            val missingBackTargets = buildList {
                if (!state.hasPersonBack) {
                    add("Ảnh người dùng (bấm 'Chọn Sau' ở mục Ảnh người dùng)")
                }
                state.supportedOutfitItems
                    .filter { it.itemId in selectedSet && state.garmentImages[it.itemId]?.back == null }
                    .forEach { item ->
                        add("${categoryLabelVi(item.category)}: ${item.label} (bấm 'Chọn Sau' trên thẻ trang phục)")
                    }
            }
            val hintText = if (missingBackTargets.isNotEmpty()) {
                "Lưu ý mặt sau: $reason Cần chọn thêm cho: ${missingBackTargets.joinToString("; ")}. Bạn vẫn có thể thử riêng mặt trước."
            } else {
                "Lưu ý mặt sau: $reason Bạn vẫn có thể thử riêng mặt trước."
            }
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Priority 6 & 7: Xác nhận quyền xử lý ảnh + Nút tạo ảnh.
 */
@Composable
private fun PrivacyAndConsentSection(
    state: TryOnUiState,
    onConsentChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewCount = state.requestedViews.size
    val garmentPhotoCount = state.selectedItemIds.size * viewCount

    TryOnEditorialPanel(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Text(
            text = "XÁC NHẬN QUYỀN XỬ LÝ ẢNH",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = "Chỉ gửi $viewCount ảnh toàn thân và $garmentPhotoCount ảnh trang phục đã chọn cho lượt thử này. ${TryOnPrivacyDisclosure.retentionNoticeForProvider(state.capability?.providerId)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(NoticeCornerShape)
                .toggleable(
                    value = state.consentConfirmed,
                    enabled = !state.isSubmitting && !state.isJobActive,
                    role = Role.Checkbox,
                    onValueChange = onConsentChanged,
                )
                .padding(vertical = Spacing.xs)
                .testTag(TryOnTestTags.CONSENT_CHECKBOX_ROW),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Checkbox(
                checked = state.consentConfirmed,
                onCheckedChange = null, // Handled by parent toggleable row for >=48dp target
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            Text(
                text = "Tôi đồng ý gửi những ảnh đã chọn cho riêng lượt thử đồ 2D này.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = onSubmit,
            enabled = state.canSubmitTryOn,
            shape = ActionButtonCornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag(TryOnTestTags.SUBMIT_BUTTON),
        ) {
            Text(
                text = when (state.selectedViewMode) {
                    TryOnViewMode.FRONT_AND_BACK -> "Tạo ảnh thử đồ (Trước & Sau)"
                    TryOnViewMode.BACK_ONLY -> "Tạo ảnh thử đồ (Mặt sau)"
                    TryOnViewMode.FRONT_ONLY -> "Tạo ảnh thử đồ (Mặt trước)"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun IdleLookbookStagePlaceholder(
    state: TryOnUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(PhotoFrameCornerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PhotoFrameCornerShape)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Text(
            text = "KHUNG KẾT QUẢ 3:4",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = "Sẵn sàng thử đồ 2D",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (state.consentConfirmed) {
                "Nhấn \"Tạo ảnh thử đồ\" ở cột bên trái để bắt đầu."
            } else {
                "Chọn trang phục và xác nhận quyền xử lý ảnh ở cột bên trái trước khi tạo ảnh."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProcessingStageSection(
    result: TryOnResult,
    onCancelJob: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stageTitle = when (result.status) {
        TryOnJobStatus.QUEUED -> "Đang chuẩn bị ảnh..."
        else -> "Đang tạo ảnh thử đồ 2D..."
    }

    TryOnEditorialPanel(
        modifier = modifier.widthIn(max = 420.dp),
    ) {
        Text(
            text = "TIẾN TRÌNH XỬ LÝ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = stageTitle,
            style = MaterialTheme.typography.titleLarge,
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            result.views.forEach { (view, vr) ->
                val label = if (view == ImageView.FRONT) "Góc nhìn mặt trước" else "Góc nhìn mặt sau"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = viewStateLabelVi(vr.state),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        Text(
            text = "Quá trình thường mất 8–20 giây. Bạn có thể hủy nếu không muốn tiếp tục chờ.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onCancelJob,
            shape = ActionButtonCornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(TryOnTestTags.CANCEL_BUTTON),
        ) {
            Text("Hủy yêu cầu thử đồ")
        }
    }
}

/**
 * Priority 8: Kết quả Virtual Try-On (Large 3:4 ContentScale.Fit portrait + Compare Original + Retry + Delete).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultShowcaseSection(
    state: TryOnUiState,
    result: TryOnResult,
    onSelectResultTab: (ImageView) -> Unit,
    onToggleCompareOriginal: (Boolean) -> Unit,
    onDeleteResult: () -> Unit,
    onPrepareFreshAttempt: () -> Unit,
    onSelectDifferentOutfit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTab = if (state.activeResultTab in result.views.keys) {
        state.activeResultTab
    } else {
        result.views.keys.firstOrNull() ?: state.activeResultTab
    }
    val activeViewResult = result.views[activeTab] ?: result.views.values.firstOrNull()
    val canCompareOriginal = activeViewResult?.state == ViewProcessState.SUCCEEDED &&
        activeViewResult.qualityReview != QualityReviewState.REJECTED &&
        state.personProfile?.bodyImages?.refFor(activeTab) != null

    TryOnEditorialPanel(
        modifier = modifier
            .widthIn(max = 440.dp)
            .testTag(TryOnTestTags.RESULT_SECTION),
    ) {
        ResultStatusHeaderBadge(result = result)

        // Front / Back view tabs + Compare Original toggle
        if (result.views.size > 1 || canCompareOriginal) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (result.views.containsKey(ImageView.FRONT) && result.views.size > 1) {
                    val frontVr = result.views[ImageView.FRONT]
                    FilterChip(
                        selected = activeTab == ImageView.FRONT,
                        onClick = { onSelectResultTab(ImageView.FRONT) },
                        shape = PillChipShape,
                        colors = editorialFilterChipColors(),
                        label = {
                            Text(
                                text = "Mặt trước (${shortViewBadgeVi(frontVr)})",
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(TryOnTestTags.TAB_FRONT),
                    )
                }
                if (result.views.containsKey(ImageView.BACK) && result.views.size > 1) {
                    val backVr = result.views[ImageView.BACK]
                    FilterChip(
                        selected = activeTab == ImageView.BACK,
                        onClick = { onSelectResultTab(ImageView.BACK) },
                        shape = PillChipShape,
                        colors = editorialFilterChipColors(),
                        label = {
                            Text(
                                text = "Mặt sau (${shortViewBadgeVi(backVr)})",
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(TryOnTestTags.TAB_BACK),
                    )
                }
                if (canCompareOriginal) {
                    FilterChip(
                        selected = state.compareWithOriginal,
                        onClick = { onToggleCompareOriginal(!state.compareWithOriginal) },
                        shape = PillChipShape,
                        colors = editorialFilterChipColors(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = {
                            Text(
                                text = if (state.compareWithOriginal) "Đang xem ảnh gốc" else "So sánh ảnh gốc",
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(TryOnTestTags.COMPARE_ORIGINAL_TOGGLE),
                    )
                }
            }
        }

        // Large 3:4 Editorial Portrait Frame (ContentScale.Fit so head & feet are never cropped)
        Box(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .aspectRatio(3f / 4f)
                .clip(PhotoFrameCornerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PhotoFrameCornerShape),
            contentAlignment = Alignment.Center,
        ) {
            RenderResultCanvasContent(
                state = state,
                result = result,
                activeView = activeTab,
                viewResult = activeViewResult,
            )
        }

        if (result.views.size > 1 && state.capability?.supportsNativePairedViews == false) {
            Text(
                text = "Lưu ý: Mặt trước và mặt sau được tạo độc lập.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (result.unappliedItemIds.isNotEmpty()) {
            val excludedNames = state.outfitSnapshot?.items
                ?.filter { it.itemId in result.unappliedItemIds }
                ?.joinToString(", ") { it.label }
                .orEmpty()
            if (excludedNames.isNotEmpty()) {
                Text(
                    text = "Giữ nguyên từ ảnh gốc: $excludedNames.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Mandatory Product Honesty Disclaimer
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TryOnTestTags.DISCLAIMER_TEXT),
            shape = NoticeCornerShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        ) {
            Text(
                text = TryOnPrivacyDisclosure.PRODUCT_LIMITATION_DISCLAIMER,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }

        // Secondary & Recovery Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Button(
                onClick = onPrepareFreshAttempt,
                shape = ActionButtonCornerShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text("Thử lại / Tạo lượt mới")
            }

            OutlinedButton(
                onClick = onDeleteResult,
                shape = ActionButtonCornerShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(TryOnTestTags.DELETE_RESULT_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text("Xóa kết quả & ảnh tạm")
            }

            TextButton(
                onClick = onSelectDifferentOutfit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Chọn bộ trang phục khác")
            }
        }
    }
}

@Composable
private fun ResultStatusHeaderBadge(result: TryOnResult) {
    val (title, subtitle, accent, tag) = when (result.status) {
        TryOnJobStatus.SUCCEEDED -> {
            val label = when {
                result.views.size > 1 -> "KẾT QUẢ THỬ ĐỒ (2 GÓC NHÌN)"
                result.views.keys == setOf(ImageView.BACK) -> "KẾT QUẢ THỬ ĐỒ (MẶT SAU)"
                else -> "KẾT QUẢ THỬ ĐỒ (MẶT TRƯỚC)"
            }
            Quad(
                label,
                "Nhấn \"So sánh ảnh gốc\" để đối chiếu trước và sau khi thay đồ.",
                MaterialTheme.colorScheme.tertiary,
                null,
            )
        }

        TryOnJobStatus.PARTIAL -> Quad(
            "Hoàn tất một phần (1/${result.views.size} góc nhìn)",
            "Một góc nhìn không thể tạo thành công. Ứng dụng giữ nguyên trạng thái lỗi của góc nhìn đó, không tự chèn ảnh giả.",
            MaterialTheme.colorScheme.secondary,
            TryOnTestTags.PARTIAL_BADGE,
        )

        TryOnJobStatus.TIMED_OUT -> Quad(
            "Quá thời gian chờ xử lý",
            "Yêu cầu vượt quá thời hạn xử lý. Bạn có thể thử lại khi sẵn sàng.",
            MaterialTheme.colorScheme.error,
            null,
        )

        TryOnJobStatus.UNKNOWN_SUBMISSION -> Quad(
            "Chưa xác nhận được trạng thái gửi",
            "Kết nối bị ngắt trước khi máy chủ trả mã tiến trình. Ứng dụng không tự gửi lại.",
            MaterialTheme.colorScheme.error,
            null,
        )

        TryOnJobStatus.CANCELLED -> Quad(
            "Đã hủy lượt thử đồ",
            "Tiến trình đã dừng theo yêu cầu của bạn.",
            MaterialTheme.colorScheme.onSurfaceVariant,
            null,
        )

        else -> Quad(
            "Không thể hoàn tất mô phỏng",
            result.views.values.firstNotNullOfOrNull { it.error?.message }
                ?: "Đã xảy ra lỗi khi xử lý bộ ảnh này.",
            MaterialTheme.colorScheme.error,
            null,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (tag != null) mod.testTag(tag) else mod },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = if (result.status == TryOnJobStatus.SUCCEEDED) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.Info
                },
                contentDescription = null,
                tint = accent,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RenderResultCanvasContent(
    state: TryOnUiState,
    result: TryOnResult,
    activeView: ImageView,
    viewResult: ViewResult?,
) {
    when {
        viewResult == null -> {
            CanvasMessageState(
                title = "Chưa có dữ liệu cho góc nhìn này",
                body = "Góc nhìn chưa được yêu cầu trong lượt thử đồ.",
            )
        }

        viewResult.state == ViewProcessState.SUCCEEDED &&
            viewResult.qualityReview == QualityReviewState.REJECTED -> {
            CanvasMessageState(
                title = "Đã ẩn ảnh không đạt chất lượng",
                body = "Ảnh mô phỏng của góc nhìn này có sai lệch hình thể hoặc trang phục nghiêm trọng nên đã bị chặn hiển thị.",
                isError = true,
            )
        }

        viewResult.state == ViewProcessState.SUCCEEDED && viewResult.outputRef != null -> {
            val targetRef = if (state.compareWithOriginal) {
                state.personProfile?.bodyImages?.refFor(activeView) ?: viewResult.outputRef
            } else {
                viewResult.outputRef
            }
            val viewTitle = if (activeView == ImageView.FRONT) "Mặt trước" else "Mặt sau"
            val modeSuffix = if (!state.compareWithOriginal && result.presentationMode == TryOnPresentationMode.STUDIO_WHITE) {
                " • Studio White"
            } else {
                ""
            }
            val liveLabel = if (result.providerId == "fosta" || state.isFostaProvider) {
                "AI LIVE — FOSTA"
            } else {
                "AI LIVE (OpenAI)"
            }
            TryOnMediaPreviewSurface(
                mediaRef = targetRef,
                caption = if (state.compareWithOriginal) {
                    "Ảnh gốc ($viewTitle)"
                } else {
                    "Thử đồ 2D ($viewTitle$modeSuffix)"
                },
                isFakeOutput = !state.compareWithOriginal && result.source == TryOnSource.FAKE,
                isLiveAiOutput = !state.compareWithOriginal && result.source == TryOnSource.AI_LIVE,
                liveProviderBadgeLabel = liveLabel,
                showBottomCaptionOnDiskImage = true,
            )
        }

        viewResult.state == ViewProcessState.EXPIRED -> {
            CanvasMessageState(
                title = "Ảnh kết quả tạm thời đã hết hạn",
                body = "Ảnh này không còn lưu trên máy chủ. Hãy tạo lại lượt thử mới nếu cần.",
            )
        }

        else -> {
            CanvasMessageState(
                title = if (activeView == ImageView.BACK) {
                    "Góc nhìn mặt sau không thành công"
                } else {
                    "Góc nhìn mặt trước không thành công"
                },
                body = viewResult.error?.message
                    ?: "Không thể tạo ảnh hợp lệ cho góc nhìn này. Không hiển thị ảnh thay thế giả.",
                isError = true,
            )
        }
    }
}

@Composable
private fun CanvasMessageState(
    title: String,
    body: String,
    isError: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Renders private file-backed media via Coil `AsyncImage`, or renders a non-deceptive
 * editorial vector lookbook diagram when `localRelativePath` starts with `seed://` (FAKE/DEV fixtures).
 */
@Composable
private fun TryOnMediaPreviewSurface(
    mediaRef: MediaRef,
    caption: String,
    isFakeOutput: Boolean,
    isLiveAiOutput: Boolean = false,
    liveProviderBadgeLabel: String = "AI LIVE (OpenAI)",
    showBottomCaptionOnDiskImage: Boolean = false,
    compactThumbnail: Boolean = false,
) {
    val diskFile = if (!mediaRef.localRelativePath.startsWith("seed://")) {
        File(mediaRef.localRelativePath)
    } else {
        null
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (diskFile == null || !diskFile.exists()) {
            val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
            val garmentTint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(
                    color = outlineColor,
                    radius = w * 0.08f,
                    center = Offset(w * 0.5f, h * 0.18f),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawRoundRect(
                    color = garmentTint,
                    topLeft = Offset(w * 0.28f, h * 0.28f),
                    size = Size(w * 0.44f, h * 0.30f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(w * 0.28f, h * 0.28f),
                    size = Size(w * 0.44f, h * 0.30f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(w * 0.32f, h * 0.61f),
                    size = Size(w * 0.36f, h * 0.26f),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            if (!compactThumbnail) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .padding(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (isFakeOutput) {
                        Text(
                            text = "FAKE • CHƯA QUA MÔ HÌNH AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            val context = LocalContext.current
            // Include MediaRef identity/hash/revision because gallery picks overwrite fixed slot paths in place.
            val imageRequest = remember(
                context,
                mediaRef.id,
                mediaRef.contentHash,
                mediaRef.revision,
                mediaRef.localRelativePath,
            ) {
                val cacheKey = "${mediaRef.id}:${mediaRef.contentHash}:${mediaRef.revision}"
                ImageRequest.Builder(context)
                    .data(diskFile)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (!compactThumbnail) {
                if (isFakeOutput) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Spacing.sm),
                        shape = PillChipShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    ) {
                        Text(
                            text = "FAKE • OFFLINE MOCK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else if (isLiveAiOutput) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Spacing.sm),
                        shape = PillChipShape,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.94f),
                    ) {
                        Text(
                            text = liveProviderBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (isFakeOutput || showBottomCaptionOnDiskImage) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                            .padding(vertical = 6.dp, horizontal = Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBannerBlock(
    message: String,
    isStale: Boolean,
    onSelectDifferentOutfit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NoticeCornerShape,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (isStale) {
                OutlinedButton(
                    onClick = onSelectDifferentOutfit,
                    shape = ActionButtonCornerShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Quay lại chọn lại trang phục mới")
                }
            }
        }
    }
}

@Composable
private fun StatusNoticeBlock(
    notice: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NoticeCornerShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = notice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun editorialFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = editorialPanelContainerColor(),
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private fun categoryLabelVi(category: Category): String = when (category) {
    Category.TOP -> "Áo"
    Category.BOTTOM -> "Quần/Chân váy"
    Category.DRESS -> "Đầm liền"
    Category.OUTERWEAR -> "Áo khoác"
    Category.SHOES -> "Giày"
    Category.ACCESSORY -> "Phụ kiện"
}

private fun viewStateLabelVi(state: ViewProcessState): String = when (state) {
    ViewProcessState.NOT_REQUESTED -> "Không yêu cầu"
    ViewProcessState.QUEUED -> "Đang chờ"
    ViewProcessState.RUNNING -> "Đang xử lý"
    ViewProcessState.SUCCEEDED -> "Hoàn tất"
    ViewProcessState.FAILED -> "Thất bại"
    ViewProcessState.EXPIRED -> "Hết hạn"
    ViewProcessState.CANCELLED -> "Đã hủy"
}

private fun shortViewBadgeVi(vr: ViewResult?): String = when (vr?.state) {
    ViewProcessState.SUCCEEDED -> if (vr.qualityReview == QualityReviewState.REJECTED) "Đã ẩn" else "Xong"
    ViewProcessState.FAILED -> "Lỗi"
    ViewProcessState.EXPIRED -> "Hết hạn"
    ViewProcessState.CANCELLED -> "Đã hủy"
    else -> "Đang xử lý"
}

private fun parseSafeHex(hex: String?): Color? {
    if (hex == null || !hex.startsWith("#") || hex.length != 7) return null
    val rgb = hex.drop(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}
