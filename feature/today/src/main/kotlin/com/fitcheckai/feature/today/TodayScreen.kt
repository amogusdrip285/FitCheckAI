package com.fitcheckai.feature.today

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
