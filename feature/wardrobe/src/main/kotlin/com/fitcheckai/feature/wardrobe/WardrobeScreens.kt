package com.fitcheckai.feature.wardrobe

import com.fitcheckai.core.ui.component.GarmentPhoto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.Season

@Composable
fun WardrobeScreen(onAddItem: () -> Unit, onOpenItem: (String) -> Unit, viewModel: WardrobeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WardrobeTheme { WardrobeContent(state, viewModel::filter, onAddItem, onOpenItem) }
}

@Composable
internal fun WardrobeContent(state: WardrobeState, onFilter: (WardrobeFilter) -> Unit, onAddItem: () -> Unit, onOpenItem: (String) -> Unit) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    // The app NavHost already applies system bars and bottom navigation padding.
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (state.total > 0) ExtendedFloatingActionButton(
                onClick = { focus.clearFocus(); onAddItem() },
                modifier = Modifier.semantics { contentDescription = "Thêm đồ" },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Thêm đồ", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("BỘ SƯU TẬP CÁ NHÂN", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                    Text("Tủ đồ của bạn", style = MaterialTheme.typography.headlineLarge)
                }
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text("${state.total} món", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> WardrobeEmpty("Chưa tải được tủ đồ", state.error)
                state.total == 0 -> WardrobeEmpty("Tủ đồ trống",
                    "Bắt đầu với món đồ bạn yêu thích. Chụp hoặc chọn ảnh, chúng mình sẽ giúp bạn sắp xếp.",
                    "Thêm món đầu tiên", onAddItem)
                else -> {
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            state.filter.query, { onFilter(state.filter.copy(query = it)) },
                            Modifier.weight(1f).semantics { contentDescription = "Tìm kiếm tủ đồ" },
                            placeholder = { Text("Tìm tên, chất liệu…", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(21.dp)) },
                            trailingIcon = if (state.filter.query.isNotEmpty()) ({
                                IconButton({ onFilter(state.filter.copy(query = "")) }) {
                                    Icon(Icons.Outlined.Close, "Xóa tìm kiếm", Modifier.size(18.dp))
                                }
                            }) else null,
                            singleLine = true, shape = RoundedCornerShape(16.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        )
                        BadgedBox(badge = {
                            val count = listOf(state.filter.color, state.filter.season).count { it != null }
                            if (count > 0) Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text("$count") }
                        }) {
                            FilledTonalIconButton({ focus.clearFocus(); filtersOpen = true },
                                Modifier.size(54.dp), shape = RoundedCornerShape(16.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )) {
                                Icon(Icons.Outlined.Tune, "Bộ lọc")
                            }
                        }
                    }
                    LazyRow(Modifier.padding(top = 12.dp), contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ChoiceChip("Tất cả", state.filter.category == null, { onFilter(state.filter.copy(category = null)) }) }
                        items(Category.entries) { category ->
                            ChoiceChip(category.shortLabel, state.filter.category == category, {
                                onFilter(state.filter.copy(category = if (state.filter.category == category) null else category))
                            })
                        }
                    }
                    if (state.filter.color != null || state.filter.season != null) LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.filter.color?.let { color -> item {
                            InputChip(true, { onFilter(state.filter.copy(color = null)) }, { Text(colorName(color)) },
                                leadingIcon = { ColorDot(color, Modifier.size(14.dp)) },
                                trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(14.dp)) },
                                modifier = Modifier.semantics { contentDescription = "Bỏ lọc màu ${colorName(color)}" })
                        } }
                        state.filter.season?.let { season -> item {
                            InputChip(true, { onFilter(state.filter.copy(season = null)) }, { Text(season.label) },
                                trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(14.dp)) },
                                modifier = Modifier.semantics { contentDescription = "Bỏ lọc mùa ${season.label}" })
                        } }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.items.size} món đang hiển thị", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.filter.isActive) TextButton({ focus.clearFocus(); onFilter(WardrobeFilter()) }) {
                            Text("Xóa lọc", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("Mới nhất", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.items.isEmpty()) WardrobeEmpty("Chưa có món phù hợp",
                        "Thử từ khóa khác hoặc bớt một bộ lọc để tìm lại món đồ của bạn.",
                        "Xóa bộ lọc", { onFilter(WardrobeFilter()) })
                    else LazyVerticalGrid(
                        columns = GridCells.Adaptive(148.dp), modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(state.items, key = { it.id }) { item -> WardrobeItemCard(item) {
                            focus.clearFocus(); onOpenItem(item.id)
                        } }
                    }
                }
            }
        }
    }
    if (filtersOpen) WardrobeFilterSheet(state, onFilter, { filtersOpen = false })
}

@Composable
private fun WardrobeItemCard(item: ClothingItem, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column(Modifier.padding(8.dp)) {
            GarmentPhoto(item.imagePath, null, Modifier.fillMaxWidth().aspectRatio(.88f))
            Column(Modifier.padding(start = 5.dp, end = 5.dp, top = 12.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(item.attributes.category.label.uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(item.attributes.subCategory, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, minLines = 1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.attributes.colors.take(3).forEach { ColorDot(it, Modifier.size(12.dp)) }
                    Spacer(Modifier.weight(1f))
                    Text(if (item.wearCount == 0) "Chưa mặc" else "${item.wearCount} lần mặc",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WardrobeFilterSheet(state: WardrobeState, onFilter: (WardrobeFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bộ lọc", style = MaterialTheme.typography.titleLarge)
                    Text("Tìm đúng món cho ngày hôm nay.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({ onFilter(state.filter.copy(color = null, season = null)) }) { Text("Đặt lại") }
            }
            SectionHeading("Màu sắc", "Chọn màu chủ đạo của món đồ.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChoiceChip("Tất cả màu", state.filter.color == null, { onFilter(state.filter.copy(color = null)) })
                state.colors.forEach { hex ->
                    ChoiceChip(colorName(hex), state.filter.color.equals(hex, true), {
                        onFilter(state.filter.copy(color = if (state.filter.color.equals(hex, true)) null else hex))
                    }, leadingIcon = { ColorDot(hex, Modifier.size(18.dp)) },
                        modifier = Modifier.semantics { contentDescription = "Lọc màu $hex" })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeading("Mùa phù hợp")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Quanh năm", state.filter.season == null, { onFilter(state.filter.copy(season = null)) })
                Season.entries.forEach { season ->
                    ChoiceChip(season.label, state.filter.season == season, {
                        onFilter(state.filter.copy(season = if (state.filter.season == season) null else season))
                    })
                }
            }
            PrimaryAction("Xem ${state.items.size} món đồ", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun WardrobeEmpty(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Outlined.Checkroom, null, Modifier.padding(24.dp).size(42.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryAction(action, onAction, icon = if (title == "Tủ đồ trống") Icons.Outlined.Add else null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackTopBar(
    title: String, onBack: () -> Unit, enabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(onBack, enabled = enabled) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại", Modifier.size(22.dp))
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

internal val Category.shortLabel: String get() = when (this) {
    Category.TOP -> "Áo"
    Category.BOTTOM -> "Quần / váy"
    Category.DRESS -> "Đầm"
    Category.OUTERWEAR -> "Áo khoác"
    Category.SHOES -> "Giày"
    Category.ACCESSORY -> "Phụ kiện"
}
