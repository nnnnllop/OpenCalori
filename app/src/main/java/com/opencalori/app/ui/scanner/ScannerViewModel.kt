package com.opencalori.app.ui.scanner

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.image.ImageProcessor
import com.opencalori.app.data.repository.LocalNutritionResolver
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.NutritionSourceMode
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.RecognizedIngredient
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.repository.AiRepository
import com.opencalori.app.domain.repository.ApiConfigStore
import com.opencalori.app.domain.repository.MealRepository
import com.opencalori.app.domain.repository.UserPreferences
import com.opencalori.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class ScannerStage {
    NOT_CONFIGURED,      // no BYOK key yet - dead end without this stage
    CAPTURE,             // camera preview
    ANALYZING_1,         // stage-1: dish name + ingredients
    REVIEW_DISH,         // user edits dish name + ingredient list
    ANALYZING_2,         // stage-2: grams/macros estimation
    REVIEW_GRAMS,        // user edits raw/cooked grams
    REVIEW_FINAL,        // final confirmation with full breakdown
    SAVED,
    ERROR
}

enum class GramsEditMode { RAW, COOKED }

internal fun suggestedMealType(time: LocalTime = LocalTime.now()): MealType = when (time.hour) {
    in 5..10 -> MealType.BREAKFAST
    in 11..15 -> MealType.LUNCH
    in 16..21 -> MealType.DINNER
    else -> MealType.SNACK
}

data class ScannerUiState(
    val stage: ScannerStage = ScannerStage.CAPTURE,
    val photo: ByteArray? = null,
    val photoBase64: String? = null,
    val dish: RecognizedDish? = null,
    val estimated: List<EstimatedIngredient> = emptyList(),
    val mealType: MealType = suggestedMealType(),
    val targetDate: LocalDate = LocalDate.now(),
    val error: String? = null,
    val gramsEditMode: GramsEditMode = GramsEditMode.COOKED,
    val saving: Boolean = false
) {
    val isAnalyzing: Boolean
        get() = stage == ScannerStage.ANALYZING_1 || stage == ScannerStage.ANALYZING_2

    val totalCalories: Float get() = estimated.sumOf { it.totalCalories.toDouble() }.toFloat()
    val totalProtein: Float get() = estimated.sumOf { it.totalProtein.toDouble() }.toFloat()
    val totalFat: Float get() = estimated.sumOf { it.totalFat.toDouble() }.toFloat()
    val totalCarbs: Float get() = estimated.sumOf { it.totalCarbs.toDouble() }.toFloat()

    // ByteArray in a data class needs these to keep equality sane for Compose.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScannerUiState) return false
        return stage == other.stage &&
            photoBase64 == other.photoBase64 &&
            dish == other.dish &&
            estimated == other.estimated &&
            mealType == other.mealType &&
            targetDate == other.targetDate &&
            error == other.error &&
            gramsEditMode == other.gramsEditMode &&
            saving == other.saving
    }

    override fun hashCode(): Int {
        var result = stage.hashCode()
        result = 31 * result + (photoBase64?.hashCode() ?: 0)
        result = 31 * result + (dish?.hashCode() ?: 0)
        result = 31 * result + estimated.hashCode()
        result = 31 * result + mealType.hashCode()
        result = 31 * result + targetDate.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + gramsEditMode.hashCode()
        result = 31 * result + saving.hashCode()
        return result
    }
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val mealRepository: MealRepository,
    private val imageProcessor: ImageProcessor,
    private val userPrefs: UserPreferences,
    private val apiConfigStore: ApiConfigStore,
    private val localNutritionResolver: LocalNutritionResolver,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val targetEpochDay: Long =
        savedStateHandle.get<Long>(Routes.ARG_DATE) ?: LocalDate.now().toEpochDay()

    private val _uiState = MutableStateFlow(ScannerUiState(targetDate = LocalDate.ofEpochDay(targetEpochDay)))
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    init {
        viewModelScope.launch {
            val mode = profile().nutritionSourceMode
            if (mode.usesAi && !apiConfigStore.current().isConfigured) {
                _uiState.update { it.copy(stage = ScannerStage.NOT_CONFIGURED) }
            }
        }
    }

    fun setMealType(type: MealType) = _uiState.update { it.copy(mealType = type) }

    fun setGramsEditMode(mode: GramsEditMode) = _uiState.update { it.copy(gramsEditMode = mode) }

    // ---- Capture ----

    fun onPhotoTaken(imageBytes: ByteArray) {
        analyze { imageProcessor.prepare(imageBytes) }
    }

    fun onImagePicked(uri: Uri) {
        analyze { imageProcessor.prepare(uri) }
    }

    fun onCaptureFailed(message: String?) {
        _uiState.update {
            it.copy(
                stage = ScannerStage.ERROR,
                error = message ?: "Не удалось сделать снимок. Попробуйте ещё раз."
            )
        }
    }

    private fun analyze(prepare: suspend () -> com.opencalori.app.data.image.PreparedImage) {
        analysisJob?.cancel()
        _uiState.update { it.copy(stage = ScannerStage.ANALYZING_1, error = null) }
        analysisJob = viewModelScope.launch {
            val prepared = runCatching { prepare() }.getOrElse { error ->
                _uiState.update {
                    it.copy(stage = ScannerStage.ERROR, error = error.message ?: "Не удалось обработать фото")
                }
                return@launch
            }
            _uiState.update { it.copy(photo = prepared.jpeg, photoBase64 = prepared.base64) }
            runStage1(prepared.base64)
        }
    }

    private suspend fun runStage1(base64: String) {
        if (!profile().nutritionSourceMode.usesAi) {
            _uiState.update {
                it.copy(
                    stage = ScannerStage.ERROR,
                    error = "\u0412 л\u043e\u043a\u0430\u043b\u044c\u043d\u043e\u043c р\u0435\u0436\u0438\u043c\u0435 ф\u043e\u0442\u043e н\u0435 о\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0435\u0442\u0441\u044f в И\u0418. Н\u0430\u0439\u0434\u0438\u0442\u0435 п\u0440\u043e\u0434\u0443\u043a\u0442 вр\u0443\u0447\u043d\u0443\u044e ил\u0438 в\u044b\u0431\u0435\u0440\u0438\u0442\u0435 к\u043e\u043c\u0431\u0438\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u044b\u0439 р\u0435\u0436\u0438\u043c."
                )
            }
            return
        }
        aiRepository.recognizeDish(base64)
            .onSuccess { dish ->
                if (dish.ingredients.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            stage = ScannerStage.ERROR,
                            error = "Еда на фото не распознана. Попробуйте снять ближе и при лучшем свете."
                        )
                    }
                    return@onSuccess
                }

                val skipReview = profile().aiSkipListReview
                _uiState.update {
                    it.copy(
                        dish = dish,
                        stage = if (skipReview) ScannerStage.ANALYZING_2 else ScannerStage.REVIEW_DISH
                    )
                }
                if (skipReview) {
                    runStage2(base64, dish.dishName, dish.ingredients.map { it.name })
                }
            }
            .onFailure { throwable -> fail(throwable, "Ошибка распознавания") }
    }

    // ---- Ingredient editing ----

    fun updateDishName(newName: String) {
        _uiState.update { state -> state.copy(dish = state.dish?.copy(dishName = newName)) }
    }

    fun updateIngredient(index: Int, newName: String) {
        _uiState.update { state ->
            val dish = state.dish ?: return@update state
            if (index !in dish.ingredients.indices) return@update state
            val updated = dish.ingredients.toMutableList()
            updated[index] = updated[index].copy(name = newName)
            state.copy(dish = dish.copy(ingredients = updated))
        }
    }

    fun removeIngredient(index: Int) {
        _uiState.update { state ->
            val dish = state.dish ?: return@update state
            if (index !in dish.ingredients.indices) return@update state
            state.copy(dish = dish.copy(ingredients = dish.ingredients.filterIndexed { i, _ -> i != index }))
        }
    }

    fun addIngredient(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { state ->
            val dish = state.dish ?: return@update state
            state.copy(dish = dish.copy(ingredients = dish.ingredients + RecognizedIngredient(trimmed)))
        }
    }

    fun confirmIngredientsAndEstimate() {
        val state = _uiState.value
        val photo = state.photoBase64 ?: return
        val dish = state.dish ?: return
        val items = dish.ingredients.map { it.name.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) {
            _uiState.update { it.copy(stage = ScannerStage.ERROR, error = "Список ингредиентов пуст") }
            return
        }
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch { runStage2(photo, dish.dishName, items) }
    }

    private suspend fun runStage2(photo: String, dishName: String, items: List<String>) {
        _uiState.update { it.copy(stage = ScannerStage.ANALYZING_2, error = null) }
        val aiEstimate = aiRepository.estimateNutrition(photo, dishName, items)
        if (aiEstimate.isFailure) {
            fail(aiEstimate.exceptionOrNull() ?: IllegalStateException(), "\\u041e\\u0448\\u0438\\u0431\\u043a\\u0430 \u043e\\u0446\\u0435\\u043d\\u043a\\u0438 \u041a\\u0411\\u0416\\u0423")
            return
        }
        val mode = profile().nutritionSourceMode
        val estimated = aiEstimate.getOrThrow()
        val finalEstimate = if (mode.usesLocalCatalogue) {
            val local = localNutritionResolver.replaceMacros(estimated)
            if (!local.isComplete) {
                _uiState.update {
                    it.copy(
                        stage = ScannerStage.ERROR,
                        error = "\\u041d\\u0435 \u043d\\u0430\\u0439\\u0434\\u0435\\u043d\\u044b \u043f\\u0440\\u043e\\u0434\\u0443\\u043a\\u0442\\u044b \u0432 \u043b\\u043e\\u043a\\u0430\\u043b\\u044c\\u043d\\u043e\\u0439 \u0431\\u0430\\u0437\\u0435: " + local.unmatchedNames.joinToString()
                    )
                }
                return
            }
            local.resolved
        } else {
            estimated
        }
        _uiState.update { it.copy(estimated = finalEstimate) }
        val profile = profile()
        when {
            profile.aiSkipGramsReview && profile.aiSkipFinalReview -> saveMeal()
            profile.aiSkipGramsReview -> _uiState.update { it.copy(stage = ScannerStage.REVIEW_FINAL) }
            else -> _uiState.update { it.copy(stage = ScannerStage.REVIEW_GRAMS) }
        }
    }
    // ---- Grams editing ----

    fun updateEstimated(index: Int, item: EstimatedIngredient) {
        _uiState.update { state ->
            if (index !in state.estimated.indices) return@update state
            state.copy(estimated = state.estimated.toMutableList().also { it[index] = item })
        }
    }

    fun setGrams(index: Int, grams: Float) {
        _uiState.update { state ->
            if (index !in state.estimated.indices) return@update state
            val current = state.estimated[index]
            val updated = when (state.gramsEditMode) {
                GramsEditMode.COOKED -> current.withCookedGrams(grams)
                GramsEditMode.RAW -> current.withRawGrams(grams)
            }
            state.copy(estimated = state.estimated.toMutableList().also { it[index] = updated })
        }
    }

    fun removeEstimated(index: Int) {
        _uiState.update { state ->
            if (index !in state.estimated.indices) return@update state
            state.copy(estimated = state.estimated.filterIndexed { i, _ -> i != index })
        }
    }

    /** "Далее" from the grams screen - honours the "skip final confirmation" switch. */
    fun proceedToFinal() {
        viewModelScope.launch {
            if (profile().aiSkipFinalReview) {
                saveMeal()
            } else {
                _uiState.update { it.copy(stage = ScannerStage.REVIEW_FINAL) }
            }
        }
    }

    fun saveMeal() {
        val state = _uiState.value
        if (state.estimated.isEmpty() || state.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            mealRepository.addItems(
                epochDay = targetEpochDay,
                mealType = state.mealType,
                items = state.estimated.map { it.toFoodItem() }
            )
            _uiState.update { it.copy(stage = ScannerStage.SAVED, saving = false) }
        }
    }

    // ---- Flow control ----

    /** Stops an in-flight request and returns to the viewfinder. */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        _uiState.update { it.copy(stage = ScannerStage.CAPTURE, error = null) }
    }

    fun retake() {
        analysisJob?.cancel()
        analysisJob = null
        _uiState.update {
            ScannerUiState(stage = ScannerStage.CAPTURE, mealType = it.mealType)
        }
    }

    /** Retries the failed step without making the user shoot the plate again. */
    fun retry() {
        val base64 = _uiState.value.photoBase64
        if (base64 == null) {
            retake()
            return
        }
        analysisJob?.cancel()
        _uiState.update { it.copy(stage = ScannerStage.ANALYZING_1, error = null) }
        analysisJob = viewModelScope.launch { runStage1(base64) }
    }

    fun onApiConfigured() {
        viewModelScope.launch {
            if (apiConfigStore.current().isConfigured) {
                _uiState.update { it.copy(stage = ScannerStage.CAPTURE, error = null) }
            }
        }
    }

    private suspend fun profile(): UserProfile = userPrefs.profile.first()

    private fun fail(throwable: Throwable, fallback: String) {
        if (throwable is CancellationException) throw throwable
        _uiState.update {
            it.copy(stage = ScannerStage.ERROR, error = throwable.message ?: fallback)
        }
    }
}
