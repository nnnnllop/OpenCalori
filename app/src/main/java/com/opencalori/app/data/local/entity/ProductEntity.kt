package com.opencalori.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Pre-populated offline product database, shipped as an asset.
 * Created from assets/databases/products.db via Room's createFromAsset().
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
)

@Fts4(contentEntity = ProductEntity::class)
@Entity(tableName = "products_fts")
data class ProductFtsEntity(
    val name: String
)
