package com.opencalori.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.opencalori.app.data.local.dao.DishDao
import com.opencalori.app.data.local.entity.DishEntity
import com.opencalori.app.data.local.entity.DishFtsEntity

@Database(
    entities = [DishEntity::class, DishFtsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DishDatabase : RoomDatabase() {
    abstract fun dishDao(): DishDao

    companion object {
        const val NAME = "dishes_catalog.db"
        const val ASSET_PATH = "databases/dishes_catalog.db"
    }
}
