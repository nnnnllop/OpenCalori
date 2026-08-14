from pathlib import Path

path = Path(r"C:\Users\zomni\Projects\OpenCalori\app\src\main\java\com\opencalori\app\ui\scanner\ScannerViewModel.kt")
text = path.read_text(encoding="utf-8")
start = "    private suspend fun runStage2(photo: String, dishName: String, items: List<String>) {"
end = "    // ---- Grams editing ----"
if text.count(start) != 1 or text.count(end) != 1:
    raise SystemExit("Expected unique scanner stage-2 boundaries")
start_index = text.index(start)
end_index = text.index(end, start_index)
replacement = r'''    private suspend fun runStage2(photo: String, dishName: String, items: List<String>) {
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
'''
path.write_text(text[:start_index] + replacement + text[end_index:], encoding="utf-8")
print("Updated scanner stage 2")
