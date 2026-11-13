package com.fitcheckai.feature.onboarding.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitcheckai.core.model.profile.STYLE_OPTIONS
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.ui.component.ScreenHeader
import com.fitcheckai.core.ui.theme.Spacing

enum class ProfileMode { ONBOARDING, EDIT }

@Composable
fun ProfileRoute(mode: ProfileMode, onDone: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(
        mode = mode,
        state = state,
        actions = ProfileActions(
            onHeight = viewModel::setHeight,
            onWeight = viewModel::setWeight,
            onToggleStyle = viewModel::toggleStyle,
            onToggleCategory = viewModel::toggleCategory,
            onSave = { viewModel.save(if (mode == ProfileMode.EDIT) ({ viewModel.notify("Đã lưu hồ sơ.") }) else onDone) },
            onSkip = { viewModel.skip(onDone) },
            onReset = viewModel::reset,
            onImportPhoto = viewModel::importPhoto,
            onRemovePhoto = viewModel::removePhoto,
            onDismissMessage = viewModel::dismissMessage,
        ),
    )
}

data class ProfileActions(
    val onHeight: (String) -> Unit = {},
    val onWeight: (String) -> Unit = {},
    val onToggleStyle: (String) -> Unit = {},
    val onToggleCategory: (Category) -> Unit = {},
    val onSave: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onImportPhoto: (ImageView, String) -> Unit = { _, _ -> },
    val onRemovePhoto: (ImageView) -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

@Composable
fun ProfileScreen(mode: ProfileMode, state: ProfileUiState, actions: ProfileActions) {
    when (val load = state.load) {
        ProfileLoad.Loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
        is ProfileLoad.Failed -> LoadFailed(load.message, state.busy, actions.onReset)
        ProfileLoad.Ready -> ProfileContent(mode, state, actions)
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = actions.onDismissMessage,
            confirmButton = { TextButton(onClick = actions.onDismissMessage) { Text("OK") } },
            text = { Text(message) },
        )
    }
}

@Composable
private fun LoadFailed(message: String, busy: Boolean, onReset: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ScreenHeader(eyebrow = "Hồ sơ", title = "Không đọc được hồ sơ")
        Text(message.ifBlank { "Dữ liệu hồ sơ trên máy bị lỗi." })
        Text("Tủ đồ và gợi ý phối đồ vẫn dùng được. Bạn có thể đặt lại hồ sơ để bắt đầu lại.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { confirm = true }, enabled = !busy) { Text("Đặt lại hồ sơ") }
    }
    if (confirm) ResetDialog(onConfirm = { confirm = false; onReset() }, onDismiss = { confirm = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(mode: ProfileMode, state: ProfileUiState, actions: ProfileActions) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg)) {
        if (mode == ProfileMode.ONBOARDING) {
            ScreenHeader(eyebrow = "FitCheck AI", title = "Phối từ đồ bạn đã có")
            Text(
                "Mọi thông tin dưới đây đều không bắt buộc — bạn có thể bỏ qua và dùng ngay. " +
                    "Hồ sơ chỉ lưu trên máy này, không tạo tài khoản và không tự gửi lên mạng.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        } else {
            ScreenHeader(eyebrow = "Hồ sơ", title = "Sở thích của bạn")
        }

        Section("Phong cách yêu thích", "Chọn tối đa 5 — dùng làm mặc định khi gợi ý.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                STYLE_OPTIONS.forEach { style ->
                    FilterChip(selected = style in form.styles, onClick = { actions.onToggleStyle(style) }, label = { Text(style) })
                }
            }
        }

        Section("Loại đồ hay mặc", null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CATEGORY_OPTIONS.forEach { (category, label) ->
                    FilterChip(selected = category in form.categories, onClick = { actions.onToggleCategory(category) },
                        label = { Text(label) })
                }
            }
        }

        Section("Số đo (tùy chọn)", "Không dùng để chẩn đoán vóc dáng hay chọn size. Cân nặng không được gửi khi gợi ý phối đồ.") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField("Chiều cao (cm)", form.heightText, state.fieldErrors[FormField.HEIGHT], actions.onHeight,
                    Modifier.weight(1f).testTag("height"))
                NumberField("Cân nặng (kg)", form.weightText, state.fieldErrors[FormField.WEIGHT], actions.onWeight,
                    Modifier.weight(1f).testTag("weight"))
            }
        }

        if (mode == ProfileMode.EDIT) {
            Section("Ảnh toàn thân (tùy chọn)", null) {
                BodyPhotosSection(state.photos, state.busy, actions.onImportPhoto, actions.onRemovePhoto)
            }
        } else {
            Text("Bạn có thể thêm ảnh toàn thân sau trong mục Hồ sơ — không cần ảnh để phối đồ.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md))
        }

        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = actions.onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("save")) {
                Text(if (mode == ProfileMode.ONBOARDING) "Lưu và bắt đầu" else "Lưu hồ sơ")
            }
            if (mode == ProfileMode.ONBOARDING) {
                TextButton(onClick = actions.onSkip, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("skip")) {
                    Text("Bỏ qua")
                }
            } else if (state.hasProfile) {
                TextButton(onClick = { confirmReset = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Xóa hồ sơ và ảnh cá nhân", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmReset) ResetDialog(onConfirm = { confirmReset = false; actions.onReset() }, onDismiss = { confirmReset = false })
}

@Composable
private fun Section(title: String, hint: String?, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}

@Composable
private fun NumberField(label: String, value: String, error: String?, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.padding(top = 2.dp),
    )
}

@Composable
internal fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xóa hồ sơ?") },
        text = { Text("Xóa sở thích, số đo và ảnh toàn thân trên máy. Tủ đồ của bạn được giữ nguyên.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Xóa", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
