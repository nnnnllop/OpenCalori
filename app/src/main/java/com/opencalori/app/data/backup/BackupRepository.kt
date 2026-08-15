package com.opencalori.app.data.backup

import android.content.Context
import android.net.Uri
import com.opencalori.app.data.local.AppDatabase
import com.opencalori.app.di.IoDispatcher
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.Product
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
    /** Legacy merge entry point kept while UI callers migrate to the confirmation flow. */
    suspend fun importFrom(uri: Uri): Result<ImportSummary>
    suspend fun previewImport(uri: Uri): Result<ImportPreview> =
        Result.failure(UnsupportedOperationException("Предпросмотр импорта не поддержан"))
    suspend fun importFrom(uri: Uri, mode: ImportMode): Result<ImportSummary> = importFrom(uri)
    suspend fun undoLastImport(): Result<ImportSummary> =
        Result.failure(IllegalStateException("Нет недавнего импорта для отмены"))
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

    override suspend fun importFrom(uri: Uri): Result<ImportSummary> = importFrom(uri, ImportMode.MERGE)

    override suspend fun previewImport(uri: Uri): Result<ImportPreview> = runCatching {
        val imported = readSnapshot(uri)
        val local = snapshot()
        val localItems = local.meals.flatMap { meal -> meal.items.map { it.fingerprint(meal) } }.toSet()
        val importedItems = imported.meals.flatMap { meal -> meal.items.map { it.fingerprint(meal) } }
        val localProducts = local.customProducts.map { it.fingerprint() }.toSet()
        val importedProducts = imported.customProducts.map { it.fingerprint() }
        ImportPreview(
            exportedAt = imported.exportedAt,
            meals = imported.meals.size,
            items = importedItems.size,
            weights = imported.weights.size,
            products = importedProducts.size,
            duplicateItems = importedItems.count { it in localItems },
            duplicateProducts = importedProducts.count { it in localProducts }
        )
    }

    override suspend fun importFrom(uri: Uri, mode: ImportMode): Result<ImportSummary> = runCatching {
        val imported = readSnapshot(uri)
        val before = snapshot()
        val summary = try {
            when (mode) {
                ImportMode.MERGE -> mergeSnapshot(imported, before)
                ImportMode.REPLACE -> replaceSnapshot(imported)
            }
        } catch (error: Throwable) {
            if (mode == ImportMode.REPLACE) restoreSnapshot(before)
            throw error
        }
        if (mode == ImportMode.REPLACE) {
            userPreferences.saveProfile(imported.profile.copy(onboardingCompleted = true))
        }
        rollbackSnapshot = before
        summary
    }

    override suspend fun undoLastImport(): Result<ImportSummary> = runCatching {
        val rollback = rollbackSnapshot ?: error("Нет недавнего импорта для отмены")
        val summary = restoreSnapshot(rollback)
        userPreferences.saveProfile(rollback.profile.copy(onboardingCompleted = true))
        rollbackSnapshot = null
        summary.copy(restored = true)
    }

    private suspend fun readSnapshot(uri: Uri): BackupSnapshot {
        val text = withContext(io) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Не удалось прочитать файл")
        }
        return BackupSerializer.decode(text)
    }

    private suspend fun mergeSnapshot(imported: BackupSnapshot, local: BackupSnapshot): ImportSummary {
        val localItems = local.meals.flatMap { meal -> meal.items.map { it.fingerprint(meal) } }.toMutableSet()
        val localWeights = local.weights.map { it.dateEpochDay }.toMutableSet()
        val localProducts = local.customProducts.map { it.fingerprint() }.toMutableSet()
        var addedMeals = 0
        var addedItems = 0
        var skippedItems = 0
        var addedWeights = 0
        var addedProducts = 0
        var skippedProducts = 0

        imported.meals.forEach { meal ->
            val accepted = meal.items.filter { item ->
                val fingerprint = item.fingerprint(meal)
                if (fingerprint in localItems) {
                    skippedItems += 1
                    false
                } else {
                    localItems += fingerprint
                    true
                }
            }
            if (accepted.isNotEmpty()) {
                mealRepository.addItems(meal.dateEpochDay, meal.mealType, accepted, meal.dishName)
                addedMeals += 1
                addedItems += accepted.size
            }
        }
        imported.weights.forEach { weight ->
            if (localWeights.add(weight.dateEpochDay)) {
                mealRepository.addWeight(weight.dateEpochDay, weight.weightKg)
                addedWeights += 1
            }
        }
        imported.customProducts.forEach { product ->
            if (localProducts.add(product.fingerprint())) {
                productRepository.addCustomProduct(product)
                addedProducts += 1
            } else {
                skippedProducts += 1
            }
        }
        return ImportSummary(
            meals = addedMeals,
            items = addedItems,
            weights = addedWeights,
            products = addedProducts,
            skippedItems = skippedItems,
            skippedProducts = skippedProducts,
            mode = ImportMode.MERGE
        )
    }

    private suspend fun replaceSnapshot(snapshot: BackupSnapshot): ImportSummary {
        withContext(io) { database.clearAllTables() }
        return restoreSnapshot(snapshot).copy(replaced = true)
    }

    private suspend fun restoreSnapshot(snapshot: BackupSnapshot): ImportSummary {
        withContext(io) { database.clearAllTables() }
        snapshot.meals.forEach { meal ->
            if (meal.items.isNotEmpty()) mealRepository.addItems(meal.dateEpochDay, meal.mealType, meal.items, meal.dishName)
        }
        snapshot.weights.forEach { weight -> mealRepository.addWeight(weight.dateEpochDay, weight.weightKg) }
        snapshot.customProducts.forEach { productRepository.addCustomProduct(it) }
        return ImportSummary(
            meals = snapshot.meals.size,
            items = snapshot.meals.sumOf { it.items.size },
            weights = snapshot.weights.size,
            products = snapshot.customProducts.size,
            mode = ImportMode.REPLACE
        )
    }

    private fun FoodItem.fingerprint(meal: Meal): String = listOf(
        meal.dateEpochDay,
        meal.mealType.name,
        name.trim().lowercase(Locale.ROOT),
        grams,
        caloriesPer100g,
        proteinPer100g,
        fatPer100g,
        carbsPer100g
    ).joinToString("|")

    private fun Product.fingerprint(): String = listOf(
        name.trim().lowercase(Locale.ROOT),
        caloriesPer100g,
        proteinPer100g,
        fatPer100g,
        carbsPer100g
    ).joinToString("|")

    private companion object {
        const val MIN_DAY = -100_000L
        const val MAX_DAY = 1_000_000L
    }
}

data class ImportSummary(
    val meals: Int,
    val items: Int,
    val weights: Int,
    val products: Int,
    val skippedItems: Int = 0,
    val skippedProducts: Int = 0,
    val mode: ImportMode = ImportMode.MERGE,
    val replaced: Boolean = false,
    val restored: Boolean = false
)
