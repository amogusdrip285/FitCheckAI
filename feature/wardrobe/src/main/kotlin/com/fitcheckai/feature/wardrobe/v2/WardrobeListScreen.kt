package com.fitcheckai.feature.wardrobe.v2

import com.fitcheckai.core.ui.component.GarmentPhoto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.model.wardrobe.*
import com.fitcheckai.feature.wardrobe.*

@Composable
fun WardrobeListScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, onDataset: () -> Unit,
    viewModel: WardrobeListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WardrobeTheme { WardrobeListContent(state, viewModel::filter, onAdd, onOpen, onDataset, viewModel::retry) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WardrobeListContent(state: ListState, onFilter: (Filter) -> Unit, onAdd: () -> Unit,
    onOpen: (String) -> Unit, onDataset: () -> Unit, onRetry: () -> Unit = {}) {
    // Text input must update synchronously; the derived list runs on IO and may lag keystrokes.
    var filter by rememberSaveable(stateSaver = listSaver<Filter, String>(
        save = { listOf(it.query, it.category?.name.orEmpty(), it.color?.name.orEmpty(), it.garmentType.orEmpty()) },
        restore = { Filter(it[0], it[1].takeIf(String::isNotEmpty)?.let(Category::valueOf),
            it[2].takeIf(String::isNotEmpty)?.let(GarmentColor::valueOf), GarmentType.find(it.getOrNull(3))?.id) },
    )) { mutableStateOf(state.filter) }
    val applyFilter: (Filter) -> Unit = { filter = it; onFilter(it) }
    var filterSheet by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), floatingActionButton = {
        if (state.total > 0) ExtendedFloatingActionButton(onClick = { focus.clearFocus(); onAdd() },
            containerColor = MaterialTheme.colorScheme.primary, icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text("Thêm đồ") }, modifier = Modifier.semantics { contentDescription = "Thêm đồ" })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BỘ SƯU TẬP CÁ NHÂN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Tủ đồ của bạn", style = MaterialTheme.typography.headlineLarge)
                }
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text("${state.total} món", Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> WardrobeEmpty("Chưa tải được tủ đồ", state.error, "Thử lại", onRetry)
                state.total == 0 -> {
                    Column(Modifier.weight(1f)) { WardrobeEmpty("Tủ đồ trống", "Thêm ảnh mặt trước của món đồ. Ảnh mặt sau có thể bổ sung sau.", "Thêm món đầu tiên", onAdd) }
                    if (BuildConfig.MOCK_WARDROBE) TextButton(onDataset, Modifier.align(Alignment.CenterHorizontally)) { Text("Bộ ảnh thật để kiểm thử") }
                }
                else -> {
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(filter.query, { applyFilter(filter.copy(query = it)) },
                            Modifier.weight(1f).semantics { contentDescription = "Tìm kiếm tủ đồ" }, singleLine = true,
                            placeholder = { Text("Tìm tên, loại đồ, chất liệu…") }, shape = MaterialTheme.shapes.medium,
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = if (filter.query.isNotBlank()) ({ IconButton({ applyFilter(filter.copy(query = "")) }) { Icon(Icons.Outlined.Close, "Xóa tìm kiếm") } }) else null)
                        FilledTonalIconButton({ focus.clearFocus(); filterSheet = true }, Modifier.size(54.dp), shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Outlined.Tune, "Bộ lọc")
                        }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ChoiceChip("Tất cả", filter.category == null, { applyFilter(filter.copy(category = null)) }) }
                        items(Category.entries) { category ->
                            ChoiceChip(category.label, filter.category == category, {
                                val next = category.takeUnless { it == filter.category }
                                val type = filter.garmentType.takeIf { next == null || GarmentType.find(it)?.category == next }
                                applyFilter(filter.copy(category = next, garmentType = type))
                            })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.cards.size} món đang hiển thị", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        if (filter.active) TextButton({ focus.clearFocus(); applyFilter(Filter()) }) { Text("Xóa lọc") }
                        else if (BuildConfig.MOCK_WARDROBE) TextButton(onDataset) { Text("Ảnh kiểm thử") }
                    }
                    if (state.cards.isEmpty()) WardrobeEmpty("Chưa có món phù hợp", "Thử từ khóa khác hoặc bớt một bộ lọc.", "Xóa bộ lọc", { applyFilter(Filter()) })
                    else LazyVerticalGrid(GridCells.Adaptive(148.dp), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(state.cards, key = { it.item.id }) { card ->
                            Surface(onClick = { focus.clearFocus(); onOpen(card.item.id) }, shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLowest, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    GarmentPhoto(card.frontPath, "Ảnh mặt trước ${card.item.name}", Modifier.fillMaxWidth().aspectRatio(.88f))
                                    Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        val attrs = card.item.attributes
                                        Text((GarmentType.find(attrs.garmentType)?.label ?: attrs.category.label).uppercase(),
                                            style = MaterialTheme.typography.labelSmall)
                                        Text(card.item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            card.item.attributes.colors.forEach { ColorDot(it.hex, Modifier.size(13.dp)) }
                                            Spacer(Modifier.weight(1f))
                                            Text(if (card.item.images.back != null) "2 mặt" else "Mặt trước", style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (card.item.attributes.needsReviewFields.isNotEmpty()) Text("Cần kiểm tra nhãn", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (filterSheet) ModalBottomSheet(onDismissRequest = { filterSheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val types = remember(filter.category) { GarmentType.entries.filter { filter.category == null || it.category == filter.category } }
            val allTypes = "Mọi loại"
            SelectField("Loại chi tiết", GarmentType.find(filter.garmentType)?.label ?: allTypes,
                listOf(allTypes) + types.map { it.label }, true) { index ->
                applyFilter(filter.copy(garmentType = types.getOrNull(index - 1)?.id))
            }
            SectionHeading("Màu sắc", "Chọn một màu có trong món đồ.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Tất cả màu", filter.color == null, { applyFilter(filter.copy(color = null)) })
                GarmentColor.entries.forEach { color -> ChoiceChip(color.label, filter.color == color,
                    { applyFilter(filter.copy(color = if (filter.color == color) null else color)) }, leadingIcon = { ColorDot(color.hex) }) }
            }
            PrimaryAction("Xem ${state.cards.size} món đồ", { filterSheet = false }, Modifier.fillMaxWidth())
        }
    }
}
