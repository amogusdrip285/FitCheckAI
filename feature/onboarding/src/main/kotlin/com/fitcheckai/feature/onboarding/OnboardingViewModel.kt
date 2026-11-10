package com.fitcheckai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.ImageSegmenter
import com.fitcheckai.core.model.MetricEvent
import com.fitcheckai.core.model.MetricNames
import com.fitcheckai.core.model.MetricsTracker
import com.fitcheckai.core.model.ProfileRepository
import com.fitcheckai.core.model.StyleProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val STYLE_OPTIONS = listOf("Minimal", "Streetwear", "Công sở", "Năng động", "Vintage", "Nữ tính", "Smart casual")

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val segmenter: ImageSegmenter,
    private val metrics: MetricsTracker,
) : ViewModel() {
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    init {
        // Tải trước model tách nền để lần thêm đồ đầu tiên không phải chờ.
        viewModelScope.launch { segmenter.warmUp() }
    }

    fun toggle(style: String) {
        _selected.update { if (style in it) it - style else it + style }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            profiles.save(StyleProfile(styles = _selected.value)).onSuccess {
                metrics.track(MetricEvent(MetricNames.ONBOARDING_COMPLETED, mapOf("styles" to _selected.value.size.toString())))
                onDone()
            }
        }
    }
}
