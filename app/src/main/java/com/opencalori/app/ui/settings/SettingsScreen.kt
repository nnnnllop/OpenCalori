package com.opencalori.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencalori.app.BuildConfig
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.ValidationStatus
import com.opencalori.app.ui.util.NumberFormat
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var keyVisible by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importFrom) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Profile ----
            SectionTitle("Профиль")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile?.let {
                        Text(
                            "Возраст " + it.age + " • Рост " + it.heightCm.toInt() + " см • Вес " +
                                NumberFormat.compact(it.weightKg) + " кг",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("Активность: " + it.activityLevel.label, style = MaterialTheme.typography.bodyMedium)
                        Text("Цель: " + it.goal.label, style = MaterialTheme.typography.bodyMedium)
                    } ?: Text("Загрузка")
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = onEditProfile) { Text("Изменить параметры") }
                }
            }

            // ---- AI ----
            SectionTitle("Искусственный интеллект")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
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

            if (profile?.aiEnabled == true) {
                SectionTitle("Сценарий анализа", small = true)
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

                SectionTitle("BYOK: подключение ИИ")
                Text(
                    "Подойдёт любой OpenAI-совместимый API. Ключ шифруется и остаётся на устройстве.",
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
                    visualTransformation = if (keyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { keyVisible = !keyVisible }) {
                            Text(if (keyVisible) "Скрыть" else "Показать")
                        }
                    },
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
                        enabled = state.canValidate,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.validation.status == ValidationStatus.VALIDATING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text("Сохранить и проверить")
                    }
                    if (state.apiKey.isNotBlank()) {
                        OutlinedButton(onClick = viewModel::clearApi) { Text("Очистить") }
                    }
                }
            }

            // ---- Backup ----
            SectionTitle("Данные")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Дневник хранится только на этом устройстве. Сделайте резервную копию " +
                            "перед сменой телефона или переустановкой.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            enabled = !state.busy,
                            onClick = {
                                exportLauncher.launch(
                                    viewModel.defaultBackupFileName(LocalDate.now().toString())
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.size(6.dp))
                            Text("Экспорт")
                        }
                        OutlinedButton(
                            enabled = !state.busy,
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null)
                            Spacer(Modifier.size(6.dp))
                            Text("Импорт")
                        }
                    }
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            // ---- Donate ----
            SectionTitle("Поддержать проект")
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(6.dp))
                        Text("OpenCalori — бесплатный опенсорсный проект", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Без рекламы, подписок и трекеров. Если приложение полезно, поддержите автора.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPONSORS_URL)))
                        }
                    ) {
                        Text("Поддержать на GitHub Sponsors")
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                        }
                    ) {
                        Text("Исходный код на GitHub")
                    }
                }
            }

            // ---- About ----
            SectionTitle("О приложении")
            Text(
                "OpenCalori " + BuildConfig.VERSION_NAME + " • GPL-3.0\n" +
                    "Все данные хранятся локально на устройстве.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private const val SPONSORS_URL = "https://github.com/sponsors/nnnnllop"
private const val REPO_URL = "https://github.com/nnnnllop/OpenCalori"

@Composable
private fun SectionTitle(text: String, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
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
private fun ValidationStatusCard(validation: ApiValidationResult) {
    when (validation.status) {
        ValidationStatus.IDLE -> Unit

        ValidationStatus.VALIDATING -> StatusCard(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(validation.message.ifBlank { "Проверяем" })
            }
        )

        ValidationStatus.SUCCESS -> StatusCard(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(validation.message.ifBlank { "Подключено" })
            }
        )

        ValidationStatus.NO_VISION -> StatusCard(
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.size(8.dp))
                Text(validation.message.ifBlank { "Модель не поддерживает изображения" })
            }
        )

        else -> StatusCard(
            container = MaterialTheme.colorScheme.errorContainer,
            content = {
                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(8.dp))
                Text(validation.message.ifBlank { "Ошибка проверки" })
            }
        )
    }
}

@Composable
private fun StatusCard(
    container: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}
