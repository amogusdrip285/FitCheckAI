package com.fitcheckai.feature.wardrobe.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitcheckai.core.model.wardrobe.*
import com.fitcheckai.feature.wardrobe.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WardrobeFields(item: ClothingItem, enabled: Boolean, name: (String) -> Unit,
    update: (ClothingAttributes, AttributeField) -> Unit) {
    val a = item.attributes
    OutlinedTextField(item.name, name, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        label = { Text("Tên món đồ *") }, placeholder = { Text("Ví dụ: Áo sơ mi trắng") })
    SelectField(a.title("Nhóm đồ", AttributeField.CATEGORY), a.category.label, Category.entries.map { it.label }, enabled) { index ->
        update(a.copy(category = Category.entries[index]), AttributeField.CATEGORY)
    }
    if (a.category != Category.UNKNOWN) {
        val types = remember(a.category) { GarmentType.entries.filter { it.category == a.category } }
        val none = "Chưa xác định"
        SelectField(a.title("Loại chi tiết", AttributeField.GARMENT_TYPE),
            GarmentType.find(a.garmentType)?.label ?: none, listOf(none) + types.map { it.label }, enabled) { index ->
            update(a.copy(garmentType = types.getOrNull(index - 1)?.id), AttributeField.GARMENT_TYPE)
        }
    }
    Text(a.title("Màu sắc · tối đa 3", AttributeField.COLORS), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GarmentColor.entries.forEach { color ->
            FilterChip(selected = color in a.colors, onClick = {
                update(a.copy(colors = if (color in a.colors) a.colors - color else a.colors + color), AttributeField.COLORS)
            }, label = { Text(color.label) }, leadingIcon = { ColorDot(color.hex) },
                enabled = enabled && (color in a.colors || a.colors.size < 3))
        }
    }
    SelectField(a.title("Họa tiết", AttributeField.PATTERN), a.pattern.label, Pattern.entries.map { it.label }, enabled) {
        update(a.copy(pattern = Pattern.entries[it]), AttributeField.PATTERN)
    }
    OutlinedTextField(a.material.orEmpty(), { update(a.copy(material = it.take(100).ifBlank { null }), AttributeField.MATERIAL) },
        Modifier.fillMaxWidth(), enabled = enabled, singleLine = true, label = { Text(a.title("Chất liệu", AttributeField.MATERIAL)) },
        placeholder = { Text("Chưa xác định") })
    val formalities = listOf("Chưa xác định", "1 · Thoải mái", "2 · Hằng ngày", "3 · Lịch sự", "4 · Trang trọng", "5 · Rất trang trọng")
    SelectField(a.title("Mức trang trọng", AttributeField.FORMALITY), formalities[a.formality ?: 0], formalities, enabled) {
        update(a.copy(formality = it.takeIf { value -> value > 0 }), AttributeField.FORMALITY)
    }
    Text(a.title("Mùa phù hợp", AttributeField.SEASONS), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        seasons.forEach { (key, label) -> FilterChip(key in a.seasonTags,
            { update(a.copy(seasonTags = if (key in a.seasonTags) a.seasonTags - key else a.seasonTags + key), AttributeField.SEASONS) },
            label = { Text(label) }, enabled = enabled) }
    }
    Text(a.title("Phong cách · tùy chọn", AttributeField.STYLES), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        a.styleTags.forEach { tag -> InputChip(true, { update(a.copy(styleTags = a.styleTags - tag), AttributeField.STYLES) },
            label = { Text("$tag ×") }, enabled = enabled) }
    }
    var tag by rememberSaveable(item.id) { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(tag, { tag = it.take(32) }, Modifier.weight(1f), singleLine = true,
            enabled = enabled && a.styleTags.size < 5, label = { Text("Thêm nhãn phong cách") })
        TextButton({
            update(a.copy(styleTags = (a.styleTags + tag.trim()).distinct()), AttributeField.STYLES); tag = ""
        }, enabled = enabled && tag.isNotBlank() && a.styleTags.size < 5) { Text("Thêm") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectField(label: String, value: String, options: List<String>, enabled: Boolean, select: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { if (enabled) expanded = it }) {
        OutlinedTextField(value, {}, Modifier.fillMaxWidth().menuAnchor(),
            readOnly = true, enabled = enabled, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; select(index) }) }
        }
    }
}

@Composable
internal fun AttributeSummary(a: ClothingAttributes) {
    WardrobePanel {
        SectionHeading("Thông tin món đồ")
        listOf(
            Triple("Nhóm đồ", a.category.label, AttributeField.CATEGORY),
            Triple("Loại chi tiết", GarmentType.find(a.garmentType)?.label ?: "Chưa xác định", AttributeField.GARMENT_TYPE),
            Triple("Màu sắc", a.colors.joinToString { it.label }.ifBlank { "Chưa xác định" }, AttributeField.COLORS),
            Triple("Họa tiết", a.pattern.label, AttributeField.PATTERN),
            Triple("Chất liệu", a.material ?: "Chưa xác định", AttributeField.MATERIAL),
            Triple("Mức trang trọng", a.formality?.let { "$it / 5" } ?: "Chưa xác định", AttributeField.FORMALITY),
            Triple("Mùa", a.seasonTags.joinToString { seasons[it].orEmpty() }.ifBlank { "Chưa xác định" }, AttributeField.SEASONS),
            Triple("Phong cách", a.styleTags.joinToString().ifBlank { "Chưa xác định" }, AttributeField.STYLES),
        ).forEach { (label, value, field) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(a.title(label, field), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private val seasons = linkedMapOf("SPRING" to "Xuân", "SUMMER" to "Hạ", "AUTUMN" to "Thu", "WINTER" to "Đông")
private fun ClothingAttributes.title(label: String, field: AttributeField): String {
    val source = when (attributeSources[field]) { AttributeSource.MANUAL -> "Tự nhập"; AttributeSource.AI -> "AI gợi ý"; null -> null }
    return listOfNotNull(label, source, "Cần kiểm tra".takeIf { field in needsReviewFields }).joinToString(" · ")
}
internal val Pattern.label get() = when (this) {
    Pattern.SOLID -> "Trơn"; Pattern.STRIPED -> "Kẻ sọc"; Pattern.CHECKED -> "Kẻ ô"
    Pattern.PRINTED -> "In họa tiết"; Pattern.OTHER -> "Khác"; Pattern.UNKNOWN -> "Chưa xác định"
}
