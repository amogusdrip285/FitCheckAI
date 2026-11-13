package com.fitcheckai.feature.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.Season
import com.fitcheckai.core.model.WardrobeRepository
import com.fitcheckai.core.model.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.Normalizer
import java.util.Locale

private const val STOP_TIMEOUT_MS = 5_000L

@HiltViewModel
class WardrobeViewModel @Inject constructor(
    wardrobe: WardrobeRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val filters = MutableStateFlow(WardrobeFilter(
        category = savedState.get<String>("category")?.let(Category::valueOf),
        color = savedState["color"], season = savedState.get<String>("season")?.let(Season::valueOf),
        query = savedState["query"] ?: "",
    ))
    val state = combine(wardrobe.observeItems(), filters) { all, filter ->
        WardrobeState(all.filter(filter::matches), all.size, filter,
            all.flatMap { it.attributes.colors }.distinctBy(String::uppercase).sorted())
    }.catch { emit(WardrobeState(error = "Không đọc được tủ đồ. Hãy mở lại màn hình.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WardrobeState(loading = true))

    fun filter(value: WardrobeFilter) {
        filters.value = value
        savedState["category"] = value.category?.name
        savedState["color"] = value.color
        savedState["season"] = value.season?.name
        savedState["query"] = value.query
    }
}

data class WardrobeFilter(
    val category: Category? = null, val color: String? = null,
    val season: Season? = null, val query: String = "",
) {
    val activeCount: Int get() = listOf(category, color, season).count { it != null }
    val isActive: Boolean get() = activeCount > 0 || query.isNotBlank()

    fun matches(item: ClothingItem) = (category == null || item.attributes.category == category) &&
        (color == null || item.attributes.colors.any { it.equals(color, ignoreCase = true) }) &&
        (season == null || season in item.attributes.seasons) && matchesQuery(item)

    private fun matchesQuery(item: ClothingItem): Boolean {
        if (query.isBlank()) return true
        val attrs = item.attributes
        val text = normalizeSearch(listOf(attrs.subCategory, attrs.category.label,
            attrs.material.orEmpty()).joinToString(" "))
        return normalizeSearch(query).split(" ").filter(String::isNotBlank).all { it in text }
    }
}

private fun normalizeSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('đ', 'd')

data class WardrobeState(
    val items: List<ClothingItem> = emptyList(), val total: Int = 0,
    val filter: WardrobeFilter = WardrobeFilter(), val colors: List<String> = emptyList(),
    val loading: Boolean = false, val error: String? = null,
)

data class DetailState(
    val item: ClothingItem? = null, val loading: Boolean = true, val error: String? = null,
    val busy: Boolean = false, val deleted: Boolean = false, val editing: Boolean = false,
    val form: AttributeForm = AttributeForm(),
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val wardrobe: WardrobeRepository,
) : ViewModel() {
    private val itemId: String = checkNotNull(savedStateHandle[Routes.ARG_ITEM_ID])

    private val _state = MutableStateFlow(DetailState(
        editing = savedStateHandle["editing"] ?: false,
        form = AttributeForm.restore(savedStateHandle["detailForm"]),
    ))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            wardrobe.observeItems().catch { _state.update { it.copy(loading = false, error = "Không đọc được món đồ.") } }
                .collect { items -> _state.update { it.copy(item = items.firstOrNull { item -> item.id == itemId }, loading = false) } }
        }
    }

    fun startEditing() {
        val item = _state.value.item ?: return
        edit(AttributeForm.from(item.attributes))
        _state.update { it.copy(editing = true, error = null) }
        savedStateHandle["editing"] = true
    }

    fun edit(form: AttributeForm) {
        if (_state.value.busy) return
        _state.update { it.copy(form = form, error = null) }
        savedStateHandle["detailForm"] = form.savedValues()
    }

    fun cancelEditing() {
        if (_state.value.busy) return
        _state.update { it.copy(editing = false, error = null) }
        savedStateHandle["editing"] = false
    }

    fun save() {
        val current = _state.value
        val item = current.item ?: return
        if (current.busy) return
        val attrs = current.form.attributes().getOrElse { error ->
            _state.update { it.copy(error = error.message) }; return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = wardrobe.update(item.copy(attributes = attrs))
            _state.update { it.copy(busy = false, editing = result.isFailure,
                error = if (result.isFailure) "Không lưu được thay đổi. Hãy thử lại." else null) }
            savedStateHandle["editing"] = result.isFailure
        }
    }

    fun delete() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = wardrobe.delete(itemId)
            _state.update { it.copy(busy = false, deleted = result.isSuccess,
                error = if (result.isFailure) "Không xóa được món đồ. Hãy thử lại." else null) }
        }
    }
}
