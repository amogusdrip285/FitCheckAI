package com.fitcheckai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.ProfileRepository
import com.fitcheckai.core.model.nav.Routes
import com.fitcheckai.core.ui.theme.FitCheckTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitCheckTheme {
                FitCheckApp()
            }
        }
    }
}

/**
 * Chọn màn hình bắt đầu MỘT LẦN khi mở app: chưa có profile → onboarding. null = đang tải.
 * Không theo dõi tiếp, để NavHost không bị dựng lại khi onboarding lưu profile.
 */
@HiltViewModel
class MainViewModel @Inject constructor(profiles: ProfileRepository) : ViewModel() {
    val startDestination: StateFlow<String?> = flow {
        val profile = profiles.observe().first()
        emit(if (profile == null) Routes.ONBOARDING else Routes.TODAY_PATTERN)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
