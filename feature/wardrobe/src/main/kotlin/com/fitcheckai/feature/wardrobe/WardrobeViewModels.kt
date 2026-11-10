package com.fitcheckai.feature.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.WardrobeRepository
import com.fitcheckai.core.model.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

@HiltViewModel
class WardrobeViewModel @Inject constructor(
    wardrobe: WardrobeRepository,
) : ViewModel() {
    val items: StateFlow<List<ClothingItem>?> = wardrobe.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
}

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    wardrobe: WardrobeRepository,
) : ViewModel() {
    private val itemId: String = checkNotNull(savedStateHandle[Routes.ARG_ITEM_ID])

    val item: StateFlow<ClothingItem?> = wardrobe.observeItems()
        .map { items -> items.firstOrNull { it.id == itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
}
