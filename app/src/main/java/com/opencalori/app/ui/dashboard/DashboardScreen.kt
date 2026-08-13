package com.opencalori.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.WeightEntry
import com.opencalori.app.ui.components.CalorieRing
import com.opencalori.app.ui.components.MacroSummaryRow
import com.opencalori.app.ui.components.WeightChart
import com.opencalori.app.ui.util.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToScanner: (Long) -> Unit,
    onNavigateToSearch: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var weightDialogVisible by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<FoodItem?>(null) }
    var addFoodSheetVisible by remember { mutableStateOf(false) }

    // Every destructive action is undoable, which is why none of them ask for confirmation.
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (state.canUndo) "Отменить" else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("OpenCalori") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addFoodSheetVisible = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Добавить еду") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DateNavigator(
                    date = state.date,
                    isToday = state.isToday,
                    onPrev = viewModel::previousDay,
                    onNext = viewModel::nextDay,
                    onToday = viewModel::goToToday
                )
            }

            item {
                CalorieRing(
                    title = if (state.isToday) "Калории сегодня" else "Калории за день",
                    consumed = state.consumedCalories,
                    target = state.goal?.targetCalories?.toFloat() ?: 2000f
                )
            }

            item {
                MacroSummaryRow(
                    protein = state.consumedProtein to (state.goal?.proteinGrams?.toFloat() ?: 100f),
                    fat = state.consumedFat to (state.goal?.fatGrams?.toFloat() ?: 70f),
                    carbs = state.consumedCarbs to (state.goal?.carbsGrams?.toFloat() ?: 250f)
                )
            }

            item {
                WeightCard(
                    currentWeight = state.currentWeight,
                    onAddClick = { weightDialogVisible = true }
                )
            }

            if (state.weightHistory.size >= 2) {
                item { WeightChartCard(history = state.weightHistory) }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Приёмы пищи", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AssistChip(
                        onClick = viewModel::requestRepeatPreviousDay,
                        label = { Text("Повторить вчера") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.width(18.dp)) }
                    )
                }
            }

            if (state.meals.isEmpty()) {
                item { EmptyDiaryCard() }
            } else {
                items(state.meals, key = { it.id }) { meal ->
                    MealCard(
                        meal = meal,
                        onDeleteMeal = { viewModel.deleteMeal(meal) },
                        onDeleteItem = { item -> viewModel.deleteFoodItem(meal, item) },
                        onEditItem = { item -> editingItem = item }
                    )
                }
            }
        }
    }

    if (addFoodSheetVisible) {
        ModalBottomSheet(onDismissRequest = { addFoodSheetVisible = false }) {
            AddFoodBottomSheet(
                scannerReady = state.aiEnabled && state.scannerReady,
                onScan = {
                    addFoodSheetVisible = false
                    if (state.aiEnabled && state.scannerReady) {
                        onNavigateToScanner(state.date.toEpochDay())
                    } else {
                        onNavigateToSettings()
                    }
                },
                onSearch = {
                    addFoodSheetVisible = false
                    onNavigateToSearch(state.date.toEpochDay())
                }
            )
        }
    }
    if (weightDialogVisible) {
        WeightInputDialog(
            initial = state.currentWeight,
            onDismiss = { weightDialogVisible = false },
            onSave = { value ->
                viewModel.addWeight(value)
                weightDialogVisible = false
            }
        )
    }

    editingItem?.let { item ->
        GramsEditDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { grams ->
                viewModel.updateItemGrams(item, grams)
                editingItem = null
            }
        )
    }
    state.pendingRepeat?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRepeatPreviousDay,
            title = { Text("Заменить дневник?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Будет скопировано ${preview.itemCount} продуктов из ${preview.mealCount} приёмов пищи за ${preview.sourceDate}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Текущие записи за выбранный день будут заменены. Сразу после этого действие можно отменить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRepeatPreviousDay) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRepeatPreviousDay) { Text("Отмена") } }
        )
    }
}

@Composable
private fun AddFoodBottomSheet(
    scannerReady: Boolean,
    onScan: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Добавить еду", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Выберите удобный способ записи в дневник",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Icon(if (scannerReady) Icons.Default.PhotoCamera else Icons.Default.Key, null)
            Spacer(Modifier.width(8.dp))
            Text(if (scannerReady) "Сканировать по фото" else "Подключить ИИ-сканер")
        }
        OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("Найти или добавить вручную")
        }
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru")) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Предыдущий день") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isToday) "Сегодня" else date.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!isToday) {
                TextButton(onClick = onToday) { Text("Вернуться к сегодня") }
            }
        }
        IconButton(onClick = onNext, enabled = !isToday) {
            Icon(Icons.Default.ChevronRight, "Следующий день")
        }
    }
}

@Composable
private fun WeightCard(currentWeight: Float?, onAddClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(
            Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Текущий вес", style = MaterialTheme.typography.labelLarge)
                Text(
                    currentWeight?.let { NumberFormat.compact(it) + " кг" } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Записать")
            }
        }
    }
}

@Composable
private fun WeightChartCard(history: List<WeightEntry>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Динамика веса", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            WeightChart(
                history = history,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }
    }
}

@Composable
private fun EmptyDiaryCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Пока нет записей.\nСфотографируйте еду или найдите продукт вручную.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    onDeleteMeal: () -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    onEditItem: (FoodItem) -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(meal.mealType.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meal.totalCalories.toInt().toString() + " ккал",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDeleteMeal,
                        modifier = Modifier.semantics { contentDescription = "Удалить приём пищи" }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            meal.items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            NumberFormat.compact(item.grams) + " г • " + item.calories.toInt() + " ккал",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onEditItem(item) }) { Text("Изменить") }
                    IconButton(onClick = { onDeleteItem(item) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить " + item.name,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightInputDialog(initial: Float?, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { NumberFormat.compact(it) } ?: "") }
    val value = NumberFormat.parse(text)
    val hasWeightError = text.isNotEmpty() && (value == null || value !in 30f..300f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Записать вес") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = NumberFormat.sanitizeDecimalInput(it, maxLength = 5) },
                label = { Text("Вес, кг") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = hasWeightError,
                supportingText = {
                    Text(if (hasWeightError) "Введите вес от 30 до 300 кг" else "От 30 до 300 кг")
                },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onSave) },
                enabled = value != null && value in 30f..300f
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun GramsEditDialog(item: FoodItem, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var text by remember(item.id) { mutableStateOf(NumberFormat.compact(item.grams)) }
    val grams = NumberFormat.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = NumberFormat.sanitizeDecimalInput(it) },
                    label = { Text("Масса, г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Text(
                    "Итого: " + ((grams ?: 0f) * item.caloriesPer100g / 100f).toInt() + " ккал",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { grams?.let(onSave) }, enabled = grams != null && grams > 0f) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
