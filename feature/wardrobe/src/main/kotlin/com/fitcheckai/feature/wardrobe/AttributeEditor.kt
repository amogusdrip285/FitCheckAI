package com.fitcheckai.feature.wardrobe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.Season
import kotlin.math.roundToInt

private val PATTERNS = listOf("solid" to "Trơn", "striped" to "Sọc", "plaid" to "Caro", "floral" to "Hoa", "graphic" to "In hình")
internal val StyleLabels = mapOf("basic" to "Cơ bản", "minimal" to "Tối giản", "streetwear" to "Đường phố",
    "office" to "Công sở", "smart-casual" to "Thanh lịch", "sporty" to "Thể thao")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttributeEditor(form: AttributeForm, onChange: (AttributeForm) -> Unit, enabled: Boolean = true) {
    var customColors by rememberSaveable { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedColors = form.colors.split(',').map(String::trim).filter(String::isNotBlank)
    val toggleColor: (String) -> Unit = { hex ->
        val selected = selectedColors.any { it.equals(hex, true) }
        onChange(form.copy(colors = (if (selected) selectedColors.filterNot { it.equals(hex, true) }
            else selectedColors + hex).joinToString(", ")))
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WardrobePanel(Modifier.fillMaxWidth()) {
            SectionHeading("Thông tin chính", "Đặt tên để tìm món đồ dễ hơn.")
            EditorField(form.name, { onChange(form.copy(name = it)) }, "Tên món đồ *", enabled,
                placeholder = "Ví dụ: Sơ mi linen trắng")
            Text("Loại đồ *", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Category.entries.forEach { category ->
                    ChoiceChip(category.shortLabel, form.category == category,
                        { onChange(form.copy(category = category)) }, enabled = enabled)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeading("Màu sắc *", "Có thể chọn nhiều màu.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WardrobePalette.forEach { (hex, name) ->
                    val selected = selectedColors.any { it.equals(hex, true) }
                    Column(Modifier.width(56.dp).heightIn(min = 64.dp)
                        .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { toggleColor(hex) })
                        .semantics { contentDescription = "Màu $name" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(40.dp).border(if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center) {
                            ColorDot(hex, Modifier.size(30.dp), selected)
                        }
                        Text(name, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val extraColors = selectedColors.filter { hex -> WardrobePalette.none { it.first.equals(hex, true) } }
            if (extraColors.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                extraColors.forEach { hex ->
                    ChoiceChip(colorName(hex), true, { toggleColor(hex) }, enabled = enabled,
                        leadingIcon = { ColorDot(hex, Modifier.size(16.dp)) })
                }
            }
            TextButton({ customColors = !customColors }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                Icon(if (customColors) Icons.Outlined.ExpandLess else Icons.Outlined.Add, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Màu tùy chỉnh")
            }
            AnimatedVisibility(customColors) {
                EditorField(form.colors, { onChange(form.copy(colors = it)) }, "Mã màu", enabled,
                    placeholder = "#FFFFFF, #3B5B8C", supporting = "Nhập mã #RRGGBB; phân cách bằng dấu phẩy.")
            }
        }
        WardrobePanel(Modifier.fillMaxWidth()) {
            SectionHeading("Dịp mặc phù hợp", "Giúp gợi ý phối đồ sát với bạn hơn.")
            Text("Mùa *", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Season.entries.forEach { season ->
                    ChoiceChip(season.label, season in form.seasons, {
                        onChange(form.copy(seasons = if (season in form.seasons) form.seasons - season else form.seasons + season))
                    }, enabled = enabled)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Độ trang trọng", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(formalityLabel(form.formality), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Column {
                Slider(form.formality.toFloat(), { onChange(form.copy(formality = it.roundToInt())) },
                    Modifier.semantics { contentDescription = "Độ trang trọng, ${form.formality} trên 5" },
                    valueRange = 1f..5f, steps = 3, enabled = enabled)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Thoải mái", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Trang trọng", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))) {
            Column {
                TextButton({ detailsExpanded = !detailsExpanded }, Modifier.fillMaxWidth(),
                    enabled = enabled, shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(18.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Chi tiết thêm", style = MaterialTheme.typography.titleMedium)
                        Text("Họa tiết, chất liệu & phong cách", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (detailsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
                AnimatedVisibility(detailsExpanded) {
                    Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Họa tiết", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PATTERNS.forEach { (value, label) ->
                                ChoiceChip(label, form.pattern == value, { onChange(form.copy(pattern = value)) }, enabled = enabled)
                            }
                        }
                        EditorField(form.pattern, { onChange(form.copy(pattern = it)) }, "Họa tiết tùy chỉnh", enabled)
                        EditorField(form.material, { onChange(form.copy(material = it)) }, "Chất liệu", enabled,
                            placeholder = "Cotton, denim, linen…")
                        Text("Phong cách", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val styles = form.styles.split(',').map(String::trim).filter(String::isNotBlank)
                            StyleLabels.forEach { (value, label) ->
                                val selected = value in styles
                                ChoiceChip(label, selected, {
                                    onChange(form.copy(styles = (if (selected) styles - value else styles + value).joinToString(", ")))
                                }, enabled = enabled)
                            }
                        }
                        EditorField(form.styles, { onChange(form.copy(styles = it)) }, "Nhãn phong cách", enabled,
                            supporting = "Thêm nhãn riêng, phân cách bằng dấu phẩy.")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String, onChange: (String) -> Unit, label: String, enabled: Boolean,
    placeholder: String? = null, supporting: String? = null,
) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } }, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
}

internal fun patternLabel(pattern: String): String = PATTERNS.firstOrNull { it.first == pattern }?.second ?: pattern
internal fun formalityLabel(value: Int): String = when (value) {
    1 -> "Thoải mái"
    2 -> "Hằng ngày"
    3 -> "Chỉn chu"
    4 -> "Lịch sự"
    else -> "Trang trọng"
}
