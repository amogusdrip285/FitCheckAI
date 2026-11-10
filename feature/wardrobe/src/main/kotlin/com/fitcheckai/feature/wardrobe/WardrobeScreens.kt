package com.fitcheckai.feature.wardrobe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.ui.component.EmptyState
import com.fitcheckai.core.ui.component.ItemThumbnail
import com.fitcheckai.core.ui.component.ScreenHeader
import com.fitcheckai.core.ui.theme.Spacing

private const val GRID_COLUMNS = 3

@Composable
fun WardrobeScreen(
    onAddItem: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: WardrobeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItem,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Thêm đồ") },
            )
        },
    ) { padding ->
        val current = items
        when {
            current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            current.isEmpty() -> EmptyState(
                title = "Tủ đồ trống",
                message = "Chụp món đồ đầu tiên — AI sẽ tách nền và gắn nhãn giúp bạn.",
                actionLabel = "Thêm đồ",
                onAction = onAddItem,
                modifier = Modifier.padding(padding),
            )
            else -> WardrobeGrid(current, onOpenItem, Modifier.padding(padding))
        }
    }
}

@Composable
private fun WardrobeGrid(items: List<ClothingItem>, onOpenItem: (String) -> Unit, modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeader(eyebrow = "${items.size} món", title = "Tủ đồ")
        }
        items(items, key = { it.id }) { item ->
            ItemThumbnail(
                item = item,
                modifier = Modifier
                    .padding(horizontal = Spacing.xs)
                    .clickable { onOpenItem(item.id) },
            )
        }
    }
}

/** TODO(Track A): CameraX/Photo Picker → tách nền → ClothingTagger → form → lưu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemPlaceholderScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar("Thêm đồ", onBack) }) { padding ->
        EmptyState(
            title = "Sắp có",
            message = "Luồng chụp ảnh + AI gắn nhãn (Track A).",
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onStyleAround: (String) -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar(item?.attributes?.subCategory.orEmpty(), onBack) }) { padding ->
        val current = item ?: return@Scaffold
        Column(
            modifier = Modifier.padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ItemThumbnail(current, Modifier.fillMaxWidth())
            val attrs = current.attributes
            Text("${attrs.category} · độ trang trọng ${attrs.formality}/5", style = MaterialTheme.typography.titleMedium)
            Text("Đã mặc ${current.wearCount} lần", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { onStyleAround(current.id) }) { Text("Phối quanh món này") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }
        },
    )
}
