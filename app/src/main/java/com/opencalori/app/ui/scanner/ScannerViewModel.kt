package com.opencalori.app.ui.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.repository.AiRepository
import com.opencalori.app.data.repository.MealRepository
import com.opencalori.app.domain.model.EstimatedFoodItem
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.RecognizedFood
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.inject.Inject

enum class ScannerStage {
    CAPTURE,            // camera preview
    ANALYZING_1,        // stage-1 recognition
    REVIEW_LIST,        // user edits recognized list
    ANALYZING_2,        // stage-2 estimation
    REVIEW_NUTRITION,   // user confirms grams/macros
    SAVED,
    ERROR
}

data class ScannerUiState(
    val stage: ScannerStage = ScannerStage.CAPTURE,
    val photoBase64: String? = null,
    val recognized: List<RecognizedFood> = emptyList(),
    val estimated: List<EstimatedFoodItem> = emptyList(),
    val mealType: MealType = MealType.SNACK,
    val error: String? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val mealRepository: MealRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun setMealType(type: MealType) = _uiState.update { it.copy(mealType = type) }

    fun onPhotoTaken(imageBytes: ByteArray) {
        val base64 = compressAndEncode(imageBytes)
        _uiState.update { it.copy(photoBase64 = base64, stage = ScannerStage.ANALYZING_1) }
        runStage1(base64)
    }

    private fun runStage1(base64: String) {
        viewModelScope.launch {
            aiRepository.recognizeFood(base64)
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        _uiState.update { it.copy(stage = ScannerStage.ERROR, error = "Еда не распознана. Попробуйте другое фото.") }
                    } else {
                        _uiState.update { it.copy(stage = ScannerStage.REVIEW_LIST, recognized = list) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(stage = ScannerStage.ERROR, error = e.message ?: "Ошибка распознавания") }
                }
        }
    }

    fun updateRecognized(index: Int, newName: String) {
        _uiState.update { s ->
            s.copy(recognized = s.recognized.toMutableList().also {
                if (index in it.indices) it[index] = it[index].copy(name = newName)
            })
        }
    }

    fun removeRecognized(index: Int) {
        _uiState.update { s ->
            s.copy(recognized = s.recognized.toMutableList().also {
                if (index in it.indices) it.removeAt(index)
            })
        }
    }

    fun addRecognized(name: String) {
        if (name.isBlank()) return
        _uiState.update { s -> s.copy(recognized = s.recognized + RecognizedFood(name.trim())) }
    }

    fun confirmListAndEstimate() {
        val s = _uiState.value
        val photo = s.photoBase64 ?: return
        val items = s.recognized.map { it.name }.filter { it.isNotBlank() }
        if (items.isEmpty()) {
            _uiState.update { it.copy(stage = ScannerStage.ERROR, error = "Список продуктов пуст") }
            return
        }
        _uiState.update { it.copy(stage = ScannerStage.ANALYZING_2) }
        viewModelScope.launch {
            aiRepository.estimateNutrition(photo, items)
                .onSuccess { list ->
                    _uiState.update { it.copy(stage = ScannerStage.REVIEW_NUTRITION, estimated = list) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(stage = ScannerStage.ERROR, error = e.message ?: "Ошибка оценки") }
                }
        }
    }

    fun updateEstimated(index: Int, item: EstimatedFoodItem) {
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

    fun saveMeal(onDone: () -> Unit) {
        val s = _uiState.value
        val items = s.estimated.map {
            FoodItem(
                name = it.name,
                grams = it.grams,
                caloriesPer100g = it.caloriesPer100g,
                proteinPer100g = it.proteinPer100g,
                fatPer100g = it.fatPer100g,
                carbsPer100g = it.carbsPer100g
            )
        }
        if (items.isEmpty()) return
        viewModelScope.launch {
            mealRepository.addMeal(
                Meal(
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    mealType = s.mealType,
                    items = items
                )
            )
            _uiState.update { it.copy(stage = ScannerStage.SAVED) }
            onDone()
        }
    }

    fun retry() {
        val photo = _uiState.value.photoBase64
        _uiState.update { it.copy(stage = if (photo != null) ScannerStage.CAPTURE else ScannerStage.CAPTURE, error = null) }
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
