package com.opencalori.app.ui.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.preferences.UserPreferencesStore
import com.opencalori.app.data.repository.AiRepository
import com.opencalori.app.data.repository.MealRepository
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.inject.Inject

enum class ScannerStage {
    CAPTURE,             // camera preview
    ANALYZING_1,         // stage-1: dish name + ingredients
    REVIEW_DISH,         // user edits dish name + ingredient list
    ANALYZING_2,         // stage-2: grams/macros estimation
    REVIEW_GRAMS,        // user edits raw/cooked grams
    REVIEW_FINAL,        // final confirmation with full breakdown
    SAVED,
    ERROR
}

data class ScannerUiState(
    val stage: ScannerStage = ScannerStage.CAPTURE,
    val photoBase64: String? = null,
    val dish: RecognizedDish? = null,
    val estimated: List<EstimatedIngredient> = emptyList(),
    val mealType: MealType = MealType.SNACK,
    val error: String? = null,
    // which grams mode is shown in REVIEW_GRAMS
    val gramsEditMode: GramsEditMode = GramsEditMode.COOKED
)

enum class GramsEditMode { RAW, COOKED }

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val mealRepository: MealRepository,
    userPrefs: UserPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val profile: StateFlow<UserProfile?> = userPrefs.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setMealType(type: MealType) = _uiState.update { it.copy(mealType = type) }
    fun setGramsEditMode(mode: GramsEditMode) = _uiState.update { it.copy(gramsEditMode = mode) }

    fun onPhotoTaken(imageBytes: ByteArray) {
        val base64 = compressAndEncode(imageBytes)
        _uiState.update { it.copy(photoBase64 = base64, stage = ScannerStage.ANALYZING_1) }
        runStage1(base64)
    }

    private fun runStage1(base64: String) {
        viewModelScope.launch {
            aiRepository.recognizeDish(base64)
                .onSuccess { dish ->
                    if (dish.ingredients.isEmpty()) {
                        _uiState.update { it.copy(stage = ScannerStage.ERROR, error = "Еда не распознана. Попробуйте другое фото.") }
                    } else {
                        val prof = profile.value
                        val nextStage = if (prof?.aiSkipListReview == true) {
                            // auto-proceed to stage 2
                            _uiState.value.copy(dish = dish).also {
                                viewModelScope.launch { runStage2(base64, dish.dishName, dish.ingredients.map { i -> i.name }) }
                            }
                            ScannerStage.ANALYZING_2
                        } else {
                            ScannerStage.REVIEW_DISH
                        }
                        _uiState.update { it.copy(stage = nextStage, dish = dish) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(stage = ScannerStage.ERROR, error = e.message ?: "Ошибка распознавания") }
                }
        }
    }

    fun updateDishName(newName: String) {
        _uiState.update { s -> s.copy(dish = s.dish?.copy(dishName = newName)) }
    }

    fun updateIngredient(index: Int, newName: String) {
        _uiState.update { s ->
            s.copy(dish = s.dish?.copy(
                ingredients = s.dish.ingredients.toMutableList().also {
                    if (index in it.indices) it[index] = it[index].copy(name = newName)
                }
            ))
        }
    }

    fun removeIngredient(index: Int) {
        _uiState.update { s ->
            s.copy(dish = s.dish?.copy(
                ingredients = s.dish.ingredients.toMutableList().also {
                    if (index in it.indices) it.removeAt(index)
                }
            ))
        }
    }

    fun addIngredient(name: String) {
        if (name.isBlank()) return
        _uiState.update { s ->
            s.copy(dish = s.dish?.copy(
                ingredients = s.dish.ingredients + com.opencalori.app.domain.model.RecognizedIngredient(name.trim())
            ))
        }
    }

    fun confirmIngredientsAndEstimate() {
        val s = _uiState.value
        val photo = s.photoBase64 ?: return
        val dish = s.dish ?: return
        val items = dish.ingredients.map { it.name }.filter { it.isNotBlank() }
        if (items.isEmpty()) {
            _uiState.update { it.copy(stage = ScannerStage.ERROR, error = "Список ингредиентов пуст") }
            return
        }
        _uiState.update { it.copy(stage = ScannerStage.ANALYZING_2) }
        runStage2(photo, dish.dishName, items)
    }

    private fun runStage2(photo: String, dishName: String, items: List<String>) {
        viewModelScope.launch {
            aiRepository.estimateNutrition(photo, dishName, items)
                .onSuccess { list ->
                    val prof = profile.value
                    val nextStage = when {
                        prof?.aiSkipGramsReview == true && prof.aiSkipFinalReview == true -> {
                            // skip both reviews — save immediately
                            _uiState.value.copy(estimated = list).also {
                                viewModelScope.launch { saveMealInternal(list) }
                            }
                            ScannerStage.SAVED
                        }
                        prof?.aiSkipGramsReview == true -> ScannerStage.REVIEW_FINAL
                        else -> ScannerStage.REVIEW_GRAMS
                    }
                    _uiState.update { it.copy(stage = nextStage, estimated = list) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(stage = ScannerStage.ERROR, error = e.message ?: "Ошибка оценки") }
                }
        }
    }

    fun updateEstimated(index: Int, item: EstimatedIngredient) {
        _uiState.update { s ->
            s.copy(estimated = s.estimated.toMutableList().also {
                if (index in it.indices) it[index] = item
            })
        }
    }

    fun removeEstimated(index: Int) {
        _uiState.update { s ->
            s.copy(estimated = s.estimated.toMutableList().also {
                if (index in it.indices) it.removeAt(index)
            })
        }
    }

    fun proceedToFinal() {
        _uiState.update { it.copy(stage = ScannerStage.REVIEW_FINAL) }
    }

    fun saveMeal(onDone: () -> Unit) {
        val items = _uiState.value.estimated
        if (items.isEmpty()) return
        viewModelScope.launch {
            saveMealInternal(items)
            onDone()
        }
    }

    private suspend fun saveMealInternal(items: List<EstimatedIngredient>) {
        val s = _uiState.value
        val foodItems = items.map {
            FoodItem(
                name = it.name,
                grams = it.effectiveGrams,
                caloriesPer100g = it.caloriesPer100g,
                proteinPer100g = it.proteinPer100g,
                fatPer100g = it.fatPer100g,
                carbsPer100g = it.carbsPer100g
            )
        }
        mealRepository.addMeal(
            Meal(
                dateEpochDay = LocalDate.now().toEpochDay(),
                mealType = s.mealType,
                items = foodItems
            )
        )
        _uiState.update { it.copy(stage = ScannerStage.SAVED) }
    }

    fun retry() {
        _uiState.update { it.copy(stage = ScannerStage.CAPTURE, error = null) }
    }

    fun reset() {
        _uiState.value = ScannerUiState()
    }

    private fun compressAndEncode(imageBytes: ByteArray, maxDim: Int = 1024): String {
        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val scale = maxOf(bmp.width, bmp.height).toFloat() / maxDim
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width / scale).toInt().coerceAtLeast(1),
                (bmp.height / scale).toInt().coerceAtLeast(1),
                true
            )
        } else bmp
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
