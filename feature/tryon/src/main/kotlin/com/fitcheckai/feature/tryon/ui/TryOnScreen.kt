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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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

// Track A-aligned shape tokens used locally
private val PhotoFrameCornerShape = RoundedCornerShape(20.dp)
private val NoticeCornerShape = RoundedCornerShape(14.dp)

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
    TryOnEditorialTheme {
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
                        TryOnTopBarSection(
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
        verticalArrangement = Arrangement.spacedBy(TryOnScreenHorizontalPadding),
    ) {
        TryOnTopBarSection(
            isFakeEnvironment = state.isFakeEnvironment && state.isCapabilityAvailable,
            isFostaLiveEnvironment = !state.isFakeEnvironment && state.isFostaProvider && state.isCapabilityAvailable,
            onNavigateBack = onNavigateBack,
        )

        state.bannerError?.let { error ->
            ErrorBannerBlock(
                message = error.message,
                isStale = error.code == TryOnErrorCode.STALE_INPUT,
                onSelectDifferentOutfit = onSelectDifferentOutfit,
                modifier = Modifier.padding(horizontal = TryOnScreenHorizontalPadding),
            )
        }

        state.statusNotice?.let { notice ->
            StatusNoticeBlock(
                notice = notice,
                modifier = Modifier.padding(horizontal = TryOnScreenHorizontalPadding),
            )
        }

        if (!state.isCapabilityAvailable) {
            CapabilityUnavailableSection(
                state = state,
                onSelectDifferentOutfit = onSelectDifferentOutfit,
                onRefreshConnection = onRefreshConnection,
                modifier = Modifier.padding(horizontal = TryOnScreenHorizontalPadding),
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
                            .padding(horizontal = TryOnScreenHorizontalPadding),
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
                            .padding(horizontal = TryOnScreenHorizontalPadding),
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
                    modifier = Modifier.padding(horizontal = TryOnScreenHorizontalPadding),
                )
            }

            SelectedGarmentsPanel(
                state = state,
                onSelectGarmentCombo = onSelectGarmentCombo,
                onToggleItem = onToggleItem,
                onPickGarmentPhoto = onPickGarmentPhoto,
                modifier = Modifier.padding(horizontal = TryOnScreenHorizontalPadding),
            )

            if (currentResult == null) {
                TryOnConfigurationPanel(
                    state = state,
                    onSelectPresentationMode = onSelectPresentationMode,
                    onSelectViewMode = onSelectViewMode,
                    modifier = Modifier.padding(horizontal = TryOnScreenHorizontalPadding),
                )

                PrivacyAndConsentSection(
                    state = state,
                    onConsentChanged = onConsentChanged,
                    onSubmit = onSubmit,
                    modifier = Modifier.padding(horizontal = TryOnScreenHorizontalPadding),
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
        TryOnTopBarSection(
            isFakeEnvironment = state.isFakeEnvironment && state.isCapabilityAvailable,
            isFostaLiveEnvironment = !state.isFakeEnvironment && state.isFostaProvider && state.isCapabilityAvailable,
            onNavigateBack = onNavigateBack,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TryOnScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Left Pane: Image-First Inputs (1. Person -> 2-3. Garments & Combo -> 4-5. Mode & View -> 6-7. Consent & Submit)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = TryOnScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(TryOnScreenHorizontalPadding),
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
                    .padding(vertical = TryOnScreenHorizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TryOnScreenHorizontalPadding),
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
private fun TryOnTopBarSection(
    isFakeEnvironment: Boolean,
    isFostaLiveEnvironment: Boolean = false,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TryOnBackTopBar(
            title = "Thử đồ",
            onBack = onNavigateBack,
        )
        if (isFakeEnvironment) {
            TryOnInfoNotice(
                text = "Chế độ kiểm thử · Kết quả giả lập, không gọi AI thực tế.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TryOnScreenHorizontalPadding)
                    .testTag(TryOnTestTags.FAKE_MODE_BADGE),
            )
        } else if (isFostaLiveEnvironment) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TryOnScreenHorizontalPadding, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Text(
                    text = "Đang kết nối dịch vụ thử đồ trực tiếp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val decisionTitle = when (state.capability?.decision) {
        FeasibilityDecision.NO_GO -> "Tính năng thử đồ đang tạm khóa"
        else -> "Dịch vụ thử đồ chưa sẵn sàng"
    }
    val reasonText = sanitizeUserFacingText(
        state.capability?.lockedReason?.takeIf { it.isNotBlank() }
            ?: "Mô phỏng ảnh người thật 2D chỉ mở khi dịch vụ xử lý ảnh sẵn sàng. Ứng dụng không hiển thị ảnh giả lập như kết quả AI thật.",
    )

    TryOnPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TryOnTestTags.CAPABILITY_UNAVAILABLE_CARD),
    ) {
        TryOnSectionHeading(
            title = decisionTitle,
            subtitle = "Bạn vẫn có thể xem lại các món trong bộ phối này.",
        )

        TryOnInfoNotice(
            text = reasonText,
            icon = Icons.Outlined.Lock,
            modifier = Modifier.fillMaxWidth(),
        )

        val outfit = state.outfitSnapshot
        if (outfit != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Bộ trang phục đang chọn",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (outfit.explanation.isNotBlank()) {
                Text(
                    text = outfit.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                outfit.items.forEach { item ->
                    OutfitItemSwatchRow(item = item, statusLabel = categoryLabelVi(item.category))
                }
            }
        }

        if (onRefreshConnection != null) {
            TryOnPrimaryAction(
                text = "Thử kết nối lại",
                onClick = onRefreshConnection,
                icon = Icons.Outlined.Refresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TryOnTestTags.REFRESH_CONNECTION_BUTTON),
            )
        }

        OutlinedButton(
            onClick = onSelectDifferentOutfit,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Quay lại danh sách gợi ý Hôm nay")
        }
    }
}

/**
 * Priority 1: Ảnh người dùng (Mặt trước | Mặt sau theo bố cục Track A).
 */
@Composable
private fun PersonPhotosPanel(
    state: TryOnUiState,
    onNavigateToProfilePhotos: () -> Unit,
    modifier: Modifier = Modifier,
    onPickPersonPhoto: ((ImageView) -> Unit)? = null,
) {
    TryOnPanel(modifier = modifier.fillMaxWidth()) {
        TryOnSectionHeading(
            title = "Ảnh người dùng",
            subtitle = "Chọn ảnh toàn thân rõ dáng để thử đồ tự nhiên hơn.",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TryOnPhotoSlotColumn(
                title = "Mặt trước",
                mediaRef = state.personProfile?.bodyImages?.front,
                missingText = "Chưa có ảnh",
                caption = "Mặt trước",
                placeholderIcon = Icons.Outlined.Person,
                enabled = !state.isJobActive && !state.isSubmitting,
                onPickPhoto = onPickPersonPhoto?.let { picker -> { picker(ImageView.FRONT) } },
                pickButtonTag = TryOnTestTags.PICK_PERSON_FRONT_INLINE,
                previewTag = TryOnTestTags.PREVIEW_PERSON_FRONT,
                modifier = Modifier.weight(1f),
            )
            TryOnPhotoSlotColumn(
                title = "Mặt sau",
                mediaRef = state.personProfile?.bodyImages?.back,
                missingText = "Chưa có ảnh",
                caption = "Mặt sau",
                placeholderIcon = Icons.Outlined.Person,
                enabled = !state.isJobActive && !state.isSubmitting,
                onPickPhoto = onPickPersonPhoto?.let { picker -> { picker(ImageView.BACK) } },
                pickButtonTag = TryOnTestTags.PICK_PERSON_BACK_INLINE,
                previewTag = TryOnTestTags.PREVIEW_PERSON_BACK,
                modifier = Modifier.weight(1f),
            )
        }

        if (!state.hasPersonFront || !state.hasPersonBack) {
            OutlinedButton(
                onClick = onNavigateToProfilePhotos,
                shape = RoundedCornerShape(50),
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
                        "Thêm ảnh mặt trước từ Hồ sơ"
                    } else {
                        "Bổ sung ảnh mặt sau từ Hồ sơ"
                    },
                )
            }
        }
    }
}

@Composable
private fun TryOnPhotoSlotColumn(
    title: String,
    mediaRef: MediaRef?,
    missingText: String?,
    caption: String,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPickPhoto: (() -> Unit)? = null,
    pickButtonTag: String? = null,
    previewTag: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TryOnPhotoFrame(
            mediaRef = mediaRef,
            contentDescription = caption,
            placeholderIcon = placeholderIcon,
            missingText = missingText,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .let { mod -> if (previewTag != null) mod.testTag(previewTag) else mod }
                .let { mod ->
                    if (onPickPhoto != null) mod.clickable(enabled = enabled, onClick = onPickPhoto) else mod
                },
        )
        if (onPickPhoto != null) {
            OutlinedButton(
                onClick = onPickPhoto,
                enabled = enabled,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 10.dp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .let { mod -> if (pickButtonTag != null) mod.testTag(pickButtonTag) else mod },
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (mediaRef == null) "Chọn ảnh" else "Thay ảnh",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Priority 2 & 3: Ảnh trang phục đã chọn (1 TryOnPanel mỗi món -> 2 TryOnPhotoFrame) + Tổ hợp thay đồ.
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TryOnSectionHeading(
            title = "Trang phục đã chọn",
            subtitle = outfit.explanation.takeIf { it.isNotBlank() },
        )

        // Visual Garment Cards (Priority 2) — max 2 layers: TryOnPanel -> 2 borderless TryOnPhotoFrames
        if (state.supportedOutfitItems.isNotEmpty()) {
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

        // Priority 3: Chọn áo / quần / cả hai
        if (
            state.canChooseGarmentCombos ||
            state.isSubsetSimulation ||
            state.hasExperimentalSelection ||
            state.excludedOutfitItems.isNotEmpty()
        ) {
            TryOnPanel(modifier = Modifier.fillMaxWidth()) {
                if (state.canChooseGarmentCombos) {
                    TryOnSectionHeading(
                        title = "Phối món muốn thử",
                        subtitle = "Chọn thử riêng từng món hoặc phối cả bộ.",
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        TryOnChoiceChip(
                            label = "Chỉ áo",
                            selected = state.activeGarmentCombo == TryOnGarmentCombo.TOP_ONLY,
                            onClick = { onSelectGarmentCombo(TryOnGarmentCombo.TOP_ONLY) },
                            enabled = !state.isJobActive && !state.isSubmitting,
                            modifier = Modifier.testTag(TryOnTestTags.GARMENT_COMBO_TOP),
                        )
                        TryOnChoiceChip(
                            label = "Chỉ quần",
                            selected = state.activeGarmentCombo == TryOnGarmentCombo.BOTTOM_ONLY,
                            onClick = { onSelectGarmentCombo(TryOnGarmentCombo.BOTTOM_ONLY) },
                            enabled = !state.isJobActive && !state.isSubmitting,
                            modifier = Modifier.testTag(TryOnTestTags.GARMENT_COMBO_BOTTOM),
                        )
                        TryOnChoiceChip(
                            label = "Cả áo và quần",
                            selected = state.activeGarmentCombo == TryOnGarmentCombo.TOP_AND_BOTTOM,
                            onClick = { onSelectGarmentCombo(TryOnGarmentCombo.TOP_AND_BOTTOM) },
                            enabled = !state.isJobActive && !state.isSubmitting,
                            modifier = Modifier.testTag(TryOnTestTags.GARMENT_COMBO_TOP_BOTTOM),
                        )
                    }
                } else {
                    TryOnSectionHeading(title = "Trang phục áp dụng")
                }

                if (state.isSubsetSimulation) {
                    TryOnInfoNotice(
                        text = "Đang thử ${state.selectedItemIds.size}/${outfit.items.size} món, các món còn lại giữ nguyên theo ảnh gốc.",
                        tone = TryOnNoticeTone.NEUTRAL,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.hasExperimentalSelection) {
                    TryOnInfoNotice(
                        text = "Chế độ thử nghiệm: Thử mặt sau hoặc thử quần đang hoàn thiện, kết quả có thể chưa ổn định.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TryOnTestTags.EXPERIMENTAL_NOTICE),
                    )
                }

                if (state.excludedOutfitItems.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Giữ nguyên trên ảnh gốc",
                        style = MaterialTheme.typography.titleSmall,
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
    }
}

@Composable
private fun GarmentThumbnailCard(
    item: TryOnItemSummary,
    imagePair: ImagePair?,
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
    val panelBorder = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
    }

    TryOnPanel(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick),
        border = panelBorder,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = categoryLabelVi(item.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Đang chọn",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TryOnPhotoSlotColumn(
                title = "Mặt trước",
                mediaRef = imagePair?.front,
                missingText = "Chưa có ảnh",
                caption = "${item.label} (Trước)",
                placeholderIcon = Icons.Outlined.Checkroom,
                enabled = enabled,
                onPickPhoto = onPickFrontPhoto,
                pickButtonTag = frontPickButtonTag,
                previewTag = frontPreviewTag,
                modifier = Modifier.weight(1f),
            )
            TryOnPhotoSlotColumn(
                title = "Mặt sau",
                mediaRef = imagePair?.back,
                missingText = "Chưa có ảnh",
                caption = "${item.label} (Sau)",
                placeholderIcon = Icons.Outlined.Checkroom,
                enabled = enabled,
                onPickPhoto = onPickBackPhoto,
                pickButtonTag = backPickButtonTag,
                previewTag = backPreviewTag,
                modifier = Modifier.weight(1f),
            )
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
 * Priority 4 & 5: Chế độ khung hình & góc nhìn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TryOnConfigurationPanel(
    state: TryOnUiState,
    onSelectPresentationMode: (TryOnPresentationMode) -> Unit,
    onSelectViewMode: (TryOnViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    TryOnPanel(modifier = modifier.fillMaxWidth()) {
        TryOnSectionHeading(
            title = "Chế độ khung hình & góc nhìn",
            subtitle = "Chọn phông nền và hướng nhìn cho ảnh kết quả.",
        )

        if (state.canSelectStudioWhite || state.isFostaProvider) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Khung hình",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    TryOnChoiceChip(
                        label = "Ảnh gốc",
                        selected = state.selectedPresentationMode == TryOnPresentationMode.ORIGINAL_PHOTO,
                        onClick = { onSelectPresentationMode(TryOnPresentationMode.ORIGINAL_PHOTO) },
                        enabled = !state.isJobActive && !state.isSubmitting,
                        modifier = Modifier.testTag(TryOnTestTags.PRESENTATION_ORIGINAL_PHOTO),
                    )
                    TryOnChoiceChip(
                        label = "Nền trắng studio",
                        selected = state.selectedPresentationMode == TryOnPresentationMode.STUDIO_WHITE,
                        onClick = { onSelectPresentationMode(TryOnPresentationMode.STUDIO_WHITE) },
                        enabled = state.canSelectStudioWhite && !state.isJobActive && !state.isSubmitting,
                        modifier = Modifier.testTag(TryOnTestTags.PRESENTATION_STUDIO_WHITE),
                    )
                }
                if (state.selectedPresentationMode == TryOnPresentationMode.STUDIO_WHITE) {
                    TryOnInfoNotice(
                        text = "AI dựng lại dáng đứng thẳng trên nền trắng sáng đều để dễ xem phom phối đồ.",
                        tone = TryOnNoticeTone.NEUTRAL,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Góc nhìn",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TryOnChoiceChip(
                    label = "Mặt trước",
                    selected = state.selectedViewMode == TryOnViewMode.FRONT_ONLY,
                    onClick = { onSelectViewMode(TryOnViewMode.FRONT_ONLY) },
                    enabled = !state.isJobActive && !state.isSubmitting,
                    modifier = Modifier.testTag(TryOnTestTags.MODE_FRONT_ONLY),
                )
                TryOnChoiceChip(
                    label = "Chỉ mặt sau",
                    selected = state.selectedViewMode == TryOnViewMode.BACK_ONLY,
                    onClick = { onSelectViewMode(TryOnViewMode.BACK_ONLY) },
                    enabled = state.canSelectBackView && !state.isJobActive && !state.isSubmitting,
                    modifier = Modifier.testTag(TryOnTestTags.MODE_BACK_ONLY),
                )
                if (!state.isFostaProvider) {
                    TryOnChoiceChip(
                        label = "Trước và sau",
                        selected = state.selectedViewMode == TryOnViewMode.FRONT_AND_BACK,
                        onClick = { onSelectViewMode(TryOnViewMode.FRONT_AND_BACK) },
                        enabled = state.canSelectDualView && !state.isJobActive && !state.isSubmitting,
                        modifier = Modifier.testTag(TryOnTestTags.MODE_DUAL_VIEW),
                    )
                }
            }
        }

        state.backViewUnavailableReason?.let { reason ->
            val selectedSet = state.selectedItemIds.toSet()
            val missingBackTargets = buildList {
                if (!state.hasPersonBack) {
                    add("Ảnh người dùng (mặt sau)")
                }
                state.supportedOutfitItems
                    .filter { it.itemId in selectedSet && state.garmentImages[it.itemId]?.back == null }
                    .forEach { item ->
                        add("${categoryLabelVi(item.category)}: ${item.label} (mặt sau)")
                    }
            }
            val cleanReason = sanitizeUserFacingText(reason)
            val hintText = if (missingBackTargets.isNotEmpty()) {
                "$cleanReason Cần chọn thêm cho: ${missingBackTargets.joinToString("; ")}. Bạn vẫn có thể thử riêng mặt trước."
            } else {
                "$cleanReason Bạn vẫn có thể thử riêng mặt trước."
            }
            TryOnInfoNotice(
                text = hintText,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Priority 6 & 7: Đồng ý xử lý ảnh + Nút tạo ảnh thử đồ.
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
    val submitHint = remember(
        state.canSubmitTryOn,
        state.isSubmitting,
        state.isJobActive,
        state.isCapabilityAvailable,
        state.selectedItemIds,
        state.hasPersonFront,
        state.hasAllSelectedGarmentFronts,
        state.requestedViews,
        state.selectedViewMode,
        state.canSelectBackView,
        state.canSelectDualView,
        state.backViewUnavailableReason,
        state.dualViewUnavailableReason,
        state.consentConfirmed,
    ) {
        if (state.canSubmitTryOn || state.isSubmitting || state.isJobActive || !state.isCapabilityAvailable) {
            null
        } else {
            when {
                state.selectedItemIds.isEmpty() ->
                    "Chọn ít nhất một món trang phục ở trên để tiếp tục."
                ImageView.FRONT in state.requestedViews && !state.hasPersonFront ->
                    "Chọn ảnh người dùng mặt trước để bắt đầu thử đồ."
                ImageView.FRONT in state.requestedViews && !state.hasAllSelectedGarmentFronts ->
                    "Chọn đủ ảnh mặt trước cho trang phục đang thử."
                (ImageView.BACK in state.requestedViews && !state.canSelectBackView) ||
                    (state.selectedViewMode == TryOnViewMode.FRONT_AND_BACK && !state.canSelectDualView) ->
                    sanitizeUserFacingText(
                        state.backViewUnavailableReason
                            ?: state.dualViewUnavailableReason
                            ?: "Bổ sung đủ ảnh cho góc nhìn đã chọn.",
                    )
                !state.consentConfirmed ->
                    "Đánh dấu đồng ý gửi ảnh ở trên để mở nút tạo ảnh."
                else -> null
            }
        }
    }

    TryOnPanel(modifier = modifier.fillMaxWidth()) {
        TryOnSectionHeading(
            title = "Đồng ý xử lý ảnh",
            subtitle = "Chỉ gửi $viewCount ảnh toàn thân và $garmentPhotoCount ảnh trang phục đã chọn cho riêng lượt thử này.",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = NoticeCornerShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = TryOnPrivacyDisclosure.retentionNoticeForProvider(state.capability?.providerId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(NoticeCornerShape)
                .toggleable(
                    value = state.consentConfirmed,
                    enabled = !state.isSubmitting && !state.isJobActive,
                    role = Role.Checkbox,
                    onValueChange = onConsentChanged,
                )
                .padding(vertical = 4.dp)
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

        val submitLabel = if (state.isSubmitting || state.isJobActive) {
            "Đang tạo ảnh thử đồ…"
        } else {
            when (state.selectedViewMode) {
                TryOnViewMode.FRONT_AND_BACK -> "Tạo ảnh thử đồ · Trước & sau"
                TryOnViewMode.BACK_ONLY -> "Tạo ảnh thử đồ · Mặt sau"
                TryOnViewMode.FRONT_ONLY -> "Tạo ảnh thử đồ"
            }
        }
        TryOnPrimaryAction(
            text = submitLabel,
            onClick = onSubmit,
            enabled = state.canSubmitTryOn,
            busy = state.isSubmitting || state.isJobActive,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TryOnTestTags.SUBMIT_BUTTON),
        )

        if (submitHint != null) {
            TryOnInfoNotice(
                text = submitHint,
                tone = TryOnNoticeTone.NEUTRAL,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IdleLookbookStagePlaceholder(
    state: TryOnUiState,
    modifier: Modifier = Modifier,
) {
    TryOnPanel(
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(PhotoFrameCornerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "Khung xem kết quả",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "Sẵn sàng thử đồ 2D",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (state.consentConfirmed) {
                        "Nhấn \"Tạo ảnh thử đồ\" ở cột bên trái để bắt đầu."
                    } else {
                        "Chọn trang phục và đánh dấu đồng ý xử lý ảnh ở cột bên trái trước khi tạo ảnh."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProcessingStageSection(
    result: TryOnResult,
    onCancelJob: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stageTitle = when (result.status) {
        TryOnJobStatus.QUEUED -> "Đang chuẩn bị ảnh…"
        else -> "Đang tạo ảnh thử đồ…"
    }

    TryOnPanel(
        modifier = modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth(),
    ) {
        TryOnSectionHeading(
            title = stageTitle,
            subtitle = "Có thể mất 1–2 phút. Bạn có thể để màn hình mở trong lúc chờ.",
        )

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        OutlinedButton(
            onClick = onCancelJob,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(TryOnTestTags.CANCEL_BUTTON),
        ) {
            Text("Hủy lượt thử")
        }
    }
}

/**
 * Priority 8: Kết quả thử đồ 2D (Khung 3:4 ContentScale.Fit không viền + So sánh ảnh gốc + Thử lại/Xóa).
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

    TryOnPanel(
        modifier = modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .testTag(TryOnTestTags.RESULT_SECTION),
    ) {
        ResultStatusHeaderBadge(result = result)

        // Tab Mặt trước / Mặt sau + So sánh ảnh gốc theo chip Track A
        if (result.views.size > 1 || canCompareOriginal) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (result.views.containsKey(ImageView.FRONT) && result.views.size > 1) {
                    val frontVr = result.views[ImageView.FRONT]
                    TryOnChoiceChip(
                        label = "Mặt trước · ${shortViewBadgeVi(frontVr)}",
                        selected = activeTab == ImageView.FRONT,
                        onClick = { onSelectResultTab(ImageView.FRONT) },
                        modifier = Modifier.testTag(TryOnTestTags.TAB_FRONT),
                    )
                }
                if (result.views.containsKey(ImageView.BACK) && result.views.size > 1) {
                    val backVr = result.views[ImageView.BACK]
                    TryOnChoiceChip(
                        label = "Mặt sau · ${shortViewBadgeVi(backVr)}",
                        selected = activeTab == ImageView.BACK,
                        onClick = { onSelectResultTab(ImageView.BACK) },
                        modifier = Modifier.testTag(TryOnTestTags.TAB_BACK),
                    )
                }
                if (canCompareOriginal) {
                    TryOnChoiceChip(
                        label = if (state.compareWithOriginal) "Đang xem ảnh gốc" else "So sánh ảnh gốc",
                        selected = state.compareWithOriginal,
                        onClick = { onToggleCompareOriginal(!state.compareWithOriginal) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.testTag(TryOnTestTags.COMPARE_ORIGINAL_TOGGLE),
                    )
                }
            }
        }

        // Khung ảnh kết quả 3:4 lớn, bo 20.dp, không viền, ContentScale.Fit để giữ trọn đầu và chân
        Box(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .aspectRatio(3f / 4f)
                .clip(PhotoFrameCornerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Tuyên bố giới hạn tính năng (tonal gọn)
        TryOnInfoNotice(
            text = TryOnPrivacyDisclosure.PRODUCT_LIMITATION_DISCLAIMER,
            tone = TryOnNoticeTone.NEUTRAL,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TryOnTestTags.DISCLAIMER_TEXT),
        )

        // Hành động tiếp theo
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TryOnPrimaryAction(
                text = "Tạo lượt thử mới",
                onClick = onPrepareFreshAttempt,
                icon = Icons.Outlined.Refresh,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = onDeleteResult,
                shape = RoundedCornerShape(50),
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
                Text("Xóa kết quả này")
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
    when (result.status) {
        TryOnJobStatus.SUCCEEDED -> {
            val subtitle = when {
                result.views.size > 1 -> "Đã tạo đủ 2 góc nhìn. Chạm \"So sánh ảnh gốc\" để đối chiếu."
                result.views.keys == setOf(ImageView.BACK) -> "Kết quả mặt sau. Chạm \"So sánh ảnh gốc\" để đối chiếu."
                else -> "Chạm \"So sánh ảnh gốc\" để đối chiếu trước và sau khi thay đồ."
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                TryOnSectionHeading(
                    title = "Kết quả thử đồ",
                    subtitle = subtitle,
                )
            }
        }

        TryOnJobStatus.PARTIAL -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TryOnSectionHeading(
                    title = "Kết quả thử đồ",
                    subtitle = "Hoàn tất 1/${result.views.size} góc nhìn.",
                )
                TryOnInfoNotice(
                    text = "Một góc nhìn không tạo được. Ứng dụng giữ nguyên thông báo lỗi, không tự chèn ảnh thay thế.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TryOnTestTags.PARTIAL_BADGE),
                )
            }
        }

        TryOnJobStatus.TIMED_OUT -> {
            TryOnErrorNotice(
                text = "Yêu cầu quá thời gian chờ xử lý. Bạn có thể tạo lại lượt mới khi sẵn sàng.",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TryOnJobStatus.UNKNOWN_SUBMISSION -> {
            TryOnErrorNotice(
                text = "Kết nối bị ngắt trước khi máy chủ xác nhận. Ứng dụng không tự gửi lại để tránh trùng lượt.",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TryOnJobStatus.CANCELLED -> {
            TryOnInfoNotice(
                text = "Đã hủy lượt thử đồ theo yêu cầu của bạn.",
                tone = TryOnNoticeTone.NEUTRAL,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> {
            val msg = result.views.values.firstNotNullOfOrNull { it.error?.message }
                ?.let(::sanitizeUserFacingText)
                ?.takeIf { it.isNotBlank() }
                ?: "Không thể tạo ảnh cho bộ phối này. Vui lòng thử lại."
            TryOnErrorNotice(
                text = msg,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                body = "Góc nhìn này chưa được yêu cầu trong lượt thử đồ.",
            )
        }

        viewResult.state == ViewProcessState.SUCCEEDED &&
            viewResult.qualityReview == QualityReviewState.REJECTED -> {
            CanvasMessageState(
                title = "Đã ẩn ảnh chưa đạt chất lượng",
                body = "Ảnh tạo ra có sai lệch dáng hoặc trang phục nên đã được ẩn đi.",
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
                " · Nền trắng studio"
            } else {
                ""
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
                showBottomCaptionOnDiskImage = true,
            )
        }

        viewResult.state == ViewProcessState.EXPIRED -> {
            CanvasMessageState(
                title = "Ảnh kết quả tạm thời đã hết hạn",
                body = "Ảnh không còn lưu trên máy chủ. Hãy tạo lại lượt thử mới nếu cần.",
            )
        }

        else -> {
            CanvasMessageState(
                title = if (activeView == ImageView.BACK) {
                    "Góc nhìn mặt sau chưa thành công"
                } else {
                    "Góc nhìn mặt trước chưa thành công"
                },
                body = viewResult.error?.message?.let(::sanitizeUserFacingText)
                    ?: "Không thể tạo ảnh hợp lệ cho góc nhìn này.",
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
            color = MaterialTheme.colorScheme.onSurface,
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
 * editorial vector lookbook diagram when `localRelativePath` starts with `seed://`.
 */
@Composable
private fun TryOnMediaPreviewSurface(
    mediaRef: MediaRef,
    caption: String,
    isFakeOutput: Boolean,
    isLiveAiOutput: Boolean = false,
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
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                        .padding(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (isFakeOutput) {
                        Text(
                            text = "Ảnh giả lập · chưa qua AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            )
            if (!compactThumbnail) {
                if (isFakeOutput) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "Ảnh giả lập",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                } else if (isLiveAiOutput) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "Ảnh AI thực tế",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
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
    TryOnErrorNotice(
        text = sanitizeUserFacingText(message),
        modifier = modifier.fillMaxWidth(),
        action = if (isStale) {
            {
                OutlinedButton(
                    onClick = onSelectDifferentOutfit,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Chọn lại trang phục")
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun StatusNoticeBlock(
    notice: String,
    modifier: Modifier = Modifier,
) {
    TryOnInfoNotice(
        text = sanitizeUserFacingText(notice),
        tone = TryOnNoticeTone.NEUTRAL,
        modifier = modifier.fillMaxWidth(),
    )
}

private val TechnicalPrefixRegex = Regex("^[A-Z][A-Z0-9_]{2,}:\\s*")

/** Removes internal enum names and error codes before showing server/VM reasons to users. */
private fun sanitizeUserFacingText(raw: String): String = raw
    .lineSequence()
    .map { line ->
        line.replace(TechnicalPrefixRegex, "")
            .replace(" (FRONT_ONLY)", "")
            .replace(" (BACK_ONLY)", "")
            .replace(" (ORIGINAL_PHOTO)", "")
            .replace(" (STUDIO_WHITE)", "")
            .replace(" (TOP+BOTTOM)", "")
            .replace("TRYON_LIVE_ENABLED đang là false", "chế độ trực tiếp đang tắt")
            .replace("FOSTA_APPROVE_QUOTA=true", "xác nhận hạn mức")
            .replace("FOSTA_API_KEY", "khóa kết nối dịch vụ")
            .replace("'UNKNOWN'", "hiện tại")
            .replace("'NO_GO'", "tạm khóa")
            .trim()
    }
    .filter { it.isNotEmpty() }
    .joinToString(" ")

private fun categoryLabelVi(category: Category): String = when (category) {
    Category.TOP -> "Áo"
    Category.BOTTOM -> "Quần / chân váy"
    Category.DRESS -> "Đầm liền"
    Category.OUTERWEAR -> "Áo khoác"
    Category.SHOES -> "Giày"
    Category.ACCESSORY -> "Phụ kiện"
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
