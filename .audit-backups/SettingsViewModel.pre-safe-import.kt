package com.opencalori.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.backup.BackupManager
import com.opencalori.app.data.preferences.ApiKeyStore
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.model.ValidationStatus
import com.opencalori.app.domain.repository.AiRepository
import com.opencalori.app.domain.repository.ApiConfigStore
import com.opencalori.app.domain.repository.UserPreferences
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
    val busy: Boolean = false,
    val message: String? = null
) {
    val canValidate: Boolean
        get() = !busy &&
            validation.status != ValidationStatus.VALIDATING &&
            apiKey.isNotBlank() && baseUrl.isNotBlank() && modelId.isNotBlank()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiConfigStore: ApiConfigStore,
    private val aiRepository: AiRepository,
    private val userPrefs: UserPreferences,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val profile: StateFlow<UserProfile?> = userPrefs.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val config = apiConfigStore.current()
            _uiState.update {
                it.copy(baseUrl = config.baseUrl, apiKey = config.apiKey, modelId = config.modelId)
            }
        }
    }

    fun setBaseUrl(value: String) = _uiState.update {
        it.copy(baseUrl = value, validation = ApiValidationResult(ValidationStatus.IDLE))
    }

    fun setApiKey(value: String) = _uiState.update {
        it.copy(apiKey = value, validation = ApiValidationResult(ValidationStatus.IDLE))
    }

    fun setModelId(value: String) = _uiState.update {
        it.copy(modelId = value, validation = ApiValidationResult(ValidationStatus.IDLE))
    }

    fun saveAndValidate() {
        val state = _uiState.value
        val config = ApiConfig(state.baseUrl.trim(), state.apiKey.trim(), state.modelId.trim())
        _uiState.update {
            it.copy(validation = ApiValidationResult(ValidationStatus.VALIDATING, "Проверяем ключ и Vision"))
        }
        viewModelScope.launch {
            apiConfigStore.save(config)
            val result = aiRepository.validateApi()
            _uiState.update { it.copy(validation = result) }
        }
    }

    fun clearApi() {
        viewModelScope.launch {
            apiConfigStore.clear()
            _uiState.update {
                it.copy(
                    apiKey = "",
                    validation = ApiValidationResult(ValidationStatus.IDLE),
                    message = "Ключ удалён с устройства"
                )
            }
        }
    }

    // ---- AI scenario ----

    fun setAiEnabled(enabled: Boolean) = viewModelScope.launch { userPrefs.setAiEnabled(enabled) }

    fun setAiSkipListReview(skip: Boolean) = viewModelScope.launch { userPrefs.setAiSkipListReview(skip) }

    fun setAiSkipGramsReview(skip: Boolean) = viewModelScope.launch { userPrefs.setAiSkipGramsReview(skip) }

    fun setAiSkipFinalReview(skip: Boolean) = viewModelScope.launch { userPrefs.setAiSkipFinalReview(skip) }

    // ---- Backup ----

    fun exportTo(uri: Uri) {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = backupManager.exportTo(uri)
            _uiState.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { count -> "Экспортировано записей: " + count },
                        onFailure = { error -> "Не удалось экспортировать: " + (error.message ?: "ошибка") }
                    )
                )
            }
        }
    }

    fun importFrom(uri: Uri) {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = backupManager.importFrom(uri)
            _uiState.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { summary ->
                            "Импортировано: " + summary.items + " продуктов, " +
                                summary.weights + " замеров веса"
                        },
                        onFailure = { error -> "Не удалось импортировать: " + (error.message ?: "ошибка") }
                    )
                )
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Suggested file name for the export picker. */
    fun defaultBackupFileName(dateStamp: String): String = "opencalori-backup-" + dateStamp + ".json"
}
