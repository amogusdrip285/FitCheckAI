package com.fitcheckai.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.wardrobe.*

/** Track D supplies DTOs and callbacks; this component has no repository, navigation or AI client. */
data class ItemThumbnailData(val itemId: String, val name: String, val imagePath: String?,
    val category: Category, val missing: Boolean = false, val stale: Boolean = false, val backImagePath: String? = null)

data class OutfitVisualData(val outfitId: String, val title: String, val items: List<ItemThumbnailData>,
    val source: OutfitSource, val explanation: String = "") {
    val available get() = items.isNotEmpty() && items.none { it.missing || it.stale }
}

fun resolveOutfitItems(snapshot: WardrobeSnapshot, refs: List<ItemRevision>, paths: Map<String, String>): List<ItemThumbnailData> =
    refs.map { ref ->
        val item = snapshot.items.find { it.id == ref.itemId && it.deletedAt == null }
        ItemThumbnailData(ref.itemId, item?.name ?: "Món đồ đã xóa", item?.images?.front?.id?.let(paths::get),
            item?.attributes?.category ?: Category.UNKNOWN, missing = item == null, stale = item != null && item.revision != ref.revision,
            backImagePath = item?.images?.back?.id?.let(paths::get))
    }

@Composable
fun ItemThumbnail(item: ItemThumbnailData, onOpenItem: (String) -> Unit, modifier: Modifier = Modifier) {
    var back by rememberSaveable(item.itemId) { mutableStateOf(false) }
    Surface(onClick = { onOpenItem(item.itemId) }, enabled = !item.missing, modifier = modifier,
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GarmentPhoto(if (back) item.backImagePath else item.imagePath, "${item.name} · ${if (back) "Mặt sau" else "Mặt trước"}", Modifier.fillMaxWidth().height(128.dp))
            Text(item.name, style = MaterialTheme.typography.titleSmall)
            if (item.backImagePath != null) TextButton({ back = !back }) { Text(if (back) "Xem mặt trước" else "Xem mặt sau") }
            if (item.missing || item.stale) Text(if (item.missing) "Không còn trong tủ" else "Món đồ đã thay đổi",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutfitVisual(outfit: OutfitVisualData, onSelect: (String) -> Unit, onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier, onRefresh: (() -> Unit)? = null, onShare: ((String) -> Unit)? = null,
    selectionLabel: String = "Chọn bộ này", selectionEnabled: Boolean = true, shareEnabled: Boolean = true) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(outfit.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                val source = when (outfit.source) {
                    OutfitSource.AI_LIVE -> "Gợi ý bởi AI"
                    OutfitSource.RULES -> "Phối cơ bản"
                    OutfitSource.FAKE -> "Dữ liệu thử"
                }
                Text(source, style = MaterialTheme.typography.labelSmall)
            }
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                outfit.items.forEach { ItemThumbnail(it, onOpenItem, Modifier.weight(1f)) }
            }
            if (outfit.explanation.isNotBlank()) Text(outfit.explanation, style = MaterialTheme.typography.bodyMedium)
            if (!outfit.available) {
                Text("Tủ đồ đã thay đổi. Cần tạo lại gợi ý trước khi chọn bộ này.", color = MaterialTheme.colorScheme.error)
                onRefresh?.let { TextButton(it) { Text("Làm mới gợi ý") } }
            }
            Button(
                { onSelect(outfit.outfitId) },
                Modifier.fillMaxWidth(),
                enabled = outfit.available && selectionEnabled,
            ) {
                Text(selectionLabel)
            }
            onShare?.let { share ->
                TextButton({ share(outfit.outfitId) }, enabled = outfit.available && shareEnabled) {
                    Text("Chia sẻ ảnh mặt trước")
                }
            }
        }
    }
}

@Composable
fun OutfitVisualList(outfits: List<OutfitVisualData>, onSelect: (String) -> Unit, onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier, onRefresh: (() -> Unit)? = null, onShare: ((String) -> Unit)? = null) {
    require(outfits.size in 1..5) { "Một kết quả cần từ 1 đến 5 outfit." }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        outfits.forEach { OutfitVisual(it, onSelect, onOpenItem, onRefresh = onRefresh, onShare = onShare) }
    }
}
