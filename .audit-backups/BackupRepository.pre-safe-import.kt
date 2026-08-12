package com.opencalori.app.data.backup

import android.content.Context
import android.net.Uri
import com.opencalori.app.data.local.AppDatabase
import com.opencalori.app.di.IoDispatcher
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.repository.MealRepository
import com.opencalori.app.domain.repository.ProductRepository
import com.opencalori.app.domain.repository.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Full-data export/import. Behind an interface so the settings screen can be tested. */
interface BackupManager {
    suspend fun snapshot(): BackupSnapshot
    suspend fun exportTo(uri: Uri): Result<Int>
    suspend fun previewImport(uri: Uri): Result<ImportPreview>
    suspend fun importFrom(uri: Uri, mode: ImportMode): Result<ImportSummary>
    suspend fun undoLastImport(): Result<ImportSummary>
}

enum class ImportMode { MERGE, REPLACE }

/** A non-destructive summary shown before the user confirms an import. */
data class ImportPreview(
    val exportedAt: Long,
    val meals: Int,
    val items: Int,
    val weights: Int,
    val products: Int,
    val duplicateItems: Int,
    val duplicateProducts: Int
)

/** Reads and writes the full-data JSON snapshot through the Storage Access Framework. */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val mealRepository: MealRepository,
    private val productRepository: ProductRepository,
    private val userPreferences: UserPreferences,
    @IoDispatcher private val io: CoroutineDispatcher
) : BackupManager {
    /** Exact state before the last import; enables an immediate Undo action. */
    private var rollbackSnapshot: BackupSnapshot? = null

    override suspend fun snapshot(): BackupSnapshot = BackupSnapshot(
        profile = userPreferences.profile.first(),
        meals = mealRepository.getMealsBetween(MIN_DAY, MAX_DAY),
        weights = mealRepository.getWeightHistory().first(),
        customProducts = productRepository.customProducts().first()
    )

    override suspend fun exportTo(uri: Uri): Result<Int> = runCatching {
        val snapshot = snapshot()
        val text = BackupSerializer.encode(snapshot)
        withContext(io) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("Не удалось открыть файл для записи")
        }
        snapshot.meals.sumOf { it.items.size }
    }

    /**
     * Merges a backup into the current database. Import is additive on purpose: nobody
     * expects "restore" to silently delete the meals they logged this morning.
     */
    override suspend fun importFrom(uri: Uri): Result<ImportSummary> = runCatching {
        val text = withContext(io) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Не удалось прочитать файл")
        }
        val snapshot = BackupSerializer.decode(text)

        snapshot.meals.forEach { meal: Meal ->
            mealRepository.addItems(meal.dateEpochDay, meal.mealType, meal.items)
        }
        snapshot.weights.forEach { mealRepository.addWeight(it.dateEpochDay, it.weightKg) }
        snapshot.customProducts.forEach { productRepository.addCustomProduct(it) }
        userPreferences.saveProfile(snapshot.profile.copy(onboardingCompleted = true))

        ImportSummary(
            meals = snapshot.meals.size,
            items = snapshot.meals.sumOf { it.items.size },
            weights = snapshot.weights.size,
            products = snapshot.customProducts.size
        )
    }

    private companion object {
        const val MIN_DAY = -100_000L
        const val MAX_DAY = 1_000_000L
    }
}

data class ImportSummary(
    val meals: Int,
    val items: Int,
    val weights: Int,
    val products: Int
)
