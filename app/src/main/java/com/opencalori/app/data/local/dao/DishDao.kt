package com.opencalori.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification
import com.opencalori.app.data.local.entity.DishEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DishDao {
    @SkipQueryVerification
    @Query(
        """
        SELECT d.* FROM dishes d
        JOIN dishes_fts fts ON d.id = fts.docid
        WHERE dishes_fts MATCH :ftsQuery
        ORDER BY LENGTH(d.name), d.name
        LIMIT :limit
        """
    )
    fun searchFts(ftsQuery: String, limit: Int = 50): Flow<List<DishEntity>>

    @Query(
        """
        SELECT * FROM dishes
        WHERE name LIKE :pattern
           OR aliases LIKE :pattern
        ORDER BY LENGTH(name), name
        LIMIT :limit
        """
    )
    fun searchLike(pattern: String, limit: Int = 50): Flow<List<DishEntity>>

    @Query("SELECT COUNT(*) FROM dishes")
    suspend fun count(): Int
}
