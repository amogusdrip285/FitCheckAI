package com.fitcheckai.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun GarmentPhoto(path: String?, description: String?, modifier: Modifier = Modifier) {
    var failed by remember(path) { mutableStateOf(false) }
    val placeholder = path == null || path.startsWith("seed://") || failed
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (placeholder) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (failed) Icons.Outlined.ImageNotSupported else Icons.Outlined.Checkroom, null,
                    Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                )
                if (failed) Text("Ảnh chưa khả dụng", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else AsyncImage(
            File(requireNotNull(path)), description, Modifier.fillMaxSize().padding(12.dp),
            contentScale = ContentScale.Fit, onError = { failed = true },
        )
    }
}
