package com.opencalori.app.ui.textfood

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.ui.components.NumberField
import com.opencalori.app.ui.theme.AppShapes
import com.opencalori.app.ui.util.FoodQuantityValidation
import com.opencalori.app.ui.util.NumberFormat

/**
 * "Описать еду": AI names the dishes and their products, the user always types the weights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextFoodScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TextFoodViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Описать еду", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (state.stage) {
                                TextFoodStage.INPUT -> onBack()
                                TextFoodStage.DISHES -> viewModel.backToInput()
                                TextFoodStage.GRAMS -> viewModel.backToDishes()
                            }
                        }
                    ) {
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
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                if (state.manualRetryAvailable) {
                    TextButton(onClick = viewModel::retry) { Text("Повторить") }
                }
            }

            if (!state.aiAvailable) {
                AiUnavailableNotice(onOpenSettings)
            } else {
                when (state.stage) {
                    TextFoodStage.INPUT -> InputStep(state, viewModel)
                    TextFoodStage.DISHES -> DishesStep(state, viewModel)
                    TextFoodStage.GRAMS -> GramsStep(state, viewModel)
                }
            }
        }
    }
}

/**
 * The text flow is an AI feature end to end: with the AI switched off or no key stored the
 * description field is never shown, so nothing can be typed for sending.
 */
@Composable
private fun AiUnavailableNotice(onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = AppShapes.Small) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "ИИ выключен",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Описание еды разбирает модель по вашему ключу. Пока ИИ выключен или ключ не добавлен, текст никуда не отправляется. Записать еду можно вручную через поиск продуктов.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть настройки")
            }
        }
    }
}

@Composable
private fun InputStep(state: TextFoodState, viewModel: TextFoodViewModel) {
    Text(
        "AI определит блюда и состав. Вес каждого продукта вы укажете сами.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::setQuery,
        label = { Text("Что вы съели?") },
        placeholder = { Text("Например: паста карбонара, салат и яблоко") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5
    )
    MealTypeRow(state.mealType, viewModel::setMealType)
    Button(
        onClick = viewModel::recognize,
        enabled = state.canRecognize,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Icon(Icons.Default.AutoAwesome, null)
        Spacer(Modifier.size(8.dp))
        Text("Определить блюдо и состав")
    }
}

@Composable
private fun MealTypeRow(selected: MealType, onSelect: (MealType) -> Unit) {
    Column {
        Text("Приём пищи", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MealType.entries.forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    label = { Text(type.label, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.DishesStep(state: TextFoodState, viewModel: TextFoodViewModel) {
    var newDish by remember { mutableStateOf("") }
    Text(
        "Найдено блюд: " + state.dishes.size,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        "Отредактируйте состав, если что-то лишнее. Каждое блюдо сохранится отдельно.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(state.dishes, key = { _, dish -> dish.id }) { dishIndex, dish ->
            var newIngredient by remember(dish.id) { mutableStateOf("") }
            Card(Modifier.fillMaxWidth(), shape = AppShapes.Small) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dish.name,
                        onValueChange = { viewModel.renameDish(dishIndex, it) },
                        label = { Text("Название блюда") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    dish.ingredients.forEachIndexed { itemIndex, ingredient ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = ingredient.name,
                                onValueChange = { viewModel.renameIngredient(dishIndex, itemIndex, it) },
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            IconButton(
                                onClick = { viewModel.removeIngredient(dishIndex, itemIndex) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить " + ingredient.name,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newIngredient,
                            onValueChange = { newIngredient = it },
                            placeholder = { Text("Добавить продукт") },
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                        IconButton(
                            onClick = {
                                viewModel.addIngredient(dishIndex, newIngredient)
                                newIngredient = ""
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить продукт")
                        }
                    }
                    TextButton(onClick = { viewModel.removeDish(dishIndex) }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text("Удалить блюдо", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newDish,
                    onValueChange = { newDish = it },
                    placeholder = { Text("Добавить блюдо вручную") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                OutlinedButton(
                    onClick = {
                        viewModel.addDish(newDish)
                        newDish = ""
                    },
                    enabled = newDish.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Добавить блюдо")
                }
            }
        }
    }
    Button(
        onClick = viewModel::calculate,
        enabled = state.canCalculate,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Text("Рассчитать КБЖУ")
    }
}

@Composable
private fun ColumnScope.GramsStep(state: TextFoodState, viewModel: TextFoodViewModel) {
    Text(
        "Укажите вес каждого продукта",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        "AI не угадывает граммовки из текста, поэтому вес нужно ввести вручную.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.dishes.forEachIndexed { dishIndex, dish ->
            item(key = "dish-" + dish.id) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        dish.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (dish.hasValidGrams) dish.totalCalories.toInt().toString() + " ккал"
                        else "Вес указан не для всех продуктов",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dish.hasValidGrams) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
            itemsIndexed(
                items = dish.estimated,
                key = { _, item -> dish.id + "-" + item.id }
            ) { itemIndex, item ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.caloriesPer100g.toInt().toString() + " ккал / 100 г",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(
                            label = "Вес, г",
                            value = item.effectiveGrams,
                            onValueChange = { viewModel.setGrams(dishIndex, itemIndex, it) },
                            resetKey = item.id,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.removeEstimated(dishIndex, itemIndex) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить " + item.name,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    FoodQuantityValidation.errorMessage(item.effectiveGrams)?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (item.effectiveGrams > 0f) {
                        Text(
                            "Итого: " + item.totalCalories.toInt() + " ккал • " +
                                NumberFormat.compact(item.effectiveGrams) + " г",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
    Text(
        "Итого за запись: " + state.totalCalories.toInt() + " ккал",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Button(
        onClick = viewModel::save,
        enabled = state.canSave,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Icon(Icons.Default.Check, null)
        Spacer(Modifier.size(8.dp))
        Text("Сохранить в дневник")
    }
}
