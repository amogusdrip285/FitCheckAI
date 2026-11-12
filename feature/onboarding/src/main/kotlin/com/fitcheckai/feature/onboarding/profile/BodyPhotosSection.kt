package com.fitcheckai.feature.onboarding.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.ui.theme.Spacing
import java.io.File

/**
 * D-005. Front and back are picked independently; a missing back is shown as missing, never cloned.
 * Import stores the photo locally only — upload consent for try-on is asked per job by Track C.
 */
@Composable
fun BodyPhotosSection(
    photos: Map<ImageView, BodyPhotoUi>,
    busy: Boolean,
    onImport: (ImageView, String) -> Unit,
    onRemove: (ImageView) -> Unit,
) {
    var pendingView by rememberSaveable { mutableStateOf<ImageView?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf<ImageView?>(null) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val view = pendingView
        pendingView = null
        if (uri != null && view != null) onImport(view, uri.toString()) // cancel = no change
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Chỉ dùng khi bạn chủ động thử đồ ảo. Ảnh lưu riêng trên máy, không sao lưu lên đám mây; " +
                "mỗi lần thử đồ sẽ hỏi lại trước khi gửi ảnh.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ImageView.entries.forEach { view ->
                PhotoSlot(
                    view = view,
                    photo = photos[view],
                    busy = busy,
                    onPick = {
                        pendingView = view
                        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    },
                    onRemove = { confirmRemove = view },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text("Mẹo: đứng thẳng, toàn thân trong khung, nền đơn giản, đủ sáng.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    confirmRemove?.let { view ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = null },
            text = { Text("Xóa ảnh ${view.label()}? Các kết quả thử đồ dùng ảnh này cũng sẽ bị hủy.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; onRemove(view) }) { Text("Xóa") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun PhotoSlot(
    view: ImageView,
    photo: BodyPhotoUi?,
    busy: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.testTag("photo-$view"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(view.label().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.66f).clip(RoundedCornerShape(12))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val path = photo?.displayPath
            when {
                path != null -> AsyncImage(model = File(path), contentDescription = "Ảnh ${view.label()}",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                photo != null -> Text("Không tìm thấy tệp ảnh", style = MaterialTheme.typography.bodySmall)
                else -> Text("Chưa có ảnh", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onPick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (photo == null) "Chọn ảnh" else "Thay ảnh")
        }
        if (photo != null) {
            TextButton(onClick = onRemove, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Xóa ảnh") }
        }
    }
}

internal fun ImageView.label() = if (this == ImageView.FRONT) "mặt trước" else "mặt sau"
