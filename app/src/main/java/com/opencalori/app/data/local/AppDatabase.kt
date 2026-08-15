package com.opencalori.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.opencalori.app.data.local.dao.CustomProductDao
import com.opencalori.app.data.local.dao.MealDao
import com.opencalori.app.data.local.dao.WeightDao
import com.opencalori.app.data.local.entity.CustomProductEntity
import com.opencalori.app.data.local.entity.FoodItemEntity
import com.opencalori.app.data.local.entity.MealEntity
import com.opencalori.app.data.local.entity.WeightEntryEntity

@Database(
    entities = [
        MealEntity::class,
        FoodItemEntity::class,
        WeightEntryEntity::class,
        CustomProductEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun weightDao(): WeightDao
    abstract fun customProductDao(): CustomProductDao

    companion object {
        const val NAME = "opencalori.db"

        /**
         * v1 -> v2: user-defined products, lookup indices and a real FK between meals and
         * their items. Written by hand on purpose - this database holds the user's entire
         * food history and must never be dropped on a schema change.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_products` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `caloriesPer100g` REAL NOT NULL,
                        `proteinPer100g` REAL NOT NULL,
                        `fatPer100g` REAL NOT NULL,
                        `carbsPer100g` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_meals_dateEpochDay_mealType` " +
                        "ON `meals` (`dateEpochDay`, `mealType`)"
                )

                // Drop rows that would violate the new foreign key before enforcing it.
                db.execSQL("DELETE FROM `food_items` WHERE `mealId` NOT IN (SELECT `id` FROM `meals`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mealId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `grams` REAL NOT NULL,
                        `caloriesPer100g` REAL NOT NULL,
                        `proteinPer100g` REAL NOT NULL,
                        `fatPer100g` REAL NOT NULL,
                        `carbsPer100g` REAL NOT NULL,
                        FOREIGN KEY(`mealId`) REFERENCES `meals`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `food_items_new` " +
                        "(`id`, `mealId`, `name`, `grams`, `caloriesPer100g`, `proteinPer100g`, `fatPer100g`, `carbsPer100g`) " +
                        "SELECT `id`, `mealId`, `name`, `grams`, `caloriesPer100g`, `proteinPer100g`, `fatPer100g`, `carbsPer100g` " +
                        "FROM `food_items`"
                )
                db.execSQL("DROP TABLE `food_items`")
                db.execSQL("ALTER TABLE `food_items_new` RENAME TO `food_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_mealId` ON `food_items` (`mealId`)")
            }
        }

        /** v2 -> v3: preserves existing entries while allowing scan results to retain a dish title. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `dishName` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_meals_dateEpochDay_mealType_dishName` " +
                        "ON `meals` (`dateEpochDay`, `mealType`, `dishName`)"
                )
            }
        }
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
