package com.opencalori.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.domain.repository.ApiConfigStore
import com.opencalori.app.domain.repository.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    userPrefs: UserPreferences,
    private val apiConfigStore: ApiConfigStore
) : ViewModel() {

    val startDestination = userPrefs.profile
        .map { if (it.onboardingCompleted) Routes.DASHBOARD else Routes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Warms the encrypted store off the main thread so the rest of the app can read
        // the BYOK config from a plain StateFlow without blocking on Keystore.
        viewModelScope.launch { apiConfigStore.current() }
    }
}
