package com.fitcheckai.feature.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.MetricEvent
import com.fitcheckai.core.model.MetricNames
import com.fitcheckai.core.model.MetricsTracker
import com.fitcheckai.core.model.WardrobeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class AddStage { CHOOSE, IMPORTING, SEGMENTING, TAGGING, EDITING, SAVING, SAVED }

data class AddItemState(
    val stage: AddStage = AddStage.CHOOSE,
    val originalPath: String? = null,
    val segmentedPath: String? = null,
    val keepOriginal: Boolean = false,
    val form: AttributeForm = AttributeForm(),
    val notices: List<String> = emptyList(),
    val error: String? = null,
    val savedId: String? = null,
) {
    val imagePath: String? get() = if (keepOriginal) originalPath else segmentedPath ?: originalPath
    val processing: Boolean get() = stage in setOf(AddStage.IMPORTING, AddStage.SEGMENTING, AddStage.TAGGING)
}

@HiltViewModel
class AddItemViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val wardrobe: WardrobeRepository,
    private val photos: PhotoProcessor,
    private val metrics: MetricsTracker,
) : ViewModel() {
    private val restoredPath: String? = savedState["original"]
    private val restoredSavedId: String? = savedState["savedId"]
    private val _state = MutableStateFlow(AddItemState(
        stage = when { restoredSavedId != null -> AddStage.SAVED; restoredPath != null -> AddStage.EDITING; else -> AddStage.CHOOSE },
        originalPath = restoredPath, segmentedPath = savedState["segmented"],
        keepOriginal = savedState["keepOriginal"] ?: false,
        form = AttributeForm.restore(savedState["form"]), savedId = restoredSavedId,
        notices = if (restoredPath != null && restoredSavedId == null) listOf("Đã khôi phục bản nháp. Kiểm tra thông tin trước khi lưu.") else emptyList(),
    ))
    val state = _state.asStateFlow()
    private var work: Job? = null
    private var predicted: ClothingAttributes? = savedState.get<ArrayList<String>>("predicted")
        ?.let { AttributeForm.restore(it).attributes().getOrNull() }
    private var startedAt = savedState.get<Long>("startedAt") ?: System.currentTimeMillis()

    fun createCaptureFile() = photos.createCaptureFile()

    fun selectPhoto(uri: String, captured: Boolean = false) {
        if (_state.value.processing || _state.value.stage in setOf(AddStage.SAVING, AddStage.SAVED)) return
        val previousPaths = listOfNotNull(_state.value.originalPath, _state.value.segmentedPath)
        startedAt = System.currentTimeMillis()
        savedState["startedAt"] = startedAt
        predicted = null
        savedState.remove<ArrayList<String>>("predicted")
        _state.value = AddItemState(stage = AddStage.IMPORTING)
        persist()
        work = viewModelScope.launch {
            try {
                photos.discard(previousPaths)
                val original = photos.importSource(uri)
                _state.update { it.copy(originalPath = original, stage = AddStage.SEGMENTING) }
                persist()
                val segmented = withTimeoutOrNull(8_000) { photos.segment(original) }
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(
                    segmentedPath = segmented?.getOrNull(), stage = AddStage.TAGGING,
                    notices = if (segmented?.isSuccess == true) emptyList() else listOf(
                        "Chưa tách được nền. Đang giữ ảnh gốc; thử chụp món đồ trên nền phẳng, đủ sáng.",
                    ),
                ) }
                persist()
                val tagged = withTimeoutOrNull(8_000) { photos.tag(requireNotNull(_state.value.imagePath)) }
                currentCoroutineContext().ensureActive()
                predicted = tagged?.getOrNull()
                predicted?.let { savedState["predicted"] = AttributeForm.from(it).savedValues() }
                _state.update { it.copy(stage = AddStage.EDITING,
                    form = predicted?.let(AttributeForm::from) ?: AttributeForm(),
                    notices = it.notices + if (predicted == null) listOf("AI chưa gắn nhãn được. Bạn vẫn có thể chọn nhanh và lưu thủ công.") else emptyList(),
                ) }
                persist()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(stage = if (it.originalPath == null) AddStage.CHOOSE else AddStage.EDITING,
                    error = "Không xử lý được ảnh. Hãy thử ảnh khác hoặc nhập thông tin thủ công.") }
            } finally {
                if (captured) withContext(NonCancellable) { photos.discard(listOf(uri)) }
            }
        }
    }

    fun skipTagging() {
        if (_state.value.stage != AddStage.TAGGING) return
        work?.cancel()
        _state.update { it.copy(stage = AddStage.EDITING, notices = it.notices + "Bạn có thể điền thông tin thủ công.") }
        persist()
    }

    fun edit(form: AttributeForm) {
        if (_state.value.stage != AddStage.EDITING) return
        _state.update { it.copy(form = form, error = null) }
        persist()
    }

    fun keepOriginal(value: Boolean) {
        if (_state.value.stage != AddStage.EDITING) return
        _state.update { it.copy(keepOriginal = value) }
        persist()
    }

    fun save() {
        val current = _state.value
        if (current.stage != AddStage.EDITING) return
        val attributes = current.form.attributes().getOrElse { error ->
            _state.update { it.copy(error = error.message) }
            return
        }
        val path = current.imagePath ?: return
        _state.update { it.copy(stage = AddStage.SAVING, error = null) }
        work = viewModelScope.launch {
            val result = wardrobe.save(path, attributes)
            result.fold(onSuccess = { item ->
                savedState["savedId"] = item.id
                metrics.track(MetricEvent(MetricNames.ITEM_ADDED, mapOf(
                    "duration_ms" to (System.currentTimeMillis() - startedAt).coerceAtLeast(0).toString(),
                    "tag_source" to if (predicted == null) "manual" else "ai",
                )))
                predicted?.let { original ->
                    val fields = correctedFields(original, attributes)
                    if (fields.isNotEmpty()) metrics.track(MetricEvent(MetricNames.TAG_CORRECTED,
                        mapOf("fields" to fields.joinToString(","))))
                }
                _state.update { it.copy(stage = AddStage.SAVED, savedId = item.id) }
                photos.discard(listOfNotNull(current.originalPath, current.segmentedPath))
            }, onFailure = {
                _state.update { it.copy(stage = AddStage.EDITING, error = "Không lưu được món đồ. Kiểm tra dung lượng và thử lại.") }
            })
        }
    }

    fun discard(onDone: () -> Unit) {
        if (_state.value.stage == AddStage.SAVING) return
        work?.cancel()
        viewModelScope.launch {
            work?.join()
            photos.discard(listOfNotNull(_state.value.originalPath, _state.value.segmentedPath))
            _state.value = AddItemState()
            persist()
            onDone()
        }
    }

    private fun persist() {
        val current = _state.value
        savedState["original"] = current.originalPath
        savedState["segmented"] = current.segmentedPath
        savedState["keepOriginal"] = current.keepOriginal
        savedState["form"] = current.form.savedValues()
    }
}
