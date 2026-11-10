package com.fitcheckai.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.WardrobeRepository
import com.fitcheckai.core.model.WearLogRepository
import com.fitcheckai.core.ui.component.ItemThumbnail
import com.fitcheckai.core.ui.component.ScreenHeader
import com.fitcheckai.core.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

private val THUMB_SIZE = 96.dp
private const val STOP_TIMEOUT_MS = 5_000L

@HiltViewModel
class InsightsViewModel @Inject constructor(
    wardrobe: WardrobeRepository,
    wearLog: WearLogRepository,
) : ViewModel() {
    val insights: StateFlow<WardrobeInsights?> =
        combine(wardrobe.observeItems(), wearLog.observeLogs()) { items, logs ->
            computeInsights(items, logs, Instant.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
}

/** TODO(Track D): biểu đồ, top món hay mặc, lịch sử outfit. */
@Composable
fun InsightsScreen(onStyleAround: (String) -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val data = insights ?: return
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader(eyebrow = "30 ngày qua", title = "Tủ đồ của bạn") }
        item {
            Row(
                Modifier.padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Stat("${data.utilizationPercent}%", "tủ đồ được mặc")
                Stat("${data.totalWears}", "lượt mặc")
                Stat("${data.totalItems}", "món đồ")
            }
        }
        item {
            Text(
                "Bị bỏ quên (${data.forgotten.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(Spacing.md),
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(data.forgotten, key = { it.id }) { item ->
                    ItemThumbnail(item, Modifier.width(THUMB_SIZE).clickable { onStyleAround(item.id) })
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.secondary)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
