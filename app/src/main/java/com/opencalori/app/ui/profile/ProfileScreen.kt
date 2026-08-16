package com.opencalori.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Параметры тела") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Норма калорий пересчитается сразу после сохранения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Пол", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Gender.entries.forEach { gender ->
                    FilterChip(
                        selected = state.gender == gender,
                        onClick = { viewModel.setGender(gender) },
                        label = { Text(gender.label) }
                    )
                }
            }

            OutlinedTextField(
                value = state.age,
                onValueChange = viewModel::setAge,
                label = { Text("Возраст, лет") },
                isError = state.age.isNotEmpty() && state.ageValue == null,
                supportingText = {
                    Text(if (state.age.isNotEmpty() && state.ageValue == null) "Введите возраст от 10 до 120 лет" else "От 10 до 120 лет")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.height,
                onValueChange = viewModel::setHeight,
                label = { Text("Рост, см") },
                isError = state.height.isNotEmpty() && state.heightValue == null,
                supportingText = {
                    Text(if (state.height.isNotEmpty() && state.heightValue == null) "Введите рост от 100 до 250 см" else "От 100 до 250 см")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.weight,
                onValueChange = viewModel::setWeight,
                label = { Text("Вес, кг") },
                supportingText = {
                    Text(
                        if (state.weight.isNotEmpty() && state.weightValue == null) "Введите вес от 30 до 300 кг"
                        else "От 30 до 300 кг · сохранится и в дневнике веса"
                    )
                },
                isError = state.weight.isNotEmpty() && state.weightValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Активность", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityLevel.entries.forEach { level ->
                    FilterChip(
                        selected = state.activityLevel == level,
                        onClick = { viewModel.setActivity(level) },
                        label = { Text(level.label + " • " + level.hint) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text("Цель", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Goal.entries.forEach { goal ->
                    FilterChip(
                        selected = state.goal == goal,
                        onClick = { viewModel.setGoal(goal) },
                        label = { Text(goal.label + " • " + goal.hint) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            viewModel.preview()?.let { preview ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            preview.targetCalories.toString() + " ккал / день",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Б " + preview.proteinGrams + " г • Ж " + preview.fatGrams +
                                " г • У " + preview.carbsGrams + " г",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = viewModel::save,
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
