package com.fitcheckai.feature.wardrobe.v2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.feature.wardrobe.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class DatasetState(val count: Int = 0, val pairs: Int = 0, val busy: Boolean = false,
    val loaded: Boolean = false, val progress: Int = 0, val message: String? = null, val credits: List<String> = emptyList())

@HiltViewModel
class DatasetViewModel @Inject constructor(private val importer: MockWardrobeImporter) : ViewModel() {
    private val mutable = MutableStateFlow(DatasetState())
    val state = mutable.asStateFlow()
    private var items: List<JsonObject> = emptyList()
    init { viewModelScope.launch {
        if (!importer.isEnabled) return@launch
        runCatching { importer.loadManifest() }.fold({ rows ->
            items = rows
            val credits = items.flatMap { listOfNotNull(it["front"] as? JsonObject, it["back"] as? JsonObject) }
                .map { "${it.text("attribution")} · ${it.text("license")}" }.distinct()
            mutable.value = DatasetState(items.size, items.count { it["pairVerified"]?.jsonPrimitive?.boolean == true }, loaded = true, credits = credits)
        }, { mutable.value = DatasetState(message = "Không đọc được bộ ảnh kiểm thử trong APK.") })
    } }

    fun importDataset() {
        if (!importer.isEnabled || state.value.busy || !state.value.loaded) return
        mutable.value = state.value.copy(busy = true, progress = 0, message = null)
        viewModelScope.launch {
            try {
                val (added, skipped, failed) = importer.import(items) { done -> mutable.value = state.value.copy(progress = done) }
                mutable.value = state.value.copy(message = "Đã thêm $added món · Bỏ qua $skipped món đã có" + if (failed > 0) " · $failed món chưa nhập được (có thể đã bị xóa trước đó)." else ".")
            } finally { mutable.value = state.value.copy(busy = false) }
        }
    }
}

@Composable
fun DatasetScreen(onBack: () -> Unit, viewModel: DatasetViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.busy) { /* Finish a short local import before leaving this screen. */ }
    WardrobeTheme {
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { BackTopBar("Bộ ảnh kiểm thử", onBack, !state.busy) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SectionHeading("Quần áo đời thường", "${state.count} món · ${state.pairs} cặp trước/sau đã kiểm tra")
                InfoNotice("Đây là dữ liệu kiểm thử. Nhãn ảnh được rà soát từ ảnh nguồn, chưa phải bộ chuẩn được người độc lập xác nhận; không chứng minh độ chính xác AI.")
                Text("Nhập ảnh vào tủ đồ để thử tìm kiếm, chỉnh sửa, thay ảnh và xóa. Mỗi món có ID ổn định; nhập lại sẽ bỏ qua món đã có.")
                PrimaryAction(if (state.busy) "Đang nhập ${state.progress}/${state.count}…" else "Thêm bộ ảnh vào tủ đồ",
                    viewModel::importDataset, Modifier.fillMaxWidth(), enabled = state.loaded && !state.busy, busy = state.busy)
                state.message?.let { InfoNotice(it) }
                SectionHeading("Nguồn và ghi công")
                state.credits.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("Nguồn, liên kết giấy phép, hash ảnh và thay đổi xử lý được giữ trong manifest đi kèm. Bộ ảnh thử chỉ có khi bật FITCHECK_MOCK_WARDROBE trong .env.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
