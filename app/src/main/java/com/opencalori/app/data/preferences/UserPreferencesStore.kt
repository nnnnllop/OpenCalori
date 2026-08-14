package com.opencalori.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.NutritionSourceMode
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.repository.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferences {

    private object Keys {
        val GENDER = stringPreferencesKey("gender")
        val AGE = intPreferencesKey("age")
        val HEIGHT = floatPreferencesKey("height_cm")
        val WEIGHT = floatPreferencesKey("weight_kg")
        val ACTIVITY = stringPreferencesKey("activity_level")
        val GOAL = stringPreferencesKey("goal")
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")

        // AI settings
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_SKIP_LIST = booleanPreferencesKey("ai_skip_list_review")
        val AI_SKIP_GRAMS = booleanPreferencesKey("ai_skip_grams_review")
        val AI_SKIP_FINAL = booleanPreferencesKey("ai_skip_final_review")
        val NUTRITION_SOURCE_MODE = stringPreferencesKey("nutrition_source_mode")
    }

    override val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            gender = prefs[Keys.GENDER]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: Gender.MALE,
            age = prefs[Keys.AGE] ?: 25,
            heightCm = prefs[Keys.HEIGHT] ?: 175f,
            weightKg = prefs[Keys.WEIGHT] ?: 70f,
            activityLevel = prefs[Keys.ACTIVITY]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() }
                ?: ActivityLevel.SEDENTARY,
            goal = prefs[Keys.GOAL]?.let { runCatching { Goal.valueOf(it) }.getOrNull() } ?: Goal.MAINTAIN,
            onboardingCompleted = prefs[Keys.ONBOARDED] ?: false,
            aiEnabled = prefs[Keys.AI_ENABLED] ?: true,
            aiSkipListReview = prefs[Keys.AI_SKIP_LIST] ?: false,
            aiSkipGramsReview = prefs[Keys.AI_SKIP_GRAMS] ?: false,
            aiSkipFinalReview = prefs[Keys.AI_SKIP_FINAL] ?: false,
            nutritionSourceMode = NutritionSourceMode.fromStorage(prefs[Keys.NUTRITION_SOURCE_MODE])
        )
    }

    override suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GENDER] = profile.gender.name
            prefs[Keys.AGE] = profile.age
            prefs[Keys.HEIGHT] = profile.heightCm
            prefs[Keys.WEIGHT] = profile.weightKg
            prefs[Keys.ACTIVITY] = profile.activityLevel.name
            prefs[Keys.GOAL] = profile.goal.name
            prefs[Keys.ONBOARDED] = profile.onboardingCompleted
            prefs[Keys.AI_ENABLED] = profile.aiEnabled
            prefs[Keys.AI_SKIP_LIST] = profile.aiSkipListReview
            prefs[Keys.AI_SKIP_GRAMS] = profile.aiSkipGramsReview
            prefs[Keys.AI_SKIP_FINAL] = profile.aiSkipFinalReview
            prefs[Keys.NUTRITION_SOURCE_MODE] = profile.nutritionSourceMode.name
        }
    }

    /** Keeps the calorie target in sync with the weight logged in the diary. */
    override suspend fun setWeight(weightKg: Float) {
        context.dataStore.edit { it[Keys.WEIGHT] = weightKg }
    }

    override suspend fun setAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_ENABLED] = enabled }
    }

    override suspend fun setAiSkipListReview(skip: Boolean) {
        context.dataStore.edit { it[Keys.AI_SKIP_LIST] = skip }
    }

    override suspend fun setAiSkipGramsReview(skip: Boolean) {
        context.dataStore.edit { it[Keys.AI_SKIP_GRAMS] = skip }
    }

    override suspend fun setAiSkipFinalReview(skip: Boolean) {
        context.dataStore.edit { it[Keys.AI_SKIP_FINAL] = skip }
    }
    override suspend fun setNutritionSourceMode(mode: NutritionSourceMode) {
        context.dataStore.edit { it[Keys.NUTRITION_SOURCE_MODE] = mode.name }
    }
}
