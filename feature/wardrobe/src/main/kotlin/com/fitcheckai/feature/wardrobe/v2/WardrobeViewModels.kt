package com.fitcheckai.feature.wardrobe.v2

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.wardrobe.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ItemCard(val item: ClothingItem, val frontPath: String?, val backPath: String?)
data class Filter(val query: String = "", val category: Category? = null, val color: GarmentColor? = null) {
    val active get() = query.isNotBlank() || category != null || color != null
    fun matches(item: ClothingItem): Boolean {
        val text = normalize("${item.name} ${item.attributes.material.orEmpty()} ${item.attributes.category.label}")
        return (category == null || item.attributes.category == category) &&
            (color == null || color in item.attributes.colors) &&
            normalize(query).split(Regex("\\s+")).filter(String::isNotBlank).all { it in text }
    }
    private fun normalize(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('đ', 'd')
}
data class ListState(val cards: List<ItemCard> = emptyList(), val total: Int = 0, val filter: Filter = Filter(),
    val loading: Boolean = true, val error: String? = null)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WardrobeListViewModel @Inject constructor(private val repository: WardrobeRepository, private val media: MediaStore,
    private val saved: SavedStateHandle) : ViewModel() {
    private val filters = MutableStateFlow(Filter(saved["query"] ?: "",
        saved.get<String>("category")?.let(Category::valueOf), saved.get<String>("color")?.let(GarmentColor::valueOf)))
    private val reloads = MutableStateFlow(0)
    val state = combine(reloads.flatMapLatest { repository.observeItems() }, filters) { load, filter ->
        when (load) {
            is LoadState.Ready -> ListState(load.value.filter(filter::matches).map {
                ItemCard(it, it.images.front?.let(media::displayPath), it.images.back?.let(media::displayPath))
            }, load.value.size, filter, false)
            is LoadState.Failed -> ListState(filter = filter, loading = false, error = load.error.message)
            LoadState.Loading -> ListState(filter = filter)
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListState(filter = filters.value))
    fun filter(filter: Filter) {
        filters.value = filter
        saved["query"] = filter.query; saved["category"] = filter.category?.name; saved["color"] = filter.color?.name
    }
    fun retry() { reloads.value++ }
}

data class EditorState(
    val item: ClothingItem,
    val frontPath: String? = null,
    val backPath: String? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val recognizing: Boolean = false,
    val importingView: ImageView? = null,
    val error: String? = null,
    val notice: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val isNew: Boolean = true,
    val expectedRevision: Long? = null,
    val fakeResult: Boolean = false,
)

@HiltViewModel
class WardrobeEditorViewModel @Inject constructor(
    private val repository: WardrobeRepository, private val media: MediaStore,
    private val recognition: ClothingRecognition, private val savedState: SavedStateHandle,
) : ViewModel() {
    private val targetId = savedState.get<String>("itemId")
    private val draftId = savedState.get<String>("draftId") ?: UUID.randomUUID().toString().also { savedState["draftId"] = it }
    private val restored = savedState.get<String>("v2Draft")?.let { runCatching { WardrobeCodec.decodeItem(it) }.getOrNull() }
        ?.let { if (it.recognitionState == RecognitionState.PENDING) it.copy(recognitionState = RecognitionState.NOT_REQUESTED) else it }
    private val mutable = MutableStateFlow(EditorState(restored ?: ClothingItem(draftId, "", ImagePair()),
        loading = targetId != null && restored == null, isNew = targetId == null,
        expectedRevision = savedState["expectedRevision"]))
    val state = mutable.asStateFlow()
    val capability get() = recognition.capability
    private var draftRevision: Long = savedState["draftRevision"] ?: 1
    private var touched = savedState.get<ArrayList<String>>("touched").orEmpty().map(AttributeField::valueOf).toSet()
    private var newlyImported = savedState.get<ArrayList<String>>("newMedia").orEmpty().map(WardrobeCodec::decodeMedia).toMutableList()
    private var recognitionJob: Job? = null
    private var activeRequest: String? = null

    init {
        viewModelScope.launch {
            if (targetId != null && restored == null) {
                repository.get(targetId).fold({ item ->
                    if (item == null) mutable.update { it.copy(loading = false, error = "Món đồ không còn trong tủ.") }
                    else {
                        touched = item.attributes.attributeSources.filterValues { it == AttributeSource.MANUAL }.keys
                        mutable.value = EditorState(item, isNew = false, expectedRevision = item.revision); persist(); refreshPaths()
                    }
                }, { e -> mutable.update { it.copy(loading = false, error = e.message) } })
            } else refreshPaths()
        }
    }

    fun editName(name: String) {
        if (state.value.busy) return
        mutable.update { it.copy(item = it.item.copy(name = name.take(100)), error = null) }; persist()
    }
    fun editAttributes(attributes: ClothingAttributes, field: AttributeField) {
        if (state.value.busy) return
        touched = touched + field
        val value = attributes.copy(attributeSources = attributes.attributeSources + (field to AttributeSource.MANUAL),
            needsReviewFields = attributes.needsReviewFields - field)
        mutable.update { it.copy(item = it.item.copy(attributes = value, recognitionState = if (value.needsReviewFields.isEmpty()) RecognitionState.CONFIRMED else RecognitionState.REVIEW), error = null) }
        persist()
    }
    fun acknowledgeReview() {
        if (state.value.busy) return
        val current = state.value.item.attributes
        val reviewed = current.needsReviewFields
        touched += reviewed
        mutable.update { it.copy(item = it.item.copy(attributes = current.copy(needsReviewFields = emptySet(),
            attributeSources = current.attributeSources + reviewed.associateWith { AttributeSource.MANUAL }), recognitionState = RecognitionState.CONFIRMED)) }
        persist()
    }

    fun chooseImage(uri: String, view: ImageView, afterImport: () -> Unit = {}) {
        if (state.value.busy || state.value.importingView != null) return
        invalidateRecognition()
        mutable.update { it.copy(importingView = view, error = null) }
        viewModelScope.launch {
            try { media.importImage(uri, MediaScope.GARMENT, state.value.item.id, view).fold({ ref ->
                val other = state.value.item.images[if (view == ImageView.FRONT) ImageView.BACK else ImageView.FRONT]
                if (other?.contentHash == ref.contentHash) {
                    media.remove(ref)
                    mutable.update { it.copy(importingView = null, error = "Bạn đã chọn cùng một ảnh cho hai mặt. Hãy chọn ảnh mặt ${if (view == ImageView.FRONT) "trước" else "sau"} khác.") }
                } else {
                    newlyImported += ref
                    mutable.update { it.copy(item = it.item.copy(images = it.item.images.with(view, ref),
                        recognitionState = RecognitionState.NOT_REQUESTED), importingView = null, notice = null) }
                    persist(); refreshPaths()
                }
            }, { e -> mutable.update { it.copy(importingView = null, error = e.message) } }) }
            finally { afterImport() }
        }
    }
    fun removeBack() {
        if (state.value.busy || state.value.importingView != null) return
        invalidateRecognition()
        mutable.update { it.copy(item = it.item.copy(images = it.item.images.copy(back = null), recognitionState = RecognitionState.NOT_REQUESTED), backPath = null) }
        persist()
    }

    /** Called only after the user confirms the provider and exact current views in the UI. */
    fun recognize(confirmedCapability: RecognitionCapability = capability,
        confirmedViews: Map<String, ImageView> = state.value.item.images.refs().associate { it.id to it.view }) {
        if (state.value.busy || state.value.recognizing || state.value.importingView != null) return
        val start = state.value.item
        if (confirmedCapability != capability || confirmedViews != start.images.refs().associate { it.id to it.view }) {
            mutable.update { it.copy(error = "Ảnh hoặc dịch vụ đã thay đổi. Hãy xem lại và xác nhận gửi ảnh lần nữa.") }; return
        }
        val requestId = UUID.randomUUID().toString()
        val request = RecognitionRequest(requestId, start.id, draftRevision, start.images,
            ConsentGrant(requestId = requestId, provider = capability.provider, providerConfigVersion = capability.configVersion,
                mediaIds = start.images.refs().map { it.id }.toSet(), mediaViews = confirmedViews,
                requestGeneration = draftRevision, confirmedByUser = true))
        try { request.validate(capability) } catch (e: Exception) { mutable.update { it.copy(error = e.message) }; return }
        activeRequest = requestId
        mutable.update { it.copy(recognizing = true, error = null, item = it.item.copy(recognitionState = RecognitionState.PENDING)) }
        recognitionJob = viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(20_000) { recognition.recognize(request) }
                    ?: Result.failure(WardrobeException(ErrorCode.UPSTREAM_TIMEOUT, "Nhận diện quá thời gian. Bạn vẫn có thể điền và lưu thủ công."))
                if (activeRequest != requestId || draftRevision != request.inputRevision) return@launch
                result.fold({ response ->
                    if (response.requestId != requestId || response.itemId != start.id || response.inputRevision != draftRevision) {
                        mutable.update { it.copy(recognizing = false, error = "Đã bỏ kết quả cũ không khớp ảnh hiện tại.", item = it.item.copy(recognitionState = RecognitionState.FAILED)) }
                    } else {
                        val attributes = mergeRecognition(state.value.item.attributes, touched, response)
                        mutable.update { it.copy(item = it.item.copy(attributes = attributes,
                            recognitionState = if (attributes.needsReviewFields.isEmpty()) RecognitionState.CONFIRMED else RecognitionState.REVIEW),
                            recognizing = false, fakeResult = response.source == ResultSource.FAKE,
                            notice = response.warnings.joinToString("\n").ifBlank { "Kiểm tra thông tin gợi ý trước khi lưu." }) }
                    }
                }, { e -> mutable.update { it.copy(recognizing = false, error = e.message, item = it.item.copy(recognitionState = RecognitionState.FAILED)) } })
                persist()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(recognizing = false, error = "Kết quả AI không hợp lệ. Hãy điền thủ công.", item = it.item.copy(recognitionState = RecognitionState.FAILED)) } }
        }
    }
    fun cancelRecognition() { invalidateRecognition(); mutable.update { it.copy(notice = "Đã dừng chờ nhận diện; bạn có thể tự điền thông tin.") }; persist() }
    private fun invalidateRecognition() {
        activeRequest = null; recognitionJob?.cancel(); draftRevision++
        mutable.update { it.copy(recognizing = false, item = it.item.copy(recognitionState =
            if (it.item.recognitionState == RecognitionState.PENDING) RecognitionState.NOT_REQUESTED else it.item.recognitionState)) }
    }

    fun save() {
        val current = state.value
        if (current.busy || current.loading || current.importingView != null || current.saved || current.deleted) return
        try { current.item.validate() } catch (e: Exception) { mutable.update { it.copy(error = e.message) }; return }
        invalidateRecognition()
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repository.upsert(state.value.item, current.expectedRevision).fold({ item ->
                cleanupUnused(item.images.refs().map { it.id }.toSet())
                savedState.remove<String>("v2Draft"); newlyImported.clear(); savedState.remove<ArrayList<String>>("newMedia")
                mutable.update { it.copy(item = item, busy = false, saved = true, expectedRevision = item.revision) }
            }, { e -> mutable.update { it.copy(busy = false, error = e.message) } })
        }
    }
    fun delete() {
        val current = state.value
        if (current.busy || current.expectedRevision == null) return
        invalidateRecognition(); mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repository.delete(current.item.id, current.expectedRevision).fold({ receipt ->
                cleanupUnused(emptySet()); savedState.remove<String>("v2Draft")
                mutable.update { it.copy(busy = false, deleted = true, notice = if (receipt.cleanupPending) "Ảnh sẽ được dọn lại khi bộ nhớ sẵn sàng." else null) }
            }, { e -> mutable.update { it.copy(busy = false, error = e.message) } })
        }
    }
    fun discard(onDone: () -> Unit) {
        if (state.value.busy || state.value.importingView != null) return
        invalidateRecognition(); mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            repository.get(state.value.item.id).fold({ item ->
                cleanupUnused(item?.images?.refs()?.map { it.id }?.toSet().orEmpty())
                savedState.remove<String>("v2Draft"); savedState.remove<ArrayList<String>>("newMedia"); onDone()
            }, { e -> mutable.update { it.copy(busy = false, error = e.message) } })
        }
    }
    fun reload() {
        if (state.value.busy || state.value.isNew) return
        viewModelScope.launch {
            repository.get(targetId ?: state.value.item.id).fold({ item ->
                if (item != null) {
                    cleanupUnused(item.images.refs().map { it.id }.toSet())
                    touched = item.attributes.attributeSources.filterValues { it == AttributeSource.MANUAL }.keys
                    mutable.value = EditorState(item, isNew = false, expectedRevision = item.revision); persist(); refreshPaths()
                } else mutable.update { it.copy(error = "Món đồ đã bị xóa.") }
            }, { e -> mutable.update { it.copy(error = e.message) } })
        }
    }
    private suspend fun cleanupUnused(retain: Set<String>) {
        newlyImported.filter { it.id !in retain }.forEach { media.remove(it) }
        newlyImported = newlyImported.filter { it.id in retain }.toMutableList()
    }
    private suspend fun refreshPaths() = withContext(Dispatchers.IO) {
        val current = state.value.item
        val front = current.images.front?.let(media::displayPath)
        val back = current.images.back?.let(media::displayPath)
        mutable.update { if (it.item.images == current.images) it.copy(frontPath = front, backPath = back) else it }
    }
    private fun persist() {
        savedState["v2Draft"] = WardrobeCodec.encodeItem(state.value.item)
        savedState["expectedRevision"] = state.value.expectedRevision
        savedState["draftRevision"] = draftRevision
        savedState["touched"] = ArrayList(touched.map { it.name })
        savedState["newMedia"] = ArrayList(newlyImported.map(WardrobeCodec::encodeMedia))
    }
}

internal val Category.label get() = when (this) { Category.TOP -> "Áo"; Category.BOTTOM -> "Quần / váy"; Category.DRESS -> "Đầm"; Category.OUTERWEAR -> "Áo khoác"; Category.SHOES -> "Giày"; Category.ACCESSORY -> "Phụ kiện"; Category.UNKNOWN -> "Chưa xác định" }
internal val GarmentColor.label get() = when (this) { GarmentColor.BLACK -> "Đen"; GarmentColor.WHITE -> "Trắng"; GarmentColor.GRAY -> "Xám"; GarmentColor.BEIGE -> "Be"; GarmentColor.BROWN -> "Nâu"; GarmentColor.BLUE -> "Xanh dương"; GarmentColor.GREEN -> "Xanh lá"; GarmentColor.RED -> "Đỏ"; GarmentColor.YELLOW -> "Vàng"; GarmentColor.PINK -> "Hồng"; GarmentColor.PURPLE -> "Tím"; GarmentColor.OTHER -> "Khác" }
internal val GarmentColor.hex get() = when (this) { GarmentColor.BLACK -> "#222222"; GarmentColor.WHITE -> "#FFFFFF"; GarmentColor.GRAY -> "#888888"; GarmentColor.BEIGE -> "#D8C3A5"; GarmentColor.BROWN -> "#6B4226"; GarmentColor.BLUE -> "#3B5B8C"; GarmentColor.GREEN -> "#228B22"; GarmentColor.RED -> "#B23A30"; GarmentColor.YELLOW -> "#E8BF50"; GarmentColor.PINK -> "#D29DA9"; GarmentColor.PURPLE -> "#84619B"; GarmentColor.OTHER -> "#BAC5C5" }
