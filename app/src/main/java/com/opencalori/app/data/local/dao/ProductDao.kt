package com.opencalori.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification
import com.opencalori.app.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @SkipQueryVerification
    @Query(
        """
        SELECT p.* FROM products p
        JOIN products_fts fts ON p.id = fts.docid
        WHERE products_fts MATCH :ftsQuery
        ORDER BY p.name
        LIMIT :limit
        """
    )
    fun searchFts(ftsQuery: String, limit: Int = 50): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    fun searchLike(query: String, limit: Int = 50): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int
}
