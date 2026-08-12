package com.opencalori.app.data.backup

import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.ProductSource
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.model.WeightEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Human-readable JSON snapshot of everything the app stores.
 *
 * The whole pitch of OpenCalori is "your data never leaves the device", which only works
 * if the user can actually take it with them when they change phones.
 */
object BackupSerializer {

    const val FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun encode(snapshot: BackupSnapshot): String =
        json.encodeToString(BackupDto.serializer(), BackupDto.from(snapshot))

    fun decode(text: String): BackupSnapshot {
        val dto = json.decodeFromString(BackupDto.serializer(), text)
        require(dto.formatVersion <= FORMAT_VERSION) {
            "Файл создан более новой версией приложения (формат " + dto.formatVersion + ")"
        }
        return dto.toSnapshot()
    }
}

data class BackupSnapshot(
    val profile: UserProfile,
    val meals: List<Meal>,
    val weights: List<WeightEntry>,
    val customProducts: List<Product>,
    val exportedAt: Long = System.currentTimeMillis()
)

@Serializable
data class BackupDto(
    val formatVersion: Int = BackupSerializer.FORMAT_VERSION,
    val exportedAt: Long = 0L,
    val profile: ProfileDto = ProfileDto(),
    val meals: List<MealDto> = emptyList(),
    val weights: List<WeightDto> = emptyList(),
    val customProducts: List<ProductDto> = emptyList()
) {
    fun toSnapshot() = BackupSnapshot(
        profile = profile.toDomain(),
        meals = meals.map { it.toDomain() },
        weights = weights.map { WeightEntry(it.dateEpochDay, it.weightKg) },
        customProducts = customProducts.map { it.toDomain() },
        exportedAt = exportedAt
    )

    companion object {
        fun from(snapshot: BackupSnapshot) = BackupDto(
            exportedAt = snapshot.exportedAt,
            profile = ProfileDto.from(snapshot.profile),
            meals = snapshot.meals.map { MealDto.from(it) },
            weights = snapshot.weights.map { WeightDto(it.dateEpochDay, it.weightKg) },
            customProducts = snapshot.customProducts.map { ProductDto.from(it) }
        )
    }
}

@Serializable
data class ProfileDto(
    val gender: String = Gender.MALE.name,
    val age: Int = 25,
    val heightCm: Float = 175f,
    val weightKg: Float = 70f,
    val activityLevel: String = ActivityLevel.SEDENTARY.name,
    val goal: String = Goal.MAINTAIN.name,
    val onboardingCompleted: Boolean = true,
    val aiEnabled: Boolean = true,
    val aiSkipListReview: Boolean = false,
    val aiSkipGramsReview: Boolean = false,
    val aiSkipFinalReview: Boolean = false
) {
    fun toDomain() = UserProfile(
        gender = enumOrDefault(gender, Gender.MALE),
        age = age,
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = enumOrDefault(activityLevel, ActivityLevel.SEDENTARY),
        goal = enumOrDefault(goal, Goal.MAINTAIN),
        onboardingCompleted = onboardingCompleted,
        aiEnabled = aiEnabled,
        aiSkipListReview = aiSkipListReview,
        aiSkipGramsReview = aiSkipGramsReview,
        aiSkipFinalReview = aiSkipFinalReview
    )

    companion object {
        fun from(profile: UserProfile) = ProfileDto(
            gender = profile.gender.name,
            age = profile.age,
            heightCm = profile.heightCm,
            weightKg = profile.weightKg,
            activityLevel = profile.activityLevel.name,
            goal = profile.goal.name,
            onboardingCompleted = profile.onboardingCompleted,
            aiEnabled = profile.aiEnabled,
            aiSkipListReview = profile.aiSkipListReview,
            aiSkipGramsReview = profile.aiSkipGramsReview,
            aiSkipFinalReview = profile.aiSkipFinalReview
        )
    }
}

@Serializable
data class MealDto(
    val dateEpochDay: Long,
    val mealType: String,
    val createdAt: Long = 0L,
    val items: List<FoodItemDto> = emptyList()
) {
    fun toDomain() = Meal(
        dateEpochDay = dateEpochDay,
        mealType = enumOrDefault(mealType, MealType.SNACK),
        createdAt = createdAt,
        items = items.map { it.toDomain() }
    )

    companion object {
        fun from(meal: Meal) = MealDto(
            dateEpochDay = meal.dateEpochDay,
            mealType = meal.mealType.name,
            createdAt = meal.createdAt,
            items = meal.items.map { FoodItemDto.from(it) }
        )
    }
}

@Serializable
data class FoodItemDto(
    val name: String,
    val grams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
) {
    fun toDomain() = FoodItem(
        name = name, grams = grams,
        caloriesPer100g = caloriesPer100g, proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g, carbsPer100g = carbsPer100g
    )

    companion object {
        fun from(item: FoodItem) = FoodItemDto(
            name = item.name, grams = item.grams,
            caloriesPer100g = item.caloriesPer100g, proteinPer100g = item.proteinPer100g,
            fatPer100g = item.fatPer100g, carbsPer100g = item.carbsPer100g
        )
    }
}

@Serializable
data class WeightDto(val dateEpochDay: Long, val weightKg: Float)

@Serializable
data class ProductDto(
    val name: String,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
) {
    fun toDomain() = Product(
        id = 0,
        name = name,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g,
        source = ProductSource.CUSTOM
    )

    companion object {
        fun from(product: Product) = ProductDto(
            name = product.name,
            caloriesPer100g = product.caloriesPer100g,
            proteinPer100g = product.proteinPer100g,
            fatPer100g = product.fatPer100g,
            carbsPer100g = product.carbsPer100g
        )
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
