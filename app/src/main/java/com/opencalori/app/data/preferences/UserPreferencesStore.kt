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
import com.opencalori.app.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GENDER = stringPreferencesKey("gender")
        val AGE = intPreferencesKey("age")
        val HEIGHT = floatPreferencesKey("height_cm")
        val WEIGHT = floatPreferencesKey("weight_kg")
        val ACTIVITY = stringPreferencesKey("activity_level")
        val GOAL = stringPreferencesKey("goal")
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")
    }

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            gender = prefs[Keys.GENDER]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: Gender.MALE,
            age = prefs[Keys.AGE] ?: 25,
            heightCm = prefs[Keys.HEIGHT] ?: 175f,
            weightKg = prefs[Keys.WEIGHT] ?: 70f,
            activityLevel = prefs[Keys.ACTIVITY]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() }
                ?: ActivityLevel.SEDENTARY,
            goal = prefs[Keys.GOAL]?.let { runCatching { Goal.valueOf(it) }.getOrNull() } ?: Goal.MAINTAIN,
            onboardingCompleted = prefs[Keys.ONBOARDED] ?: false
        )
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GENDER] = profile.gender.name
            prefs[Keys.AGE] = profile.age
            prefs[Keys.HEIGHT] = profile.heightCm
            prefs[Keys.WEIGHT] = profile.weightKg
            prefs[Keys.ACTIVITY] = profile.activityLevel.name
            prefs[Keys.GOAL] = profile.goal.name
            prefs[Keys.ONBOARDED] = profile.onboardingCompleted
        }
    }
}
