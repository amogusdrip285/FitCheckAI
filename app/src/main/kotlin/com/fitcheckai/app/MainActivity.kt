package com.fitcheckai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.wardrobe.LoadState
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
 * Chooses the start destination ONCE: no profile / onboarding not completed → onboarding.
 * An unreadable profile never blocks the app: go to Today, the Profile tab offers reset. null = loading.
 */
@HiltViewModel
class MainViewModel @Inject constructor(profiles: ProfileStorage) : ViewModel() {
    val startDestination: StateFlow<String?> = flow {
        val state = profiles.observeProfile().first { it !is LoadState.Loading }
        emit(startDestinationFor(state))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

internal fun startDestinationFor(state: LoadState<com.fitcheckai.core.model.profile.UserProfile?>): String = when (state) {
    is LoadState.Ready -> if (state.value?.onboardingCompleted == true) Routes.TODAY_PATTERN else Routes.ONBOARDING
    is LoadState.Failed -> Routes.TODAY_PATTERN
    LoadState.Loading -> Routes.ONBOARDING
}
