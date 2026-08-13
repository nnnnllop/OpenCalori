package com.opencalori.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.ui.components.LegendDot
import com.opencalori.app.ui.theme.CarbsColor
import com.opencalori.app.ui.theme.FatColor
import com.opencalori.app.ui.theme.ProteinColor

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("OC", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column {
                Text(
                    if (state.result == null) "\u0412\u0430\u0448 \u043f\u043b\u0430\u043d \u043f\u0438\u0442\u0430\u043d\u0438\u044f" else "\u041f\u043b\u0430\u043d \u0433\u043e\u0442\u043e\u0432",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (state.result == null) "OpenCalori" else "\u041b\u0438\u0447\u043d\u044b\u0435 \u043e\u0440\u0438\u0435\u043d\u0442\u0438\u0440\u044b \u043d\u0430 \u043a\u0430\u0436\u0434\u044b\u0439 \u0434\u0435\u043d\u044c",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        if (state.result == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043f\u043b\u0430\u043d\u0430", style = MaterialTheme.typography.labelLarge)
                Text("\u0428\u0430\u0433 " + (state.step + 1) + " \u0438\u0437 " + TOTAL_STEPS, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (state.step + 1) / TOTAL_STEPS.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
        }        // The scrollable part only takes the space that is left, so the buttons below stay
        // pinned to the bottom instead of drifting off-screen on small displays.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                state.result != null -> ResultStep(state)
                state.step == 0 -> GenderStep(state.gender, viewModel::setGender)
                state.step == 1 -> MetricsStep(state, viewModel)
                state.step == 2 -> ActivityStep(state.activityLevel, viewModel::setActivity)
                else -> GoalStep(state.goal, viewModel::setGoal)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (state.result == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.step > 0) {
                    OutlinedButton(onClick = viewModel::back, modifier = Modifier.weight(1f)) {
                        Text("Назад")
                    }
                }
                Button(
                    onClick = viewModel::next,
                    enabled = viewModel.canProceed(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.step == TOTAL_STEPS - 1) "Рассчитать" else "Далее")
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::editAgain, modifier = Modifier.weight(1f)) {
                    Text("Изменить")
                }
                Button(
                    onClick = { viewModel.saveAndFinish(onFinished) },
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.saving) "Сохранение…" else "Начать")
                }
            }
        }
    }
}

private const val TOTAL_STEPS = 4

@Composable
private fun GenderStep(selected: Gender, onSelect: (Gender) -> Unit) {
    Text("Ваш пол", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Gender.entries.forEach { gender ->
            FilterChip(
                selected = selected == gender,
                onClick = { onSelect(gender) },
                label = { Text(gender.label) }
            )
        }
    }
}

@Composable
private fun MetricsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val ageError = state.age.isNotEmpty() && state.ageValue == null
    val heightError = state.height.isNotEmpty() && state.heightValue == null
    val weightError = state.weight.isNotEmpty() && state.weightValue == null
    Text("Параметры тела", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = state.age,
        onValueChange = viewModel::setAge,
        label = { Text("Возраст, лет") },
        isError = ageError,
        supportingText = {
            Text(if (ageError) "Введите возраст от 10 до 120 лет" else "От 10 до 120 лет")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.height,
        onValueChange = viewModel::setHeight,
        label = { Text("Рост, см") },
        isError = heightError,
        supportingText = {
            Text(if (heightError) "Введите рост от 100 до 250 см" else "От 100 до 250 см")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.weight,
        onValueChange = viewModel::setWeight,
        label = { Text("Вес, кг") },
        isError = weightError,
        supportingText = {
            Text(if (weightError) "Введите вес от 30 до 300 кг" else "От 30 до 300 кг")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ActivityStep(selected: ActivityLevel, onSelect: (ActivityLevel) -> Unit) {
    Text("Уровень активности", style = MaterialTheme.typography.titleLarge)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityLevel.entries.forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(level) },
                label = { Text(level.label + " • " + level.hint) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GoalStep(selected: Goal, onSelect: (Goal) -> Unit) {
    Text("Ваша цель", style = MaterialTheme.typography.titleLarge)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Goal.entries.forEach { goal ->
            FilterChip(
                selected = selected == goal,
                onClick = { onSelect(goal) },
                label = { Text(goal.label + " • " + goal.hint) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ResultStep(state: OnboardingUiState) {
    val result = state.result ?: return
    Text("Ваша норма", style = MaterialTheme.typography.titleLarge)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                result.targetCalories.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Text("ккал / день", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "BMR " + result.bmr.toInt() + " ккал • TDEE " + result.tdee.toInt() + " ккал",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LegendDot(ProteinColor, "Белки: " + result.proteinGrams + " г")
            LegendDot(FatColor, "Жиры: " + result.fatGrams + " г")
            LegendDot(CarbsColor, "Углеводы: " + result.carbsGrams + " г")
        }
    }

    Text(
        "Норму всегда можно пересчитать в настройках.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
