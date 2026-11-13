package com.fitcheckai.feature.wardrobe

import com.fitcheckai.core.ui.component.GarmentPhoto

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.model.ClothingItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ItemDetailScreen(onBack: () -> Unit, onStyleAround: (String) -> Unit, viewModel: ItemDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WardrobeTheme {
        ItemDetailContent(state, onBack, onStyleAround, viewModel::startEditing, viewModel::cancelEditing,
            viewModel::edit, viewModel::save, viewModel::delete)
    }
}

@Composable
internal fun ItemDetailContent(
    state: DetailState, onBack: () -> Unit, onStyleAround: (String) -> Unit, onEdit: () -> Unit,
    onCancel: () -> Unit, onFormChange: (AttributeForm) -> Unit, onSave: () -> Unit, onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    BackHandler(state.editing || state.busy) { if (!state.busy) { focus.clearFocus(); onCancel() } }
    Scaffold(
        modifier = Modifier.imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(if (state.editing) "Sửa món đồ" else "Chi tiết món đồ",
                { focus.clearFocus(); if (state.editing) onCancel() else onBack() }, !state.busy) {
                if (!state.editing && state.item != null) Box {
                    IconButton({ menuOpen = true }, enabled = !state.busy) { Icon(Icons.Outlined.MoreHoriz, "Tùy chọn món đồ") }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Xóa món đồ", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmDelete = true },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        )
                    }
                }
            }
        },
        bottomBar = {
            state.item?.let { item -> ActionFooter {
                state.error?.let { ErrorNotice(it) }
                if (state.editing) PrimaryAction(
                    if (state.busy) "Đang lưu…" else "Lưu thay đổi",
                    { focus.clearFocus(); onSave() }, Modifier.fillMaxWidth(),
                    enabled = !state.busy, busy = state.busy, icon = Icons.Outlined.Check,
                ) else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onEdit, Modifier.heightIn(min = 54.dp), enabled = !state.busy,
                        shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Sửa")
                    }
                    PrimaryAction("Phối đồ", { onStyleAround(item.id) }, Modifier.weight(1f),
                        enabled = !state.busy, icon = Icons.Outlined.AutoAwesome)
                }
            } }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.item == null -> Box(Modifier.padding(padding)) {
                WardrobeEmpty("Món đồ không còn trong tủ", state.error ?: "Món đồ này có thể đã được xóa.", "Quay lại", onBack)
            }
            else -> {
                // Editing gets its own scroll state, so the form always opens at its first field.
                key(state.editing) {
                    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (state.editing) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                GarmentPhoto(state.item.imagePath, null, Modifier.size(84.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Một chút chỉnh sửa,", style = MaterialTheme.typography.titleMedium)
                                    Text("để tủ đồ đúng với bạn hơn.", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            AttributeEditor(state.form, onFormChange, !state.busy)
                        } else ItemDetails(state.item)
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Xóa món đồ?") },
        text = { Text("Món đồ và ảnh đã lưu sẽ được xóa khỏi tủ. Bạn có thể chọn Giữ lại để tiếp tục sử dụng.") },
        confirmButton = { TextButton({ confirmDelete = false; onDelete() }) {
            Text("Xóa món đồ", color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Giữ lại") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ItemDetails(item: ClothingItem) {
    val attrs = item.attributes
    GarmentPhoto(item.imagePath, "Ảnh ${attrs.subCategory}", Modifier.fillMaxWidth().height(268.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(attrs.category.label.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary)
        Text(attrs.subCategory, style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            attrs.colors.forEach { hex -> Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorDot(hex, Modifier.size(17.dp))
                Text(colorName(hex), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
    WardrobePanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.wearCount.toString(), style = MaterialTheme.typography.headlineSmall)
                Text("lần đã mặc", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VerticalDivider(Modifier.height(46.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val date = item.lastWornAt?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi")))
                Text(date ?: "Chưa mặc", style = MaterialTheme.typography.titleMedium)
                Text("Lần mặc gần nhất", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    WardrobePanel(Modifier.fillMaxWidth()) {
        SectionHeading("Chi tiết món đồ")
        DetailRow("Chất liệu", attrs.material?.replaceFirstChar { it.uppercase() } ?: "Chưa bổ sung")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DetailRow("Họa tiết", patternLabel(attrs.pattern))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DetailRow("Độ trang trọng", "${formalityLabel(attrs.formality)} · ${attrs.formality}/5")
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("Mùa phù hợp")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            attrs.seasons.forEach { season -> DetailTag(season.label) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("Phong cách")
        if (attrs.styleTags.isEmpty()) Text("Thêm phong cách trong phần Sửa món đồ.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            attrs.styleTags.forEach { DetailTag(StyleLabels[it] ?: it) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailTag(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelMedium)
    }
}
