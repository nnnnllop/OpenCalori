package com.opencalori.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.opencalori.app.data.local.dao.ProductDao
import com.opencalori.app.data.local.entity.ProductEntity
import com.opencalori.app.data.local.entity.ProductFtsEntity

/**
 * Pre-populated offline product database shipped in assets.
 * Opened read-only via createFromAsset().
 */
@Database(
    entities = [ProductEntity::class, ProductFtsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ProductDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    companion object {
        const val ASSET_PATH = "databases/products.db"
    }
}
