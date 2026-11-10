package com.fitcheckai.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.ui.theme.Spacing
import java.io.File

private const val SEED_PREFIX = "seed://"

/** Ảnh một món đồ; dữ liệu mẫu (`seed://`) hoặc ảnh lỗi hiển thị ô màu chủ đạo. */
@Composable
fun ItemThumbnail(item: ClothingItem, modifier: Modifier = Modifier) {
    val swatch = item.attributes.colors.firstOrNull()?.let(::parseHexColor)
        ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(swatch),
        contentAlignment = Alignment.Center,
    ) {
        if (item.imagePath.startsWith(SEED_PREFIX)) {
            Text(
                text = item.attributes.subCategory,
                style = MaterialTheme.typography.labelLarge,
                color = readableOn(swatch),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.sm),
            )
        } else {
            AsyncImage(
                model = File(item.imagePath),
                contentDescription = item.attributes.subCategory,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(Spacing.sm),
            )
        }
    }
}

internal fun parseHexColor(hex: String): Color? =
    hex.removePrefix("#").toLongOrNull(radix = 16)
        ?.takeIf { hex.length == 7 }
        ?.let { Color(0xFF000000 or it) }

private fun readableOn(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color(0xFF1E1B18) else Color.White
}
