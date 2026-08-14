package com.opencalori.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.backup.BackupManager
import com.opencalori.app.data.backup.ImportMode
import com.opencalori.app.data.backup.ImportPreview
import com.opencalori.app.data.backup.ImportSummary
import com.opencalori.app.data.preferences.ApiKeyStore
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.NutritionSourceMode
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
import java.net.URI
import javax.inject.Inject

data class PendingImport(val uri: Uri, val preview: ImportPreview)

data class SettingsUiState(
    val baseUrl: String = ApiKeyStore.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val modelId: String = ApiKeyStore.DEFAULT_MODEL_ID,
    val validation: ApiValidationResult = ApiValidationResult(ValidationStatus.IDLE),
    val busy: Boolean = false,
    val message: String? = null,
    val baseUrlError: String? = null,
    val pendingImport: PendingImport? = null,
    val canUndoImport: Boolean = false
) {
    val canValidate: Boolean
        get() = !busy &&
            validation.status != ValidationStatus.VALIDATING &&
            apiKey.isNotBlank() && baseUrl.isNotBlank() && modelId.isNotBlank() && baseUrlError == null
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
                it.copy(
                    baseUrl = config.baseUrl,
                    baseUrlError = validateBaseUrl(config.baseUrl),
                    apiKey = config.apiKey,
                    modelId = config.modelId
                )
            }
        }
    }

    fun setBaseUrl(value: String) = _uiState.update {
        it.copy(
            baseUrl = value,
            baseUrlError = validateBaseUrl(value),
            validation = ApiValidationResult(ValidationStatus.IDLE)
        )
    }

    fun setApiKey(value: String) = _uiState.update {
        it.copy(apiKey = value, validation = ApiValidationResult(ValidationStatus.IDLE))
    }

    fun setModelId(value: String) = _uiState.update {
        it.copy(modelId = value, validation = ApiValidationResult(ValidationStatus.IDLE))
    }

    /** Applies the public NVIDIA endpoint and model ID without touching the user key. */
    fun useNvidiaNemotronPreset() = _uiState.update {
        it.copy(
            baseUrl = NVIDIA_BASE_URL,
            modelId = NVIDIA_NEMOTRON_MODEL,
            baseUrlError = null,
            validation = ApiValidationResult(ValidationStatus.IDLE)
        )
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

    private fun validateBaseUrl(value: String): String? {
        val input = value.trim()
        if (input.isEmpty()) return null
        val uri = runCatching { URI(input) }.getOrNull()
            ?: return "Укажите корректный URL, например https://api.openai.com/v1"
        val host = uri.host?.lowercase()
            ?: return "В URL должен быть указан домен или адрес сервера"
        if (uri.scheme !in setOf("https", "http")) return "Используйте URL с https://"
        val localHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
        if (uri.scheme == "http" && !localHost) {
            return "Для внешнего API используйте защищённый адрес https://"
        }
        return null
    }

    // ---- AI scenario ----
    fun setAiEnabled(enabled: Boolean) = viewModelScope.launch {
        userPrefs.setAiEnabled(enabled)
        if (!enabled) userPrefs.setNutritionSourceMode(NutritionSourceMode.LOCAL_DATABASE)
    }
    fun setAiSkipListReview(skip: Boolean) = viewModelScope.launch { userPrefs.setAiSkipListReview(skip) }
    fun setAiSkipGramsReview(skip: Boolean) = viewModelScope.launch { userPrefs.setAiSkipGramsReview(skip) }
    fun setAiSkipFinalReview(skip: Boolean) = viewModelScope.launch { userPrefs.setAiSkipFinalReview(skip) }
    fun setNutritionSourceMode(mode: NutritionSourceMode) = viewModelScope.launch {
        userPrefs.setNutritionSourceMode(mode)
        userPrefs.setAiEnabled(mode.usesAi)
    }

    // ---- Backup ----
    fun exportTo(uri: Uri) {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = backupManager.exportTo(uri)
            _uiState.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { count -> "Экспортировано записей: $count" },
                        onFailure = { error -> "Не удалось экспортировать: ${error.message ?: "ошибка"}" }
                    )
                )
            }
        }
    }

    /** Reads and summarizes a selected file without changing local data. */
    fun prepareImport(uri: Uri) {
        _uiState.update { it.copy(busy = true, pendingImport = null) }
        viewModelScope.launch {
            val result = backupManager.previewImport(uri)
            _uiState.update {
                it.copy(
                    busy = false,
                    pendingImport = result.getOrNull()?.let { preview -> PendingImport(uri, preview) },
                    message = result.exceptionOrNull()?.let { error ->
                        "Не удалось прочитать резервную копию: ${error.message ?: "ошибка"}"
                    }
                )
            }
        }
    }

    fun dismissImportPreview() = _uiState.update { it.copy(pendingImport = null) }

    fun confirmImport(mode: ImportMode) {
        val pending = _uiState.value.pendingImport ?: return
        _uiState.update { it.copy(busy = true, pendingImport = null, canUndoImport = false) }
        viewModelScope.launch {
            val result = backupManager.importFrom(pending.uri, mode)
            _uiState.update {
                val summary = result.getOrNull()
                it.copy(
                    busy = false,
                    canUndoImport = summary != null,
                    message = summary?.toMessage() ?: run {
                        val error = result.exceptionOrNull()
                        "Не удалось импортировать: ${error?.message ?: "ошибка"}"
                    }
                )
            }
        }
    }

    fun undoImport() {
        _uiState.update { it.copy(busy = true, canUndoImport = false) }
        viewModelScope.launch {
            val result = backupManager.undoLastImport()
            _uiState.update {
                it.copy(
                    busy = false,
                    canUndoImport = false,
                    message = result.fold(
                        onSuccess = { "Импорт отменён: восстановлены прежние данные" },
                        onFailure = { error -> "Не удалось отменить импорт: ${error.message ?: "ошибка"}" }
                    )
                )
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, canUndoImport = false) }

    /** Suggested file name for the export picker. */
    fun defaultBackupFileName(dateStamp: String): String = "opencalori-backup-$dateStamp.json"

    private companion object {
        const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
        const val NVIDIA_NEMOTRON_MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning"
    }

    private fun ImportSummary.toMessage(): String = when {
        restored -> "Импорт отменён: восстановлены прежние данные"
        replaced -> "Данные заменены: $items продуктов, $weights замеров веса"
        else -> buildString {
            append("Добавлено: $items продуктов, $weights замеров веса")
            if (skippedItems > 0 || skippedProducts > 0) {
                append("; пропущено дублей: ${skippedItems + skippedProducts}")
            }
        }
    }
}
