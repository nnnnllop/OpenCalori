package com.opencalori.app.data.repository

import com.opencalori.app.data.local.dao.ProductDao
import com.opencalori.app.data.local.entity.ProductEntity
import com.opencalori.app.domain.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao
) {
    fun search(query: String): Flow<List<Product>> {
        if (query.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        // FTS4 query: append * for prefix matching, escape quotes
        val ftsQuery = query.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" AND ") { "\"${it.replace("\"", "")}\"*" }
        return productDao.searchFts(ftsQuery).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getById(id: Long): Product? = productDao.getById(id)?.toDomain()

    suspend fun count(): Int = productDao.count()

    private fun ProductEntity.toDomain() = Product(
        id = id, name = name,
        caloriesPer100g = caloriesPer100g, proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g, carbsPer100g = carbsPer100g
    )
}
