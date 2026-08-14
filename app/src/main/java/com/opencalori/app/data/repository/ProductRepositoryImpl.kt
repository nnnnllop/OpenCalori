package com.opencalori.app.data.repository

import com.opencalori.app.data.local.FtsQuery
import com.opencalori.app.data.local.dao.CustomProductDao
import com.opencalori.app.data.local.dao.ProductDao
import com.opencalori.app.data.local.entity.CustomProductEntity
import com.opencalori.app.data.local.entity.ProductEntity
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.ProductSource
import com.opencalori.app.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val customProductDao: CustomProductDao
) : ProductRepository {

    override fun search(query: String): Flow<List<Product>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return flowOf(emptyList())

        val builtIn = when (val fts = FtsQuery.buildExpanded(trimmed)) {
            null -> productDao.searchLike(trimmed)
            // A malformed MATCH throws at collection time; degrade to LIKE instead of crashing.
            else -> productDao.searchFts(fts).catch { emitAll(productDao.searchLike(trimmed)) }
        }

        return combine(
            customProductDao.search(trimmed),
            builtIn
        ) { custom, stock ->
            // The user's own products always win over the bundled table.
            custom.map { it.toDomain() } + stock.map { it.toDomain() }
        }
    }

    override suspend fun addCustomProduct(product: Product): Long =
        customProductDao.insert(
            CustomProductEntity(
                name = product.name.trim(),
                caloriesPer100g = product.caloriesPer100g,
                proteinPer100g = product.proteinPer100g,
                fatPer100g = product.fatPer100g,
                carbsPer100g = product.carbsPer100g
            )
        )

    override suspend fun deleteCustomProduct(id: Long) = customProductDao.delete(id)

    override fun customProducts(): Flow<List<Product>> =
        customProductDao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun builtInCount(): Int = productDao.count()

    private fun ProductEntity.toDomain() = Product(
        id = id, name = name,
        caloriesPer100g = caloriesPer100g, proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g, carbsPer100g = carbsPer100g,
        source = ProductSource.BUILT_IN
    )

    private fun CustomProductEntity.toDomain() = Product(
        id = id, name = name,
        caloriesPer100g = caloriesPer100g, proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g, carbsPer100g = carbsPer100g,
        source = ProductSource.CUSTOM
    )
}
