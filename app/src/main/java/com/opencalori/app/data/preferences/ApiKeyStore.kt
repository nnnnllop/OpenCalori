package com.opencalori.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.di.IoDispatcher
import com.opencalori.app.domain.repository.ApiConfigStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's BYOK credentials in EncryptedSharedPreferences.
 *
 * Keystore-backed prefs are expensive to open (key generation on first run can take
 * hundreds of milliseconds), so nothing touches disk until the first suspending call and
 * all of it happens off the main thread.
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ApiConfigStore {

    private val _config = MutableStateFlow(ApiConfig(apiKey = ""))
    override val config: Flow<ApiConfig> = _config.asStateFlow()

    private val lock = Mutex()
    private var prefs: SharedPreferences? = null
    private var loaded = false

    private suspend fun prefs(): SharedPreferences = lock.withLock {
        prefs ?: withContext(io) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { prefs = it }
        }
    }

    override suspend fun current(): ApiConfig {
        if (!loaded) {
            val stored = prefs()
            val config = withContext(io) {
                ApiConfig(
                    baseUrl = stored.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
                    apiKey = stored.getString(KEY_API_KEY, "") ?: "",
                    modelId = stored.getString(KEY_MODEL_ID, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
                )
            }
            loaded = true
            _config.value = config
        }
        return _config.value
    }

    override suspend fun save(config: ApiConfig) {
        val normalized = ApiConfig(
            baseUrl = config.baseUrl.trim(),
            apiKey = config.apiKey.trim(),
            modelId = config.modelId.trim()
        )
        val stored = prefs()
        withContext(io) {
            stored.edit()
                .putString(KEY_BASE_URL, normalized.baseUrl)
                .putString(KEY_API_KEY, normalized.apiKey)
                .putString(KEY_MODEL_ID, normalized.modelId)
                .commit()
        }
        loaded = true
        _config.value = normalized
    }

    override suspend fun clear() {
        val stored = prefs()
        withContext(io) { stored.edit().clear().commit() }
        loaded = true
        _config.value = ApiConfig(apiKey = "")
    }

    companion object {
        private const val PREFS_NAME = "byok_api_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL_ID = "model_id"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL_ID = "gpt-4o"
    }
}
