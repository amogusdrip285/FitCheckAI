package com.fitcheckai.core.analytics

import com.fitcheckai.core.model.MetricEvent
import com.fitcheckai.core.model.MetricsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_EVENTS = 500

/**
 * SPRINT 0 STUB — giữ tối đa [MAX_EVENTS] event trong RAM.
 * TODO(Track D): lưu bền (Room/file) + export CSV + (tuỳ chọn) Firebase Analytics.
 */
@Singleton
class InMemoryMetricsTracker @Inject constructor() : MetricsTracker {
    private val _events = MutableStateFlow<List<MetricEvent>>(emptyList())
    val events: StateFlow<List<MetricEvent>> = _events.asStateFlow()

    override fun track(event: MetricEvent) {
        _events.update { (it + event).takeLast(MAX_EVENTS) }
    }
}
