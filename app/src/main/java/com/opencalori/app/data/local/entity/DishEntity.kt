package com.opencalori.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

@Entity(tableName = "dishes")
data class DishEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val aliases: String,
    val ingredients: String,
    val portionGrams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
)

@Fts4(
    contentEntity = DishEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61
)
@Entity(tableName = "dishes_fts")
data class DishFtsEntity(
    val name: String,
    val aliases: String
)
