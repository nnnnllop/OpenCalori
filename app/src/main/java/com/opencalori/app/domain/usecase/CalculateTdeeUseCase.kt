package com.opencalori.app.domain.usecase

import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.CalorieGoal
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.UserProfile
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * BMR via Mifflin-St Jeor, TDEE = BMR * activity factor,
 * target = TDEE + goal delta. Macros: protein 2g/kg (gain) or 1.6g/kg,
 * fat 25% of calories, carbs = remainder.
 */
class CalculateTdeeUseCase @Inject constructor() {

    operator fun invoke(profile: UserProfile): CalorieGoal {
        val s = if (profile.gender == Gender.MALE) 5.0 else -161.0
        val bmr = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.age + s
        val tdee = bmr * profile.activityLevel.factor
        val target = (tdee + profile.goal.calorieDelta).coerceAtLeast(1200.0)

        val proteinPerKg = if (profile.goal == Goal.GAIN) 2.0 else 1.6
        val proteinGrams = (proteinPerKg * profile.weightKg).roundToInt()
        val proteinCal = proteinGrams * 4.0

        val fatCal = target * 0.25
        val fatGrams = (fatCal / 9.0).roundToInt()

        val carbsCal = (target - proteinCal - fatCal).coerceAtLeast(0.0)
        val carbsGrams = (carbsCal / 4.0).roundToInt()

        return CalorieGoal(
            bmr = bmr,
            tdee = tdee,
            targetCalories = target.roundToInt(),
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            carbsGrams = carbsGrams
        )
    }
}
