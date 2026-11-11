package com.fitcheckai.feature.wardrobe.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.imaging.LocalMediaStore
import com.fitcheckai.core.imaging.MlKitSegmenter
import com.fitcheckai.core.imaging.SegmentationReadiness
import com.fitcheckai.core.model.wardrobe.*
import com.fitcheckai.feature.wardrobe.GarmentPhoto
import com.fitcheckai.feature.wardrobe.InfoNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CutoutState(val originalId: String? = null, val busy: Boolean = false,
    val readiness: SegmentationReadiness? = null, val path: String? = null, val message: String? = null)

@HiltViewModel
class SegmentationPreviewModel @Inject constructor(private val segmenter: MlKitSegmenter,
    private val media: LocalMediaStore) : ViewModel() {
    private val mutable = MutableStateFlow(CutoutState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun reset() { job?.cancel(); mutable.value = CutoutState() }
    fun cancel() { job?.cancel(); mutable.value = CutoutState(message = "Đã bỏ qua tách nền; ảnh gốc được giữ nguyên.") }
    fun run(original: MediaRef) {
        if (state.value.busy) return
        mutable.value = CutoutState(original.id, busy = true)
        job = viewModelScope.launch {
            try {
                val readiness = segmenter.readiness(requestDownload = true)
                mutable.value = state.value.copy(readiness = readiness)
                if (readiness != SegmentationReadiness.READY) {
                    mutable.value = state.value.copy(busy = false, message = if (readiness == SegmentationReadiness.DOWNLOADING)
                        "Model đang tải. Bạn có thể thử lại sau hoặc tiếp tục với ảnh gốc." else "Tách nền chưa sẵn sàng trên thiết bị. Tiếp tục dùng ảnh gốc.")
                    return@launch
                }
                media.read(original).getOrThrow()
                val originalPath = media.displayPath(original) ?: error("Missing source")
                val temporary = segmenter.removeBackground(originalPath).getOrThrow()
                val result = try { media.importSegmented(temporary, original).getOrThrow() }
                    finally { withContext(Dispatchers.IO) { File(temporary).delete() } }
                val path = withContext(Dispatchers.IO) { media.displayPath(result) }
                mutable.value = CutoutState(original.id, readiness = readiness, path = path,
                    message = "Bản tách nền để xem trước. Kiểm tra viền và chi tiết; ảnh gốc vẫn được giữ riêng.")
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { mutable.value = CutoutState(original.id, message = "Chưa tách được món đồ rõ ràng. Ảnh gốc vẫn được giữ nguyên.") }
        }
    }
}

@Composable
internal fun SegmentationPreview(images: ImagePair, enabled: Boolean, model: SegmentationPreviewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(images) { model.reset() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tách nền · tùy chọn", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            images.refs().forEach { ref -> OutlinedButton({ model.run(ref) }, enabled = enabled && !state.busy) { Text(ref.view.label) } }
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Đang kiểm tra model và chuẩn bị ảnh…", style = MaterialTheme.typography.bodySmall)
            TextButton(model::cancel) { Text("Bỏ qua, dùng ảnh gốc") }
        }
        if (state.originalId == null || images.refs().any { it.id == state.originalId }) {
            state.message?.let { InfoNotice(it) }
            state.path?.let { GarmentPhoto(it, "Bản xem trước đã tách nền", Modifier.fillMaxWidth().height(230.dp)) }
        }
    }
}
