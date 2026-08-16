package com.opencalori.app.ui.scanner

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.image.ImageProcessor
import com.opencalori.app.data.network.aiUserMessage
import com.opencalori.app.data.repository.LocalNutritionResolver
import com.opencalori.app.data.repository.PhotoNutritionResolver
import com.opencalori.app.domain.model.Dish
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.NutritionSourceMode
import com.opencalori.app.domain.model.PhotoQuality
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.RecognizedIngredient
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.repository.AiRepository
import com.opencalori.app.domain.repository.ApiConfigStore
import com.opencalori.app.domain.repository.MealDishItems
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
import java.util.UUID
import javax.inject.Inject

enum class ScannerStage {
    NOT_CONFIGURED,      // no BYOK key yet - dead end without this stage
    CAPTURE,             // camera preview
    ANALYZING_1,         // stage-1: dishes + ingredients
    REVIEW_DISHES,       // stage-3: user confirms the list of recognised dishes
    REVIEW_DISH,         // stage-4: user edits one dish name + its ingredients
    ANALYZING_2,         // stage-5: grams/macros estimation, dish by dish
    REVIEW_GRAMS,        // stage-6: user edits raw/cooked grams per dish
    REVIEW_FINAL,        // stage-7: final confirmation with full breakdown
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

/**
 * One dish being reviewed. A photo can hold several of them, and every dish keeps its own
 * ingredients and its own estimated products all the way into the diary - no name concatenation,
 * no shared bucket.
 */
data class DishDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val confidence: Float? = null,
    val ingredients: List<RecognizedIngredient> = emptyList(),
    val estimated: List<EstimatedIngredient> = emptyList(),
    /** Ingredient names the local catalogue could not resolve (local/hybrid modes only). */
    val unmatched: List<String> = emptyList(),
    val localDish: Dish? = null
) {
    val confirmedIngredientNames: List<String>
        get() = ingredients.map { it.name.trim() }.filter { it.isNotEmpty() }

    val isUnknown: Boolean get() = RecognizedDish(name, ingredients).isUnknown
    val isLowConfidence: Boolean get() = confidence != null && confidence < RecognizedDish.LOW_CONFIDENCE

    val totalCalories: Float get() = estimated.sumOf { it.totalCalories.toDouble() }.toFloat()
    val hasAllGrams: Boolean get() = estimated.isNotEmpty() && estimated.all { it.effectiveGrams > 0f }

    fun toRecognizedDish() = RecognizedDish(name, ingredients, confidence, id)

    companion object {
        fun from(dish: RecognizedDish) = DishDraft(
            id = dish.id,
            name = dish.dishName,
            confidence = dish.confidence,
            ingredients = dish.ingredients
        )
    }
}

data class ScannerUiState(
    val stage: ScannerStage = ScannerStage.CAPTURE,
    val photo: ByteArray? = null,
    val photoBase64: String? = null,
    val dishes: List<DishDraft> = emptyList(),
    val currentDishIndex: Int = 0,
    val isLocalDraft: Boolean = false,
    val unmatchedIngredients: List<String> = emptyList(),
    val mealType: MealType = suggestedMealType(),
    val targetDate: LocalDate = LocalDate.now(),
    val error: String? = null,
    val gramsEditMode: GramsEditMode = GramsEditMode.COOKED,
    val nutritionSourceMode: NutritionSourceMode = NutritionSourceMode.AI_ONLY,
    /** The AI step that may be repeated without restarting the photo flow. */
    val failedAiStage: ScannerStage? = null,
    /** Retry of the failed AI step stays available until the user leaves the flow. */
    val manualRetryAvailable: Boolean = false,
    val saving: Boolean = false
) {
    val isAnalyzing: Boolean
        get() = stage == ScannerStage.ANALYZING_1 || stage == ScannerStage.ANALYZING_2

    val currentDish: DishDraft? get() = dishes.getOrNull(currentDishIndex)

    /** The dish currently under review, in the shape the AI layer speaks. */
    val dish: RecognizedDish? get() = currentDish?.toRecognizedDish()

    /** Local catalogue match of the dish under review; always null in AI-only mode. */
    val localDish: Dish? get() = currentDish?.localDish

    /** Every estimated product of every dish, in dish order. Powers totals and the final list. */
    val estimated: List<EstimatedIngredient> get() = dishes.flatMap { it.estimated }

    val hasMultipleDishes: Boolean get() = dishes.size > 1
    val isLastDish: Boolean get() = currentDishIndex >= dishes.lastIndex
    val dishPositionLabel: String get() = "" + (currentDishIndex + 1) + " / " + dishes.size

    val totalCalories: Float get() = estimated.sumOf { it.totalCalories.toDouble() }.toFloat()
    val totalProtein: Float get() = estimated.sumOf { it.totalProtein.toDouble() }.toFloat()
    val totalFat: Float get() = estimated.sumOf { it.totalFat.toDouble() }.toFloat()
    val totalCarbs: Float get() = estimated.sumOf { it.totalCarbs.toDouble() }.toFloat()

    /** Saving is only allowed once every product has a usable weight. */
    val canSave: Boolean get() = dishes.any { it.estimated.isNotEmpty() } &&
        dishes.filter { it.estimated.isNotEmpty() }.all { it.hasAllGrams }

    // ByteArray in a data class needs these to keep equality sane for Compose.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScannerUiState) return false
        return stage == other.stage &&
            photoBase64 == other.photoBase64 &&
            dishes == other.dishes &&
            currentDishIndex == other.currentDishIndex &&
            isLocalDraft == other.isLocalDraft &&
            unmatchedIngredients == other.unmatchedIngredients &&
            mealType == other.mealType &&
            targetDate == other.targetDate &&
            error == other.error &&
            gramsEditMode == other.gramsEditMode &&
            nutritionSourceMode == other.nutritionSourceMode &&
            saving == other.saving
    }

    override fun hashCode(): Int {
        var result = stage.hashCode()
        result = 31 * result + (photoBase64?.hashCode() ?: 0)
        result = 31 * result + dishes.hashCode()
        result = 31 * result + currentDishIndex
        result = 31 * result + isLocalDraft.hashCode()
        result = 31 * result + unmatchedIngredients.hashCode()
        result = 31 * result + mealType.hashCode()
        result = 31 * result + targetDate.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + gramsEditMode.hashCode()
        result = 31 * result + nutritionSourceMode.hashCode()
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
    private var photoQuality: PhotoQuality = PhotoQuality.HIGH

    init {
        viewModelScope.launch {
            userPrefs.profile.collect { profile ->
                photoQuality = profile.photoQuality
                _uiState.update { it.copy(nutritionSourceMode = profile.nutritionSourceMode) }
            }
        }
        viewModelScope.launch {
            if (!apiConfigStore.current().isConfigured) {
                _uiState.update { it.copy(stage = ScannerStage.NOT_CONFIGURED) }
            }
        }
    }

    fun setMealType(type: MealType) = _uiState.update { it.copy(mealType = type) }

    fun setGramsEditMode(mode: GramsEditMode) = _uiState.update { it.copy(gramsEditMode = mode) }

    // ---- Capture ----

    fun onPhotoTaken(imageBytes: ByteArray) {
        analyze { imageProcessor.prepare(imageBytes, photoQuality) }
    }

    fun onImagePicked(uri: Uri) {
        analyze { imageProcessor.prepare(uri, photoQuality) }
    }

    fun onCaptureFailed(message: String?) {
        _uiState.update {
            it.copy(
                stage = ScannerStage.ERROR,
                error = "Не удалось сделать снимок. Попробуйте ещё раз.",
                failedAiStage = null,
                manualRetryAvailable = false
            )
        }
    }

    private fun analyze(prepare: suspend () -> com.opencalori.app.data.image.PreparedImage) {
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                stage = ScannerStage.ANALYZING_1,
                error = null,
                failedAiStage = null,
                manualRetryAvailable = false
            )
        }
        analysisJob = viewModelScope.launch {
            val prepared = runCatching { prepare() }.getOrElse {
                _uiState.update {
                    it.copy(
                        stage = ScannerStage.ERROR,
                        error = "Не удалось подготовить фото. Попробуйте выбрать или сделать другой снимок.",
                        failedAiStage = null,
                        manualRetryAvailable = false
                    )
                }
                return@launch
            }
            // The photo is kept even when recognition fails, so a retry costs no new shot.
            _uiState.update { it.copy(photo = prepared.jpeg, photoBase64 = prepared.base64) }
            runStage1(prepared.base64)
        }
    }

    private suspend fun runStage1(base64: String, manualRetry: Boolean = false) {
        if (!apiConfigStore.current().isConfigured) {
            _uiState.update {
                it.copy(
                    stage = ScannerStage.NOT_CONFIGURED,
                    error = "\u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0438\u0442\u0435 \u0418\u0418 \u0432 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430\u0445, \u0447\u0442\u043e\u0431\u044b \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u0432\u0430\u0442\u044c \u0431\u043b\u044e\u0434\u0430 \u043f\u043e \u0444\u043e\u0442\u043e."
                )
            }
            return
        }
        aiRepository.recognizeDishes(base64)
            .onSuccess { recognized ->
                val drafts = recognized
                    .map { DishDraft.from(it) }
                    .filter { it.name.isNotBlank() || it.ingredients.isNotEmpty() }
                    .filter { it.ingredients.isNotEmpty() }
                if (drafts.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            stage = ScannerStage.ERROR,
                            error = "Еда на фото не распознана. Попробуйте снять ближе и при лучшем свете.",
                            failedAiStage = ScannerStage.ANALYZING_1,
                            manualRetryAvailable = true
                        )
                    }
                    return@onSuccess
                }

                // Reviewing the result is mandatory: several dishes get their own list step so the
                // user can drop or rename one before any grams are estimated.
                _uiState.update {
                    it.copy(
                        dishes = drafts,
                        currentDishIndex = 0,
                        stage = if (drafts.size > 1) ScannerStage.REVIEW_DISHES else ScannerStage.REVIEW_DISH,
                        isLocalDraft = false,
                        unmatchedIngredients = emptyList(),
                        error = null
                    )
                }
            }
            .onFailure { throwable ->
                fail(throwable, "Не удалось распознать еду на фото. Повторите текущий шаг.", ScannerStage.ANALYZING_1)
            }
    }

    // ---- Dish list editing (stage 3) ----

    fun renameDish(index: Int, newName: String) = updateDish(index) { it.copy(name = newName) }

    fun markDishUnknown(index: Int) = updateDish(index) { it.copy(name = RecognizedDish.UNKNOWN_LABEL) }

    fun removeDish(index: Int) {
        _uiState.update { state ->
            if (index !in state.dishes.indices) return@update state
            val remaining = state.dishes.filterIndexed { i, _ -> i != index }
            state.copy(
                dishes = remaining,
                currentDishIndex = state.currentDishIndex.coerceAtMost((remaining.size - 1).coerceAtLeast(0))
            )
        }
    }

    /** Adds a dish the model missed. It starts empty, so the user fills the ingredients in. */
    fun addDish(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { state -> state.copy(dishes = state.dishes + DishDraft(name = trimmed)) }
    }

    /** Opens one dish for ingredient review. */
    fun reviewDish(index: Int) {
        _uiState.update { state ->
            if (index !in state.dishes.indices) return@update state
            state.copy(currentDishIndex = index, stage = ScannerStage.REVIEW_DISH, error = null)
        }
    }

    fun confirmDishList() {
        val state = _uiState.value
        if (state.dishes.isEmpty()) {
            _uiState.update {
                it.copy(error = "\u0421\u043f\u0438\u0441\u043e\u043a \u0431\u043b\u044e\u0434 \u043f\u0443\u0441\u0442. \u0414\u043e\u0431\u0430\u0432\u044c\u0442\u0435 \u0431\u043b\u044e\u0434\u043e \u0438\u043b\u0438 \u043f\u0435\u0440\u0435\u0441\u043d\u0438\u043c\u0438\u0442\u0435 \u0444\u043e\u0442\u043e.")
            }
            return
        }
        _uiState.update { it.copy(currentDishIndex = 0, stage = ScannerStage.REVIEW_DISH, error = null) }
    }

    /** Back from a single dish to the dish list, when there is more than one dish. */
    fun backToDishList() {
        _uiState.update { state ->
            if (!state.hasMultipleDishes) state
            else state.copy(stage = ScannerStage.REVIEW_DISHES, error = null)
        }
    }

    // ---- Ingredient editing of the dish under review (stage 4) ----

    fun updateDishName(newName: String) = updateDish(_uiState.value.currentDishIndex) { it.copy(name = newName) }

    fun updateIngredient(index: Int, newName: String) = updateCurrentDish { dish ->
        if (index !in dish.ingredients.indices) dish
        else dish.copy(
            ingredients = dish.ingredients.toMutableList().also { it[index] = it[index].copy(name = newName) }
        )
    }

    fun removeIngredient(index: Int) = updateCurrentDish { dish ->
        if (index !in dish.ingredients.indices) dish
        else dish.copy(ingredients = dish.ingredients.filterIndexed { i, _ -> i != index })
    }

    fun addIngredient(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updateCurrentDish { dish -> dish.copy(ingredients = dish.ingredients + RecognizedIngredient(trimmed)) }
    }

    /**
     * "Next" from the ingredient review. With several dishes this walks to the next dish first,
     * and only estimates nutrition once every dish has been confirmed.
     */
    fun confirmIngredientsAndEstimate() {
        val state = _uiState.value
        val photo = state.photoBase64 ?: return
        val dish = state.currentDish ?: return
        if (dish.confirmedIngredientNames.isEmpty()) {
            _uiState.update { it.copy(error = "\u0421\u043f\u0438\u0441\u043e\u043a \u0438\u043d\u0433\u0440\u0435\u0434\u0438\u0435\u043d\u0442\u043e\u0432 \u043f\u0443\u0441\u0442") }
            return
        }
        if (!state.isLastDish) {
            _uiState.update { it.copy(currentDishIndex = it.currentDishIndex + 1, error = null) }
            return
        }
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch { runStage2(photo) }
    }

    private suspend fun runStage2(photo: String, manualRetry: Boolean = false) {
        _uiState.update {
            it.copy(
                stage = ScannerStage.ANALYZING_2,
                error = null,
                failedAiStage = null,
                manualRetryAvailable = false
            )
        }

        val dishes = _uiState.value.dishes.filter { it.confirmedIngredientNames.isNotEmpty() }
        if (dishes.isEmpty()) {
            _uiState.update {
                it.copy(stage = ScannerStage.REVIEW_DISH, error = "\u0421\u043f\u0438\u0441\u043e\u043a \u0438\u043d\u0433\u0440\u0435\u0434\u0438\u0435\u043d\u0442\u043e\u0432 \u043f\u0443\u0441\u0442")
            }
            return
        }

        // Recognition always uses the configured vision provider. The nutrition source below is
        // selected explicitly by the user: AI-only estimates macros with the model, while local
        // and hybrid resolve macros through the local catalogue.
        val nutritionMode = profile().nutritionSourceMode
        val resolved = mutableListOf<DishDraft>()

        dishes.forEachIndexed { index, dish ->
            val items = dish.confirmedIngredientNames
            if (nutritionMode == NutritionSourceMode.AI_ONLY) {
                val estimate = try {
                    aiRepository.estimateNutrition(photo, dish.name, items)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                if (estimate.isFailure) {
                    fail(
                        estimate.exceptionOrNull() ?: IllegalStateException(),
                        "Не удалось рассчитать КБЖУ. Повторите текущий шаг.",
                        ScannerStage.ANALYZING_2
                    )
                    return
                }
                val estimated = estimate.getOrThrow()
                if (estimated.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            stage = ScannerStage.REVIEW_DISH,
                            currentDishIndex = index,
                            error = "\u0418\u0418 \u043d\u0435 \u0432\u0435\u0440\u043d\u0443\u043b \u043f\u0440\u043e\u0434\u0443\u043a\u0442\u044b \u0434\u043b\u044f \u0440\u0430\u0441\u0447\u0451\u0442\u0430. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u0441\u043e\u0441\u0442\u0430\u0432 \u0438 \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u0435 \u043f\u043e\u043f\u044b\u0442\u043a\u0443."
                        )
                    }
                    return
                }
                resolved += dish.copy(estimated = estimated, localDish = null, unmatched = emptyList())
                return@forEachIndexed
            }

            val localResolution = try {
                photoNutritionResolver.resolve(dish.name, items)
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
                        currentDishIndex = index,
                        isLocalDraft = true,
                        unmatchedIngredients = localResolution.unmatchedNames,
                        dishes = it.dishes.mapIndexed { i, draft ->
                            if (i == index) {
                                draft.copy(
                                    estimated = localResolution.items,
                                    unmatched = localResolution.unmatchedNames,
                                    localDish = null
                                )
                            } else {
                                draft
                            }
                        }
                    )
                }
                return
            }
            resolved += dish.copy(
                estimated = localResolution.items,
                localDish = localResolution.matchedDish,
                unmatched = emptyList()
            )
        }

        val anyDraft = resolved.any { it.localDish == null } && nutritionMode != NutritionSourceMode.AI_ONLY
        _uiState.update {
            it.copy(
                dishes = resolved,
                currentDishIndex = 0,
                isLocalDraft = anyDraft,
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

    // ---- Grams editing (stage 6) ----

    /** Per-dish edit used by the grouped grams screen. */
    fun setGramsFor(dishIndex: Int, itemIndex: Int, grams: Float) {
        _uiState.update { state ->
            val dish = state.dishes.getOrNull(dishIndex) ?: return@update state
            if (itemIndex !in dish.estimated.indices) return@update state
            val current = dish.estimated[itemIndex]
            val updated = when (state.gramsEditMode) {
                GramsEditMode.COOKED -> current.withCookedGrams(grams)
                GramsEditMode.RAW -> current.withRawGrams(grams)
            }
            state.replaceDish(dishIndex) { d ->
                d.copy(estimated = d.estimated.toMutableList().also { it[itemIndex] = updated })
            }
        }
    }

    fun updateEstimatedFor(dishIndex: Int, itemIndex: Int, item: EstimatedIngredient) {
        _uiState.update { state ->
            val dish = state.dishes.getOrNull(dishIndex) ?: return@update state
            if (itemIndex !in dish.estimated.indices) return@update state
            state.replaceDish(dishIndex) { d ->
                d.copy(estimated = d.estimated.toMutableList().also { it[itemIndex] = item })
            }
        }
    }

    fun removeEstimatedFor(dishIndex: Int, itemIndex: Int) {
        _uiState.update { state ->
            val dish = state.dishes.getOrNull(dishIndex) ?: return@update state
            if (itemIndex !in dish.estimated.indices) return@update state
            state.replaceDish(dishIndex) { d ->
                d.copy(estimated = d.estimated.filterIndexed { i, _ -> i != itemIndex })
            }
        }
    }

    /** Flat-index variants, addressing products across all dishes in display order. */
    fun setGrams(index: Int, grams: Float) = withFlatIndex(index) { dishIndex, itemIndex ->
        setGramsFor(dishIndex, itemIndex, grams)
    }

    fun updateEstimated(index: Int, item: EstimatedIngredient) = withFlatIndex(index) { dishIndex, itemIndex ->
        updateEstimatedFor(dishIndex, itemIndex, item)
    }

    fun removeEstimated(index: Int) = withFlatIndex(index) { dishIndex, itemIndex ->
        removeEstimatedFor(dishIndex, itemIndex)
    }

    private inline fun withFlatIndex(index: Int, action: (Int, Int) -> Unit) {
        var remaining = index
        _uiState.value.dishes.forEachIndexed { dishIndex, dish ->
            if (remaining < dish.estimated.size) {
                action(dishIndex, remaining)
                return
            }
            remaining -= dish.estimated.size
        }
    }

    /** "Next" from the grams screen - honours the "skip final confirmation" switch. */
    fun proceedToFinal() {
        viewModelScope.launch {
            if (profile().aiSkipFinalReview) {
                saveMeal()
            } else {
                _uiState.update { it.copy(stage = ScannerStage.REVIEW_FINAL) }
            }
        }
    }

    /** Writes one diary entry per dish, so the diary keeps meal -> dish -> products. */
    fun saveMeal() {
        val state = _uiState.value
        val dishes = state.dishes.filter { it.estimated.isNotEmpty() }
        if (dishes.isEmpty() || state.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            mealRepository.addDishItems(
                epochDay = targetEpochDay,
                mealType = state.mealType,
                dishes = dishes.map { dish ->
                    MealDishItems(
                        dishName = dish.name.takeIf { it.isNotBlank() },
                        items = dish.estimated.map { it.toFoodItem() }
                    )
                }
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
                targetDate = it.targetDate,
                nutritionSourceMode = it.nutritionSourceMode
            )
        }
    }

    /** Retries only the failed AI step; confirmed dishes, ingredients and the photo stay intact. */
    fun retry() {
        val state = _uiState.value
        val photo = state.photoBase64
        val failedStage = state.failedAiStage
        if (photo == null || failedStage == null) return
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                stage = failedStage,
                error = null,
                failedAiStage = null,
                manualRetryAvailable = true
            )
        }
        analysisJob = viewModelScope.launch {
            when (failedStage) {
                ScannerStage.ANALYZING_1 -> runStage1(photo, manualRetry = true)
                ScannerStage.ANALYZING_2 -> runStage2(photo, manualRetry = true)
                else -> Unit
            }
        }
    }

    fun onApiConfigured() {
        viewModelScope.launch {
            if (apiConfigStore.current().isConfigured) {
                _uiState.update { it.copy(stage = ScannerStage.CAPTURE, error = null) }
            }
        }
    }

    private suspend fun profile(): UserProfile = userPrefs.profile.first()

    private fun updateCurrentDish(transform: (DishDraft) -> DishDraft) =
        updateDish(_uiState.value.currentDishIndex, transform)

    private fun updateDish(index: Int, transform: (DishDraft) -> DishDraft) {
        _uiState.update { state ->
            if (index !in state.dishes.indices) state else state.replaceDish(index, transform)
        }
    }

    private fun fail(throwable: Throwable, fallback: String, failedStage: ScannerStage? = null) {
        if (throwable is CancellationException) throw throwable
        _uiState.update {
            it.copy(
                stage = ScannerStage.ERROR,
                error = throwable.aiUserMessage(fallback),
                failedAiStage = failedStage,
                manualRetryAvailable = failedStage != null
            )
        }
    }
}

private fun ScannerUiState.replaceDish(index: Int, transform: (DishDraft) -> DishDraft): ScannerUiState =
    copy(dishes = dishes.mapIndexed { i, dish -> if (i == index) transform(dish) else dish })
