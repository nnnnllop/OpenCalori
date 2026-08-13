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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarResult
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
import com.opencalori.app.data.backup.ImportMode
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.ValidationStatus
import com.opencalori.app.ui.theme.AppShapes
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
    var analysisOptionsExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::prepareImport) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (state.canUndoImport) "Отменить" else null
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoImport()
            else viewModel.consumeMessage()
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
            Card(Modifier.fillMaxWidth(), shape = AppShapes.Medium) {
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
            Card(Modifier.fillMaxWidth(), shape = AppShapes.Medium) {
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
                SectionTitle("\u041a\u043e\u043d\u0442\u0440\u043e\u043b\u044c AI", small = true)
                Card(Modifier.fillMaxWidth(), shape = AppShapes.Medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("\u041f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442\u0430", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "\u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e \u0432\u044b \u043f\u0440\u043e\u0432\u0435\u0440\u044f\u0435\u0442\u0435 \u0441\u043e\u0441\u0442\u0430\u0432, \u0432\u0435\u0441 \u0438 \u0438\u0442\u043e\u0433 \u043f\u0435\u0440\u0435\u0434 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0435\u043c.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { analysisOptionsExpanded = !analysisOptionsExpanded }) {
                            Text(if (analysisOptionsExpanded) "\u0421\u043a\u0440\u044b\u0442\u044c \u0434\u043e\u043f\u043e\u043b\u043d\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u0435 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438" else "\u0418\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u044d\u0442\u0430\u043f\u044b \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0438")
                        }
                        if (analysisOptionsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ScenarioToggle(
                                    title = "\u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c \u043f\u0440\u0430\u0432\u043a\u0443 \u0441\u043f\u0438\u0441\u043a\u0430",
                                    subtitle = "AI \u0441\u0440\u0430\u0437\u0443 \u043f\u0435\u0440\u0435\u0439\u0434\u0451\u0442 \u043a \u043e\u0446\u0435\u043d\u043a\u0435 \u0432\u0435\u0441\u0430",
                                    checked = profile?.aiSkipListReview ?: false,
                                    onCheckedChange = viewModel::setAiSkipListReview
                                )
                                ScenarioToggle(
                                    title = "\u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c \u043f\u0440\u0430\u0432\u043a\u0443 \u0433\u0440\u0430\u043c\u043c\u043e\u0432\u043e\u043a",
                                    subtitle = "AI \u0441\u0440\u0430\u0437\u0443 \u043f\u0435\u0440\u0435\u0439\u0434\u0451\u0442 \u043a \u0438\u0442\u043e\u0433\u043e\u0432\u043e\u043c\u0443 \u043f\u043e\u0434\u0441\u0447\u0451\u0442\u0443",
                                    checked = profile?.aiSkipGramsReview ?: false,
                                    onCheckedChange = viewModel::setAiSkipGramsReview
                                )
                                ScenarioToggle(
                                    title = "\u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0442\u0438\u044c \u0444\u0438\u043d\u0430\u043b\u044c\u043d\u043e\u0435 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u0435",
                                    subtitle = "\u041f\u0440\u0438\u0451\u043c \u043f\u0438\u0449\u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u0441\u044f \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438",
                                    checked = profile?.aiSkipFinalReview ?: false,
                                    onCheckedChange = viewModel::setAiSkipFinalReview
                                )
                            }
                        }
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
                    isError = state.baseUrlError != null,
                    supportingText = { Text(state.baseUrlError ?: "Адрес OpenAI-совместимого API") },
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
                    supportingText = { Text("Хранится зашифрованно только на этом устройстве") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.modelId,
                    onValueChange = viewModel::setModelId,
                    label = { Text("Model ID") },
                    placeholder = { Text("gpt-4o") },
                    supportingText = { Text("Идентификатор модели у выбранного провайдера") },
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
            Card(Modifier.fillMaxWidth(), shape = AppShapes.Medium) {
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
                    state.pendingImport?.let { pending ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = AppShapes.Small
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Проверка резервной копии", fontWeight = FontWeight.Bold)
                                Text(
                                    "Найдено: ${pending.preview.items} продуктов, ${pending.preview.weights} замеров веса, ${pending.preview.products} своих продуктов.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (pending.preview.duplicateItems > 0 || pending.preview.duplicateProducts > 0) {
                                    Text(
                                        "Будет пропущено дублей: ${pending.preview.duplicateItems + pending.preview.duplicateProducts}.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Text(
                                    "Объединить добавит только новые записи. Заменить очистит текущий дневник и восстановит данные из файла.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = viewModel::dismissImportPreview) { Text("Отмена") }
                                    TextButton(onClick = { viewModel.confirmImport(ImportMode.MERGE) }) { Text("Объединить") }
                                    TextButton(onClick = { viewModel.confirmImport(ImportMode.REPLACE) }) { Text("Заменить") }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Donate ----
            SectionTitle("Поддержать проект")
            Card(
                Modifier.fillMaxWidth(),
                shape = AppShapes.Medium,
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
