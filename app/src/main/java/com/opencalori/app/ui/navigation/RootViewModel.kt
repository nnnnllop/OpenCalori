package com.opencalori.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.preferences.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    userPrefs: UserPreferencesStore
) : ViewModel() {
    val startDestination = userPrefs.profile
        .map { if (it.onboardingCompleted) Routes.DASHBOARD else Routes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
