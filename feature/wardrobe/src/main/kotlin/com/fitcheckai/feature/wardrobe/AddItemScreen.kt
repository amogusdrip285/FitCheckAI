package com.fitcheckai.feature.wardrobe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.imaging.WardrobeCamera
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AddItemScreen(onBack: () -> Unit, viewModel: AddItemViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.selectPhoto(it.toString()) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted; showCamera = granted
    }
    val choosePhoto: () -> Unit = {
        focus.clearFocus()
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val openCamera: () -> Unit = {
        focus.clearFocus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraDenied = false; showCamera = true
        } else permission.launch(Manifest.permission.CAMERA)
    }
    val back: () -> Unit = {
        focus.clearFocus()
        when {
            showCamera -> showCamera = false
            state.stage == AddStage.SAVING -> Unit
            state.originalPath != null || state.processing -> confirmDiscard = true
            else -> onBack()
        }
    }
    BackHandler { back() }
    LaunchedEffect(state.savedId) { if (state.savedId != null) onBack() }
    WardrobeTheme {
        Scaffold(
            modifier = Modifier.imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { BackTopBar(if (showCamera) "Chụp món đồ" else "Thêm món đồ", back, state.stage != AddStage.SAVING) },
            bottomBar = {
                if (!showCamera && state.stage in setOf(AddStage.EDITING, AddStage.SAVING)) ActionFooter {
                    state.error?.let { ErrorNotice(it) }
                    PrimaryAction(if (state.stage == AddStage.SAVING) "Đang lưu…" else "Lưu vào tủ đồ",
                        { focus.clearFocus(); viewModel.save() }, Modifier.fillMaxWidth(),
                        enabled = state.stage == AddStage.EDITING, busy = state.stage == AddStage.SAVING,
                        icon = Icons.Outlined.Check)
                }
            },
        ) { padding ->
            if (showCamera) CameraCapture(Modifier.padding(padding), viewModel::createCaptureFile, { path ->
                showCamera = false; viewModel.selectPhoto(path, captured = true)
            }, { showCamera = false })
            else Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                AddProgress(state.stage)
                if (state.originalPath == null && !state.processing) ChoosePhotoContent(openCamera, choosePhoto)
                state.imagePath?.let { path ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GarmentPhoto(path, "Ảnh món đồ", Modifier.fillMaxWidth().height(236.dp))
                        if (!state.processing) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text(if (state.segmentedPath != null && !state.keepOriginal) "Đã tách nền" else "Ảnh gốc",
                                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                Spacer(Modifier.weight(1f))
                                ChangePhotoMenu(openCamera, choosePhoto, state.stage == AddStage.EDITING)
                            }
                        }
                    }
                }
                if (state.processing) WardrobePanel(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(when (state.stage) {
                                AddStage.IMPORTING -> "Đang chuẩn bị ảnh…"
                                AddStage.SEGMENTING -> "Đang tách nền…"
                                else -> "Đang gợi ý thông tin…"
                            }, style = MaterialTheme.typography.titleMedium)
                            Text("Bạn có thể chỉnh lại mọi thông tin trước khi lưu.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
                    if (state.stage == AddStage.TAGGING) TextButton(viewModel::skipTagging) { Text("Tự điền ngay") }
                }
                if (cameraDenied) {
                    InfoNotice("Chưa có quyền camera. Bạn vẫn có thể chọn ảnh từ thư viện.")
                    TextButton({
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text("Mở cài đặt quyền") }
                }
                state.notices.forEach { InfoNotice(it) }
                if (state.stage == AddStage.EDITING || state.stage == AddStage.SAVING) {
                    if (state.segmentedPath != null) WardrobePanel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Giữ ảnh gốc", style = MaterialTheme.typography.titleSmall)
                                Text("Dùng khi nền tách chưa như ý.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(state.keepOriginal, viewModel::keepOriginal, enabled = state.stage == AddStage.EDITING)
                        }
                    }
                    AttributeEditor(state.form, viewModel::edit, enabled = state.stage == AddStage.EDITING)
                } else state.error?.let { ErrorNotice(it) }
            }
        }
        if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
            title = { Text("Bỏ bản nháp?") }, text = { Text("Ảnh và thông tin chưa lưu sẽ được bỏ.") },
            confirmButton = { TextButton({ confirmDiscard = false; viewModel.discard(onBack) }) { Text("Bỏ bản nháp") } },
            dismissButton = { TextButton({ confirmDiscard = false }) { Text("Tiếp tục sửa") } })
    }
}

@Composable
private fun AddProgress(stage: AddStage) {
    val current = when (stage) { AddStage.CHOOSE -> 0; AddStage.IMPORTING, AddStage.SEGMENTING, AddStage.TAGGING -> 1; else -> 2 }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Ảnh", "Xử lý", "Thông tin").forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(shape = CircleShape,
                    color = when { index < current -> MaterialTheme.colorScheme.tertiaryContainer
                        index == current -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant },
                    contentColor = when { index < current -> MaterialTheme.colorScheme.onTertiaryContainer
                        index == current -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant }) {
                    Box(Modifier.size(24.dp), Alignment.Center) {
                        if (index < current) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp))
                        else Text((index + 1).toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = if (index == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index < 2) HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun ChoosePhotoContent(onCamera: () -> Unit, onPicker: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.fillMaxWidth().height(164.dp), Alignment.Center) {
            Surface(Modifier.size(134.dp).offset(x = (-34).dp, y = 5.dp).rotate(-12f),
                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer) {}
            Surface(Modifier.size(136.dp).offset(x = 12.dp).rotate(7f), shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Checkroom, null, Modifier.size(62.dp), tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Surface(Modifier.offset(x = 79.dp, y = 60.dp), shape = CircleShape,
                color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, null, Modifier.padding(10.dp).size(21.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Thêm món đồ\nbạn yêu thích.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text("Chụp hoặc chọn một bức ảnh.\nThông tin có thể chỉnh lại trước khi lưu.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryAction("Chụp ảnh", onCamera, Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera)
            OutlinedButton(onPicker, Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text("Chọn ảnh")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LightMode, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text("Một món đồ · Nền đơn giản · Đủ sáng", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChangePhotoMenu(onCamera: () -> Unit, onPicker: () -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton({ expanded = true }, enabled = enabled) {
            Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Đổi ảnh")
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text("Chụp ảnh") }, { expanded = false; onCamera() },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) })
            DropdownMenuItem({ Text("Chọn ảnh") }, { expanded = false; onPicker() },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) })
        }
    }
}

@Composable
internal fun CameraCapture(modifier: Modifier, createFile: () -> File, onCaptured: (String) -> Unit, onChoosePhoto: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val camera = remember(context) { WardrobeCamera(context) }
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(camera, owner) {
        try {
            check(withTimeoutOrNull(10_000) { camera.bind(owner); true } == true)
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Không mở được camera. Hãy chọn ảnh từ thư viện." }
    }
    DisposableEffect(camera) { onDispose { camera.close() } }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AndroidView({ camera.previewView }, Modifier.fillMaxWidth().weight(1f)
            .clip(RoundedCornerShape(24.dp)).clipToBounds())
        Text("Chụp trọn món đồ và giữ máy ổn định.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!ready && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { ErrorNotice(it) }
        PrimaryAction(if (busy) "Đang chụp…" else "Chụp món đồ", {
            busy = true
            scope.launch {
                try { onCaptured(checkNotNull(withTimeoutOrNull(10_000) { camera.capture(createFile()) })) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "Chưa chụp được ảnh. Hãy thử lại." }
                finally { busy = false }
            }
        }, Modifier.fillMaxWidth(), enabled = ready && !busy, busy = busy, icon = Icons.Outlined.PhotoCamera)
        TextButton(onChoosePhoto, Modifier.align(Alignment.CenterHorizontally), enabled = !busy) { Text("Quay lại chọn ảnh") }
    }
}
