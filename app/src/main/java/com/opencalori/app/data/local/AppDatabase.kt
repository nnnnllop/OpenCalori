package com.opencalori.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.opencalori.app.data.local.dao.MealDao
import com.opencalori.app.data.local.dao.WeightDao
import com.opencalori.app.data.local.entity.FoodItemEntity
import com.opencalori.app.data.local.entity.MealEntity
import com.opencalori.app.data.local.entity.WeightEntryEntity

@Database(
    entities = [MealEntity::class, FoodItemEntity::class, WeightEntryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun weightDao(): WeightDao

    companion object {
        const val NAME = "opencalori.db"
    }
}
