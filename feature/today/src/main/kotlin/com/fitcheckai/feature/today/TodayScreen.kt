package com.fitcheckai.feature.today

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.Occasion
import com.fitcheckai.core.model.Outfit
import com.fitcheckai.core.model.OutfitSource
import com.fitcheckai.core.model.WeatherSnapshot
import com.fitcheckai.core.ui.component.EmptyState
import com.fitcheckai.core.ui.component.ItemThumbnail
import com.fitcheckai.core.ui.component.ScreenHeader
import com.fitcheckai.core.ui.theme.Spacing
import kotlin.math.roundToInt

private val THUMB_SIZE = 96.dp

@Composable
fun TodayScreen(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(eyebrow = weatherLine(state.suggestions?.weather), title = "Hôm nay mặc gì?")
        OccasionChips(state.occasion, viewModel::onOccasionSelected)
        val suggestions = state.suggestions
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            suggestions == null || suggestions.outfits.isEmpty() -> EmptyState(
                title = "Chưa phối được",
                message = suggestions?.emptyReason ?: "Hãy thêm vài món đồ vào tủ.",
                actionLabel = "Thử lại",
                onAction = viewModel::suggest,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(suggestions.outfits, key = { it.id }) { outfit ->
                    OutfitCard(
                        outfit = outfit,
                        items = outfit.itemIds.mapNotNull(state.itemsById::get),
                        isWorn = outfit.id == state.wornOutfitId,
                        onWear = { viewModel.onWear(outfit) },
                    )
                }
                item { OutlinedButton(onClick = viewModel::suggest) { Text("Phối lại") } }
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

@Composable
private fun OutfitCard(outfit: Outfit, items: List<ClothingItem>, isWorn: Boolean, onWear: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items.forEach { ItemThumbnail(it, Modifier.width(THUMB_SIZE)) }
            }
            Text(outfit.explanation, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (outfit.source == OutfitSource.AI) "Gợi ý bởi AI" else "Chế độ offline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onWear, enabled = !isWorn) {
                Text(if (isWorn) "Đã ghi nhận ✓" else "Mặc bộ này")
            }
        }
    }
}

private fun weatherLine(weather: WeatherSnapshot?): String =
    weather?.let { "${it.city} · ${it.tempC.roundToInt()}°C · ${it.condition}" } ?: "Đang lấy thời tiết…"

private fun Occasion.label(): String = when (this) {
    Occasion.SCHOOL -> "Đi học"
    Occasion.WORK -> "Đi làm"
    Occasion.DATE -> "Hẹn hò"
    Occasion.PARTY -> "Tiệc"
    Occasion.SPORT -> "Thể thao"
    Occasion.CASUAL -> "Dạo phố"
    Occasion.FORMAL_EVENT -> "Sự kiện"
}
