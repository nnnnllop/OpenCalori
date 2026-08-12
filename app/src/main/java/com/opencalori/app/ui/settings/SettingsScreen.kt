package com.opencalori.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencalori.app.domain.model.ValidationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- AI toggle ----
            Text("Искусственный интеллект", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Использовать ИИ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Сканер еды, распознавание по фото, автоматический расчёт КБЖУ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = profile?.aiEnabled ?: true,
                        onCheckedChange = viewModel::setAiEnabled
                    )
                }
            }

            // ---- AI scenario settings (visible only when AI is on) ----
            if (profile?.aiEnabled == true) {
                Text("Сценарий анализа", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Отключите этапы подтверждения, если доверяете ИИ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScenarioToggle(
                            title = "Пропустить правку списка",
                            subtitle = "ИИ сразу переходит к оценке веса",
                            checked = profile?.aiSkipListReview ?: false,
                            onCheckedChange = viewModel::setAiSkipListReview
                        )
                        ScenarioToggle(
                            title = "Пропустить правку граммовок",
                            subtitle = "ИИ сразу переходит к итоговому подсчёту",
                            checked = profile?.aiSkipGramsReview ?: false,
                            onCheckedChange = viewModel::setAiSkipGramsReview
                        )
                        ScenarioToggle(
                            title = "Пропустить финальное подтверждение",
                            subtitle = "Приём пищи сохраняется автоматически",
                            checked = profile?.aiSkipFinalReview ?: false,
                            onCheckedChange = viewModel::setAiSkipFinalReview
                        )
                    }
                }

                // ---- BYOK section ----
                Text("BYOK: подключение ИИ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Введите данные любого OpenAI-совместимого API. Ключ хранится в зашифрованном виде на устройстве.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::setBaseUrl,
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::setApiKey,
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.modelId,
                    onValueChange = viewModel::setModelId,
                    label = { Text("Model ID") },
                    placeholder = { Text("gpt-4o") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ValidationStatusCard(state.validation)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = viewModel::saveAndValidate,
                        enabled = state.validation.status != ValidationStatus.VALIDATING &&
                                state.apiKey.isNotBlank() && state.baseUrl.isNotBlank() && state.modelId.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.validation.status == ValidationStatus.VALIDATING) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                        }
                        Text("Сохранить и проверить")
                    }
                    if (state.apiKey.isNotBlank()) {
                        OutlinedButton(onClick = viewModel::clearApi) { Text("Очистить") }
                    }
                }
            }

            // ---- Profile summary ----
            Spacer(Modifier.height(8.dp))
            Text("Профиль", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    profile?.let {
                        Text("Возраст: ${it.age} • Рост: ${it.heightCm.toInt()} см • Вес: ${it.weightKg} кг",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Активность: ${it.activityLevel.labelRes}", style = MaterialTheme.typography.bodyMedium)
                        Text("Цель: ${it.goal.labelRes}", style = MaterialTheme.typography.bodyMedium)
                    } ?: Text("Загрузка…")
                }
            }

            // ---- Donate ----
            Spacer(Modifier.height(8.dp))
            Text("Поддержать проект", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.padding(4.dp))
                        Text("OpenCalori — бесплатный опенсорсный проект", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Если приложение вам полезно, вы можете поддержать автора добровольным пожертвованием.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sponsors")))
                    }) {
                        Text("Поддержать (GitHub Sponsors)")
                    }
                }
            }

            // ---- About ----
            Spacer(Modifier.height(8.dp))
            Text("О приложении", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "OpenCalori v0.1.1 • Лицензия GPL-3.0\nВсе данные хранятся локально на устройстве.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScenarioToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ValidationStatusCard(validation: com.opencalori.app.domain.model.ApiValidationResult) {
    when (validation.status) {
        ValidationStatus.IDLE -> {}
        ValidationStatus.VALIDATING -> {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.padding(6.dp))
                    Text("Проверка соединения и Vision…")
                }
            }
        }
        ValidationStatus.SUCCESS -> {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(6.dp))
                    Text(validation.message.ifBlank { "Подключено. Vision поддерживается." })
                }
            }
        }
        else -> {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.padding(6.dp))
                    Text(validation.message.ifBlank { "Ошибка проверки" })
                }
            }
        }
    }
}
