package com.opencalori.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("OpenCalori 🥑", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Настроим вашу норму калорий",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LinearProgressIndicator(
            progress = { (state.step + 1) / 4f },
            modifier = Modifier.fillMaxWidth()
        )

        when {
            state.result != null -> ResultStep(state, onSave = { viewModel.saveAndFinish(onFinished) })
            else -> when (state.step) {
                0 -> GenderStep(state.gender, viewModel::setGender)
                1 -> MetricsStep(state, viewModel)
                2 -> ActivityStep(state.activityLevel, viewModel::setActivity)
                3 -> GoalStep(state.goal, viewModel::setGoal)
            }
        }

        Spacer(Modifier.weight(1f))

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
                    Text(if (state.step == 3) "Рассчитать" else "Далее")
                }
            }
        }
    }
}

@Composable
private fun GenderStep(selected: Gender, onSelect: (Gender) -> Unit) {
    Text("Ваш пол", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(
            selected = selected == Gender.MALE,
            onClick = { onSelect(Gender.MALE) },
            label = { Text("Мужской") }
        )
        FilterChip(
            selected = selected == Gender.FEMALE,
            onClick = { onSelect(Gender.FEMALE) },
            label = { Text("Женский") }
        )
    }
}

@Composable
private fun MetricsStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    Text("Параметры тела", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = state.age,
        onValueChange = vm::setAge,
        label = { Text("Возраст (лет)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.height,
        onValueChange = vm::setHeight,
        label = { Text("Рост (см)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.weight,
        onValueChange = vm::setWeight,
        label = { Text("Вес (кг)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                label = { Text(level.labelRes) },
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
                label = { Text(goal.labelRes) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ResultStep(state: OnboardingUiState, onSave: () -> Unit) {
    val r = state.result ?: return
    Text("Ваша норма", style = MaterialTheme.typography.titleLarge)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${r.targetCalories}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("ккал / день", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("BMR: ${r.bmr.toInt()} ккал • TDEE: ${r.tdee.toInt()} ккал",
                style = MaterialTheme.typography.bodySmall)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LegendDot(ProteinColor, "Белки: ${r.proteinGrams} г")
            LegendDot(FatColor, "Жиры: ${r.fatGrams} г")
            LegendDot(CarbsColor, "Углеводы: ${r.carbsGrams} г")
        }
    }

    Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.saving) "Сохранение…" else "Начать")
    }
}
