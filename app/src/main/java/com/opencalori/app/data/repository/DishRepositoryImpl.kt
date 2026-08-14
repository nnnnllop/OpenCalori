package com.opencalori.app.data.repository

import com.opencalori.app.data.local.FtsQuery
import com.opencalori.app.data.local.dao.DishDao
import com.opencalori.app.data.local.entity.DishEntity
import com.opencalori.app.domain.model.Dish
import com.opencalori.app.domain.repository.DishRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DishRepositoryImpl @Inject constructor(
    private val dishDao: DishDao
) : DishRepository {
    override fun search(query: String): Flow<List<Dish>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return flowOf(emptyList())
        val rows = when (val fts = FtsQuery.buildExpanded(trimmed)) {
            null -> dishDao.searchLike("%$trimmed%")
            else -> dishDao.searchFts(fts).catch { emitAll(dishDao.searchLike("%$trimmed%")) }
        }
        return rows.map { entities -> entities.map { it.toDish() } }
    }

    override suspend fun count(): Int = dishDao.count()

    private fun DishEntity.toDish() = Dish(
        id = id,
        name = name,
        aliases = aliases.split(DELIMITER).filter(String::isNotBlank),
        ingredients = ingredients.split(DELIMITER).filter(String::isNotBlank),
        portionGrams = portionGrams,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g
    )

    private companion object {
        const val DELIMITER = "|"
    }
}
