package com.fitcheckai.feature.wardrobe.v2

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.model.wardrobe.*
import com.fitcheckai.feature.wardrobe.*
import java.io.File
import java.util.UUID

@Composable
fun WardrobeEditorScreen(onBack: () -> Unit, onStyleAround: ((String) -> Unit)? = null,
    viewModel: WardrobeEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var editing by rememberSaveable { mutableStateOf(state.isNew) }
    var selectedView by rememberSaveable { mutableStateOf(ImageView.FRONT) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var consent by remember { mutableStateOf<Pair<RecognitionCapability, Map<String, ImageView>>?>(null) }
    val locked = state.busy || state.loading || state.importingView != null
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.chooseImage(uri.toString(), selectedView)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showCamera = granted; cameraDenied = !granted
    }
    val pick: (ImageView) -> Unit = { view ->
        selectedView = view; focus.clearFocus()
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val camera: (ImageView) -> Unit = { view ->
        selectedView = view; focus.clearFocus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCamera = true; cameraDenied = false
        } else permission.launch(Manifest.permission.CAMERA)
    }
    val back: () -> Unit = {
        focus.clearFocus()
        when {
            showCamera -> showCamera = false
            locked -> Unit
            editing -> discard = true
            else -> viewModel.discard(onBack)
        }
    }
    BackHandler { back() }
    LaunchedEffect(state.saved, state.deleted) { if (state.saved || state.deleted) onBack() }
    WardrobeTheme {
        Scaffold(modifier = Modifier.imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { BackTopBar(if (showCamera) "Chụp ${selectedView.label.lowercase()}" else if (state.isNew) "Thêm món đồ" else "Chi tiết món đồ", back, !locked) },
            bottomBar = {
                if (!showCamera && !state.loading) ActionFooter {
                    state.error?.let { ErrorNotice(it) }
                    if (!state.isNew && state.expectedRevision == null) TextButton(viewModel::reload, enabled = !locked) { Text("Thử tải lại") }
                    else if (editing) PrimaryAction(if (state.busy) "Đang lưu…" else "Lưu vào tủ đồ",
                        { focus.clearFocus(); viewModel.save() }, Modifier.fillMaxWidth(), enabled = !locked,
                        busy = state.busy, icon = Icons.Outlined.Check)
                    else PrimaryAction("Chỉnh sửa món đồ", { editing = true }, Modifier.fillMaxWidth(), enabled = !locked, icon = Icons.Outlined.Edit)
                }
            }) { padding ->
            if (showCamera) CameraCapture(Modifier.padding(padding), {
                File(context.cacheDir, "wardrobe-captures").apply { mkdirs() }.let { File(it, "${UUID.randomUUID()}.jpg") }
            }, { path ->
                showCamera = false
                viewModel.chooseImage(path, selectedView) { File(path).delete() }
            }, { showCamera = false; pick(selectedView) })
            else if (state.loading) Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            else Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (state.isNew) SectionHeading("Giữ lại món đồ bạn yêu thích", "Ảnh mặt trước là bắt buộc. Mặt sau giúp lưu đủ chi tiết của cùng món đồ.")
                else Text(state.item.name, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImageView.entries.forEach { view ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(view.label, style = MaterialTheme.typography.titleSmall)
                            GarmentPhoto(if (view == ImageView.FRONT) state.frontPath else state.backPath,
                                "Ảnh ${view.label.lowercase()}", Modifier.fillMaxWidth().height(190.dp))
                            if (editing) {
                                OutlinedButton({ pick(view) }, Modifier.fillMaxWidth(), enabled = !locked) {
                                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                    Text(if (state.item.images[view] == null) "Chọn ảnh" else "Thay ảnh")
                                }
                                TextButton({ camera(view) }, Modifier.fillMaxWidth(), enabled = !locked) {
                                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Chụp ảnh")
                                }
                                if (view == ImageView.BACK && state.item.images.back != null) TextButton(viewModel::removeBack, enabled = !locked) { Text("Bỏ mặt sau") }
                            } else if (state.item.images[view] == null) Text("Chưa có ảnh", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (state.importingView != null) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Đang chuẩn bị ảnh ${state.importingView!!.label.lowercase()}…") }
                if (cameraDenied) InfoNotice("Chưa có quyền camera. Bạn vẫn có thể chọn ảnh từ thư viện hoặc cấp quyền trong cài đặt Android.")
                state.notice?.let { InfoNotice(it) }
                if (state.fakeResult) InfoNotice("FAKE · Kết quả giả lập dùng kiểm thử, không phải AI thực tế.")
                if (editing) {
                    if (state.item.images.front != null) SegmentationPreview(state.item.images, !locked)
                    WardrobePanel {
                        SectionHeading("Thông tin món đồ", "Chưa biết thuộc tính nào thì để trống. Bạn có thể bổ sung sau.")
                        if (viewModel.capability.available) {
                            OutlinedButton({ consent = viewModel.capability to state.item.images.refs().associate { it.id to it.view } }, enabled = !locked && !state.recognizing && state.item.images.front != null) {
                                Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Gợi ý bằng AI")
                            }
                        } else InfoNotice("Nhận diện AI chưa được kết nối. Nhập thông tin bên dưới để lưu món đồ.")
                        if (state.recognizing) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(viewModel::cancelRecognition) { Text("Dừng nhận diện, tự điền") } }
                        WardrobeFields(state.item, !locked, viewModel::editName, viewModel::editAttributes)
                    }
                    if (state.item.attributes.needsReviewFields.isNotEmpty()) {
                        InfoNotice("Một số nhãn cần kiểm tra. Bạn có thể sửa từng trường hoặc xác nhận sau khi xem lại.")
                        TextButton(viewModel::acknowledgeReview, enabled = !locked) { Text("Đã kiểm tra các nhãn") }
                    }
                    if (!state.isNew && state.error != null) TextButton(viewModel::reload, enabled = !locked) { Text("Bỏ bản sửa và tải lại") }
                } else {
                    AttributeSummary(state.item.attributes)
                    if (onStyleAround != null) OutlinedButton({ onStyleAround(state.item.id) }, Modifier.fillMaxWidth(),
                        enabled = state.item.attributes.category != Category.UNKNOWN) { Text("Phối đồ cùng món này") }
                    TextButton({ delete = true }, enabled = !locked, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Xóa món đồ")
                    }
                }
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Bỏ thay đổi chưa lưu?") },
            text = { Text("Ảnh vừa chọn sẽ được dọn. Món đồ đã lưu vẫn được giữ nguyên.") },
            confirmButton = { TextButton({ discard = false; viewModel.discard(onBack) }) { Text("Bỏ thay đổi") } },
            dismissButton = { TextButton({ discard = false }) { Text("Tiếp tục sửa") } })
        if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Xóa món đồ này?") },
            text = { Text("Ảnh mặt trước, mặt sau và các ảnh dẫn xuất của món đồ sẽ được xóa.") },
            confirmButton = { TextButton({ delete = false; viewModel.delete() }) { Text("Xóa món đồ") } },
            dismissButton = { TextButton({ delete = false }) { Text("Giữ lại") } })
        consent?.let { preview -> AlertDialog(onDismissRequest = { consent = null }, title = { Text("Gửi ảnh để nhận diện?") },
            text = { Text("Gửi ${preview.second.size} ảnh (${preview.second.values.joinToString { it.label.lowercase() }}) tới ${preview.first.provider} cho lần này. Chỉ tiếp tục nếu cả hai ảnh là cùng một món đồ.") },
            confirmButton = { TextButton({ consent = null; viewModel.recognize(preview.first, preview.second) }) { Text("Đồng ý gửi lần này") } },
            dismissButton = { TextButton({ consent = null }) { Text("Tự điền") } }) }
    }
}

internal val ImageView.label get() = if (this == ImageView.FRONT) "Mặt trước" else "Mặt sau"
