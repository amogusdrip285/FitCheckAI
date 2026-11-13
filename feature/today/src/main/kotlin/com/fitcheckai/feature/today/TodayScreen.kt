package com.fitcheckai.feature.today

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.model.profile.STYLE_OPTIONS
import com.fitcheckai.core.model.recommendation.MAX_FREE_TEXT
import com.fitcheckai.core.model.recommendation.MAX_OUTFITS
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.ui.component.EmptyState
import com.fitcheckai.core.ui.component.OutfitVisual
import com.fitcheckai.core.ui.component.ScreenHeader
import com.fitcheckai.core.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    onOpenItem: (String) -> Unit,
    onTryOn: (route: String) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var openingTryOn by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(eyebrow = "Phối từ tủ đồ của bạn", title = "Hôm nay mặc gì?")
        OccasionChips(state.occasion) {
            shareError = null
            viewModel.onOccasionSelected(it)
        }
        RequestOptions(
            state = state,
            enabled = !state.isLoading,
            onStyleToggled = viewModel::onStyleToggled,
            onFreeTextChanged = viewModel::onFreeTextChanged,
            onCountSelected = viewModel::onCountSelected,
            onApply = {
                shareError = null
                viewModel.applyPreferences()
            },
        )
        shareError?.let {
            Text(it, Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.error)
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.outfits.isEmpty() -> EmptyState(
                title = "Chưa phối được",
                message = state.message ?: "Hãy thêm vài món đồ vào tủ.",
                actionLabel = "Thử lại",
                onAction = viewModel::suggest,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.outfits, key = { it.outfitId }) { outfit ->
                    val worn = outfit.outfitId == state.wornOutfitId
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutfitVisual(
                            outfit = outfit,
                            onSelect = viewModel::onWear,
                            onOpenItem = onOpenItem,
                            selectionLabel = if (worn) "Đã ghi nhận ✓" else "Mặc bộ này",
                            selectionEnabled = !worn,
                            shareEnabled = !sharing && outfit.items.all { it.imagePath != null },
                            onShare = { id ->
                                if (!sharing) {
                                    sharing = true
                                    shareError = null
                                    scope.launch {
                                        try {
                                            viewModel.prepareShare(id).fold(
                                                onSuccess = { intent ->
                                                    try { context.startActivity(Intent.createChooser(intent, "Chia sẻ ảnh bộ đồ")) }
                                                    catch (_: ActivityNotFoundException) {
                                                        shareError = "Thiết bị chưa có ứng dụng nhận ảnh."
                                                    }
                                                },
                                                onFailure = { shareError = it.message ?: "Chưa chuẩn bị được ảnh để chia sẻ." },
                                            )
                                        } finally { sharing = false }
                                    }
                                }
                            },
                        )
                        TextButton(
                            enabled = !sharing && !openingTryOn,
                            onClick = {
                                if (!openingTryOn) {
                                    openingTryOn = true
                                    shareError = null
                                    scope.launch {
                                        try {
                                            viewModel.prepareTryOn(outfit.outfitId).fold(
                                                onSuccess = onTryOn,
                                                onFailure = { shareError = it.message ?: "Chưa mở được thử đồ." },
                                            )
                                        } finally { openingTryOn = false }
                                    }
                                }
                            },
                        ) { Text("Thử đồ với bộ này") }
                    }
                }
                state.message?.let { note -> item { Text(note, style = MaterialTheme.typography.bodySmall) } }
                item { OutlinedButton(onClick = viewModel::reroll) { Text("Phối lại") } }
            }
        }
    }
}

/** C-001: optional style/free-text/count. Collapsed by default; editing never triggers a request. */
@Composable
private fun RequestOptions(
    state: TodayUiState,
    enabled: Boolean,
    onStyleToggled: (String) -> Unit,
    onFreeTextChanged: (String) -> Unit,
    onCountSelected: (Int) -> Unit,
    onApply: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Spacing.md)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Ẩn tùy chỉnh" else "Tùy chỉnh yêu cầu")
        }
        if (!expanded) return@Column
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Phong cách (để trống = theo hồ sơ)", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                STYLE_OPTIONS.forEach { style ->
                    FilterChip(
                        selected = style in state.styleOverrides,
                        onClick = { onStyleToggled(style) },
                        label = { Text(style) },
                    )
                }
            }
            OutlinedTextField(
                value = state.freeText,
                onValueChange = onFreeTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Yêu cầu thêm (không bắt buộc)") },
                supportingText = { Text("${state.freeText.length}/$MAX_FREE_TEXT") },
                maxLines = 3,
            )
            Text("Số bộ muốn xem", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                (1..MAX_OUTFITS).forEach { count ->
                    FilterChip(
                        selected = count == state.requestedCount,
                        onClick = { onCountSelected(count) },
                        label = { Text("$count") },
                    )
                }
            }
            Button(onClick = onApply, enabled = enabled) { Text("Gợi ý theo yêu cầu") }
        }
    }
}

@Composable
private fun OccasionChips(selected: Occasion, onSelect: (Occasion) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Occasion.entries.forEach { occasion ->
            FilterChip(
                selected = occasion == selected,
                onClick = { onSelect(occasion) },
                label = { Text(occasion.label()) },
            )
        }
    }
}

private fun Occasion.label(): String = when (this) {
    Occasion.SCHOOL -> "Đi học"
    Occasion.WORK -> "Đi làm"
    Occasion.DATE -> "Hẹn hò"
    Occasion.PARTY -> "Tiệc"
    Occasion.SPORT -> "Thể thao"
    Occasion.CASUAL -> "Dạo phố"
    Occasion.FORMAL_EVENT -> "Sự kiện"
}
