package com.fitcheckai.feature.wardrobe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/** Feature-local styling: shared components and other tracks keep their own theme. */
@Composable
internal fun WardrobeTheme(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFF3EEE7), onPrimary = Color(0xFF232522),
        primaryContainer = Color(0xFF373B34), onPrimaryContainer = Color(0xFFF3EEE7),
        secondary = Color(0xFFE4AB90), onSecondary = Color(0xFF3E2319),
        secondaryContainer = Color(0xFF49362D), onSecondaryContainer = Color(0xFFF4CBB5),
        tertiary = Color(0xFFBAC9AF), onTertiary = Color(0xFF273323),
        tertiaryContainer = Color(0xFF323D2D), onTertiaryContainer = Color(0xFFDCE9D4),
        background = Color(0xFF191B18), onBackground = Color(0xFFF3EEE7),
        surface = Color(0xFF191B18), onSurface = Color(0xFFF3EEE7),
        surfaceVariant = Color(0xFF2E312A), onSurfaceVariant = Color(0xFFBBBEB3),
        surfaceContainerLowest = Color(0xFF22251F), surfaceContainerLow = Color(0xFF242720),
        surfaceContainer = Color(0xFF282B24), surfaceContainerHigh = Color(0xFF32352E),
        outline = Color(0xFF8E9385), outlineVariant = Color(0xFF3D4237),
    ) else lightColorScheme(
        primary = Color(0xFF252822), onPrimary = Color(0xFFFFFDF9),
        primaryContainer = Color(0xFFE8EBDD), onPrimaryContainer = Color(0xFF30372B),
        secondary = Color(0xFFA24F35), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E6DC), onSecondaryContainer = Color(0xFF783E2B),
        tertiary = Color(0xFF526348), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEAF0E3), onTertiaryContainer = Color(0xFF3D5133),
        background = Color(0xFFF8F5EF), onBackground = Color(0xFF252822),
        surface = Color(0xFFF8F5EF), onSurface = Color(0xFF252822),
        surfaceVariant = Color(0xFFEFEAE1), onSurfaceVariant = Color(0xFF676B60),
        surfaceContainerLowest = Color(0xFFFFFEFB), surfaceContainerLow = Color(0xFFF3EFE7),
        surfaceContainer = Color(0xFFEFEAE1), surfaceContainerHigh = Color(0xFFE8E3D9),
        outline = Color(0xFF797E70), outlineVariant = Color(0xFFE1DED4),
    )
    val type = MaterialTheme.typography.copy(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Medium,
        ),
        headlineSmall = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp,
        ),
        titleLarge = MaterialTheme.typography.titleLarge.copy(
            fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.SansSerif),
        labelSmall = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.1.sp),
    )
    MaterialTheme(colorScheme = colors, typography = type, shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(30.dp),
    ), content = content)
}

internal fun garmentColor(hex: String): Color = hex.removePrefix("#").toLongOrNull(16)
    ?.takeIf { hex.length == 7 }?.let { Color(0xFF000000 or it) } ?: Color(0xFF999B94)

internal val WardrobePalette = listOf(
    "#FFFFFF" to "Trắng", "#000000" to "Đen", "#808080" to "Xám", "#D8C3A5" to "Be",
    "#6B4226" to "Nâu", "#3B5B8C" to "Xanh dương", "#B23A30" to "Đỏ",
    "#228B22" to "Xanh lá", "#E8BF50" to "Vàng", "#D29DA9" to "Hồng",
)

private val GarmentColorNames = WardrobePalette + listOf(
    "#B9CDE5" to "Xanh nhạt", "#6E8DB8" to "Xanh denim", "#2F4F6F" to "Xanh đậm",
    "#5E5E62" to "Xám đậm", "#C0C0C0" to "Xám nhạt", "#FFF5D9" to "Kem",
    "#E78A3F" to "Cam", "#84619B" to "Tím", "#A7C4A0" to "Xanh lá nhạt",
)

internal fun colorName(hex: String): String {
    val value = garmentColor(hex)
    return GarmentColorNames.minByOrNull { (color, _) ->
        val other = garmentColor(color)
        val r = value.red - other.red
        val g = value.green - other.green
        val b = value.blue - other.blue
        r * r + g * g + b * b
    }?.second ?: "Màu khác"
}

@Composable
internal fun ColorDot(hex: String, modifier: Modifier = Modifier, selected: Boolean = false) {
    val color = garmentColor(hex)
    Box(modifier.size(22.dp).clip(CircleShape).background(color)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f), CircleShape),
        contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp),
            tint = if (color.luminance() > .45f) Color(0xFF252822) else Color.White)
    }
}

@Composable
internal fun ChoiceChip(
    label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected, onClick, { Text(label) }, modifier.heightIn(min = 40.dp),
        enabled = enabled, shape = RoundedCornerShape(50),
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled, selected, borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun WardrobePanel(
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier, shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun SectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun GarmentPhoto(path: String?, description: String?, modifier: Modifier = Modifier) {
    var failed by remember(path) { mutableStateOf(false) }
    val placeholder = path == null || path.startsWith("seed://") || failed
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        if (placeholder) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (failed) Icons.Outlined.ImageNotSupported else Icons.Outlined.Checkroom, null,
                    Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
                if (failed) Text("Ảnh chưa khả dụng", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else AsyncImage(
            File(requireNotNull(path)), description, Modifier.fillMaxSize().padding(12.dp),
            contentScale = ContentScale.Fit, onError = { failed = true },
        )
    }
}

@Composable
internal fun PrimaryAction(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, busy: Boolean = false, icon: ImageVector? = null,
) {
    Button(onClick, modifier.heightIn(min = 54.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun ActionFooter(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
internal fun InfoNotice(text: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(14.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, Modifier.size(18.dp),
                tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
            Text(text, style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
internal fun ErrorNotice(text: String) = InfoNotice(text, error = true)
