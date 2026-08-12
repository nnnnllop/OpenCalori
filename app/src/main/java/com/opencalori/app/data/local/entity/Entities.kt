package com.opencalori.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meals",
    indices = [Index(value = ["dateEpochDay", "mealType"])]
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val mealType: String,        // MealType.name
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "food_items",
    indices = [Index(value = ["mealId"])],
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val name: String,
    val grams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
)

@Entity(tableName = "weight_history")
data class WeightEntryEntity(
    @PrimaryKey val dateEpochDay: Long,
    val weightKg: Float
)

/** A product the user typed in themselves. Lives in the writable app database. */
@Entity(tableName = "custom_products")
data class CustomProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float,
    val createdAt: Long = System.currentTimeMillis()
)
