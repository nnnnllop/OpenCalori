package com.opencalori.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
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

/**
 * Full-text index over [ProductEntity.name].
 *
 * The tokenizer MUST be unicode61: the default `simple` tokenizer only case-folds ASCII,
 * so a Cyrillic query typed in lowercase ("гречка") would never match a stored name that
 * starts with a capital letter ("Гречка варёная").
 */
@Fts4(
    contentEntity = ProductEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61
)
@Entity(tableName = "products_fts")
data class ProductFtsEntity(
    val name: String
)
