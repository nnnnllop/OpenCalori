package com.opencalori.app.ui.scanner

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.image.ImageProcessor
import com.opencalori.app.data.repository.LocalNutritionResolver
import com.opencalori.app.data.repository.PhotoNutritionResolver
import com.opencalori.app.domain.model.Dish
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.MealType
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
    val localDish: Dish? = null,
    val isLocalDraft: Boolean = false,
    val unmatchedIngredients: List<String> = emptyList(),
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
            localDish == other.localDish &&
            isLocalDraft == other.isLocalDraft &&
            unmatchedIngredients == other.unmatchedIngredients &&
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
        result = 31 * result + (localDish?.hashCode() ?: 0)
        result = 31 * result + isLocalDraft.hashCode()
        result = 31 * result + unmatchedIngredients.hashCode()
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
    private val photoNutritionResolver: PhotoNutritionResolver,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val targetEpochDay: Long =
        savedStateHandle.get<Long>(Routes.ARG_DATE) ?: LocalDate.now().toEpochDay()

    private val _uiState = MutableStateFlow(ScannerUiState(targetDate = LocalDate.ofEpochDay(targetEpochDay)))
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    init {
        viewModelScope.launch {
            if (!apiConfigStore.current().isConfigured) {
                _uiState.update { it.copy(stage = ScannerStage.NOT_CONFIGURED) }
            }
        }
    }    fun setMealType(type: MealType) = _uiState.update { it.copy(mealType = type) }

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
        if (!apiConfigStore.current().isConfigured) {
            _uiState.update {
                it.copy(
                    stage = ScannerStage.NOT_CONFIGURED,
                    error = "\u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0438\u0442\u0435 \u0418\u0418 \u0432 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430\u0445, \u0447\u0442\u043e\u0431\u044b \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u0432\u0430\u0442\u044c \u0431\u043b\u044e\u0434\u0430 \u043f\u043e \u0444\u043e\u0442\u043e."
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

                // The list review is mandatory for photo results: the user must see whether a
                // canonical local dish was found or an unknown result remains a local draft.
                _uiState.update {
                    it.copy(
                        dish = dish,
                        stage = ScannerStage.REVIEW_DISH,
                        localDish = null,
                        isLocalDraft = false,
                        unmatchedIngredients = emptyList(),
                        estimated = emptyList()
                    )
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

        // A configured provider is authoritative for the photo flow: recognition and nutrition
        // estimation both come from the AI. The local catalogue remains a safe fallback only
        // when the provider is unavailable, and every repository failure is converted into a UI
        // error instead of escaping the ViewModel coroutine and closing the application.
        if (apiConfigStore.current().isConfigured) {
            val aiEstimate = try {
                aiRepository.estimateNutrition(photo, dishName, items)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (aiEstimate.isFailure) {
                fail(
                    aiEstimate.exceptionOrNull() ?: IllegalStateException(),
                    "\u041e\u0448\u0438\u0431\u043a\u0430 \u043e\u0446\u0435\u043d\u043a\u0438 \u041a\u0411\u0416\u0423 \u0447\u0435\u0440\u0435\u0437 \u0418\u0418"
                )
                return
            }
            val estimated = aiEstimate.getOrThrow()
            if (estimated.isEmpty()) {
                _uiState.update {
                    it.copy(
                        stage = ScannerStage.REVIEW_DISH,
                        error = "\u0418\u0418 \u043d\u0435 \u0432\u0435\u0440\u043d\u0443\u043b \u043f\u0440\u043e\u0434\u0443\u043a\u0442\u044b \u0434\u043b\u044f \u0440\u0430\u0441\u0447\u0451\u0442\u0430. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u0441\u043e\u0441\u0442\u0430\u0432 \u0438 \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u0435 \u043f\u043e\u043f\u044b\u0442\u043a\u0443."
                    )
                }
                return
            }
            _uiState.update {
                it.copy(
                    estimated = estimated,
                    localDish = null,
                    isLocalDraft = false,
                    unmatchedIngredients = emptyList()
                )
            }
            openEstimatedResult()
            return
        }

        val localResolution = try {
            photoNutritionResolver.resolve(dishName, items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            fail(error, "\u041e\u0448\u0438\u0431\u043a\u0430 \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u043e\u0433\u043e \u0441\u043e\u043f\u043e\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u0438\u044f \u043f\u0440\u043e\u0434\u0443\u043a\u0442\u043e\u0432")
            return
        }
        if (!localResolution.isComplete) {
            _uiState.update {
                it.copy(
                    stage = ScannerStage.REVIEW_DISH,
                    localDish = null,
                    isLocalDraft = true,
                    unmatchedIngredients = localResolution.unmatchedNames,
                    estimated = localResolution.items
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                estimated = localResolution.items,
                localDish = localResolution.matchedDish,
                isLocalDraft = localResolution.isDraft,
                unmatchedIngredients = emptyList()
            )
        }
        openEstimatedResult()
    }

    private suspend fun openEstimatedResult() {
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
            ScannerUiState(
                stage = ScannerStage.CAPTURE,
                mealType = it.mealType,
                targetDate = it.targetDate
            )
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
