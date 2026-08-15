package com.opencalori.app.ui.settings

import android.net.Uri
import com.opencalori.app.data.backup.BackupManager
import com.opencalori.app.data.backup.BackupSnapshot
import com.opencalori.app.data.backup.ImportSummary
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.NutritionSourceMode
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.model.ValidationStatus
import com.opencalori.app.testing.FakeAiRepository
import com.opencalori.app.testing.FakeApiConfigStore
import com.opencalori.app.testing.FakeUserPreferences
import com.opencalori.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiConfig = FakeApiConfigStore(ApiConfig(apiKey = ""))
    private val ai = FakeAiRepository()
    private val prefs = FakeUserPreferences(UserProfile(onboardingCompleted = true))

    private val backup = object : BackupManager {
        override suspend fun snapshot() = BackupSnapshot(UserProfile(), emptyList(), emptyList(), emptyList())
        override suspend fun exportTo(uri: Uri): Result<Int> = Result.success(0)
        override suspend fun importFrom(uri: Uri): Result<ImportSummary> =
            Result.success(ImportSummary(0, 0, 0, 0))
    }

    private fun viewModel() = SettingsViewModel(apiConfig, ai, prefs, backup)

    @Test
    fun `stored credentials are loaded into the form`() = runTest {
        apiConfig.state.value = ApiConfig("https://proxy.local/v1", "sk-stored", "gpt-4o-mini")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("https://proxy.local/v1", vm.uiState.value.baseUrl)
        assertEquals("sk-stored", vm.uiState.value.apiKey)
        assertEquals("gpt-4o-mini", vm.uiState.value.modelId)
    }

    @Test
    fun `validation is blocked until every field is filled`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canValidate)

        vm.setApiKey("sk-test")
        assertTrue(vm.uiState.value.canValidate)
    }

    @Test
    fun `editing a field clears the previous verdict`() = runTest {
        ai.validation = ApiValidationResult(ValidationStatus.AUTH_ERROR, "Неверный ключ")
        val vm = viewModel()
        advanceUntilIdle()
        vm.setApiKey("sk-bad")
        vm.saveAndValidate()
        advanceUntilIdle()
        assertEquals(ValidationStatus.AUTH_ERROR, vm.uiState.value.validation.status)

        vm.setApiKey("sk-better")

        assertEquals(ValidationStatus.IDLE, vm.uiState.value.validation.status)
    }

    @Test
    fun `a successful check saves the credentials`() = runTest {
        ai.validation = ApiValidationResult(ValidationStatus.SUCCESS, "Подключено")
        val vm = viewModel()
        advanceUntilIdle()

        vm.setApiKey("  sk-test  ")
        vm.setModelId(" gpt-4o ")
        vm.saveAndValidate()
        advanceUntilIdle()

        assertEquals(ValidationStatus.SUCCESS, vm.uiState.value.validation.status)
        // Stray whitespace from a paste would otherwise break the Authorization header.
        assertEquals("sk-test", apiConfig.state.value.apiKey)
        assertEquals("gpt-4o", apiConfig.state.value.modelId)
    }

    @Test
    fun `a model without vision is reported distinctly from a bad key`() = runTest {
        ai.validation = ApiValidationResult(ValidationStatus.NO_VISION, "Модель не принимает изображения")
        val vm = viewModel()
        advanceUntilIdle()

        vm.setApiKey("sk-test")
        vm.saveAndValidate()
        advanceUntilIdle()

        assertEquals(ValidationStatus.NO_VISION, vm.uiState.value.validation.status)
    }

    @Test
    fun `clearing wipes the key from encrypted storage`() = runTest {
        apiConfig.state.value = ApiConfig(apiKey = "sk-secret")
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearApi()
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.apiKey)
        assertEquals("", apiConfig.state.value.apiKey)
        assertEquals(1, apiConfig.clearCount)
    }

    @Test
    fun `ai scenario switches are persisted`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setAiSkipListReview(true)
        vm.setAiSkipGramsReview(true)
        vm.setAiSkipFinalReview(true)
        vm.setAiEnabled(false)
        advanceUntilIdle()

        val profile = prefs.state.value
        assertTrue(profile.aiSkipListReview)
        assertTrue(profile.aiSkipGramsReview)
        assertTrue(profile.aiSkipFinalReview)
        assertFalse(profile.aiEnabled)
    }

    @Test
    fun `enabling ai puts the app into AI-only mode`() = runTest {
        prefs.state.value = prefs.state.value.copy(
            aiEnabled = false,
            nutritionSourceMode = NutritionSourceMode.LOCAL_DATABASE
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.setAiEnabled(true)
        advanceUntilIdle()

        assertTrue(prefs.state.value.aiEnabled)
        assertEquals(NutritionSourceMode.AI_ONLY, prefs.state.value.nutritionSourceMode)
    }

    @Test
    fun `disabling ai falls back to the offline catalogue`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setAiEnabled(false)
        advanceUntilIdle()

        assertFalse(prefs.state.value.aiEnabled)
        assertEquals(NutritionSourceMode.LOCAL_DATABASE, prefs.state.value.nutritionSourceMode)
    }

    @Test
    fun `choosing hybrid turns ai back on`() = runTest {
        prefs.state.value = prefs.state.value.copy(aiEnabled = false)
        val vm = viewModel()
        advanceUntilIdle()

        vm.setNutritionSourceMode(NutritionSourceMode.HYBRID)
        advanceUntilIdle()

        assertEquals(NutritionSourceMode.HYBRID, prefs.state.value.nutritionSourceMode)
        assertTrue(prefs.state.value.aiEnabled)
    }

    @Test
    fun `choosing the local catalogue turns ai off`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setNutritionSourceMode(NutritionSourceMode.LOCAL_DATABASE)
        advanceUntilIdle()

        assertEquals(NutritionSourceMode.LOCAL_DATABASE, prefs.state.value.nutritionSourceMode)
        assertFalse(prefs.state.value.aiEnabled)
    }

    @Test
    fun `the backup file name is dated`() = runTest {
        val vm = viewModel()
        assertEquals("opencalori-backup-2026-08-12.json", vm.defaultBackupFileName("2026-08-12"))
    }
}
