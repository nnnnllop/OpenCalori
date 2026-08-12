package com.opencalori.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.preferences.ApiKeyStore
import com.opencalori.app.data.preferences.UserPreferencesStore
import com.opencalori.app.data.repository.AiRepository
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.ValidationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val baseUrl: String = ApiKeyStore.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val modelId: String = ApiKeyStore.DEFAULT_MODEL_ID,
    val validation: ApiValidationResult = ApiValidationResult(ValidationStatus.IDLE),
    val saved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val aiRepository: AiRepository,
    val userPrefs: UserPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val profile = userPrefs.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            apiKeyStore.config.collect { cfg ->
                _uiState.update {
                    it.copy(baseUrl = cfg.baseUrl, apiKey = cfg.apiKey, modelId = cfg.modelId)
                }
            }
        }
    }

    fun setBaseUrl(v: String) = _uiState.update { it.copy(baseUrl = v, saved = false) }
    fun setApiKey(v: String) = _uiState.update { it.copy(apiKey = v, saved = false) }
    fun setModelId(v: String) = _uiState.update { it.copy(modelId = v, saved = false) }

    fun saveAndValidate() {
        val s = _uiState.value
        val config = ApiConfig(s.baseUrl.trim(), s.apiKey.trim(), s.modelId.trim())
        apiKeyStore.save(config)
        _uiState.update { it.copy(validation = ApiValidationResult(ValidationStatus.VALIDATING, "Проверка…"), saved = false) }
        viewModelScope.launch {
            val result = aiRepository.validateApi()
            _uiState.update { it.copy(validation = result, saved = result.status == ValidationStatus.SUCCESS) }
        }
    }

    fun clearApi() {
        apiKeyStore.clear()
        _uiState.update {
            it.copy(
                apiKey = "",
                validation = ApiValidationResult(ValidationStatus.IDLE),
                saved = false
            )
        }
    }
}
