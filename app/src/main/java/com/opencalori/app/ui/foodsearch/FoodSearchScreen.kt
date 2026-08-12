package com.opencalori.app.ui.foodsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.ProductSource
import com.opencalori.app.ui.util.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    onBack: () -> Unit,
    viewModel: FoodSearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val results by viewModel.results.collectAsState()

    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var creatingProduct by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.resetSaved()
            onBack()
        }
    }

    val visible = if (state.query.isBlank()) state.recents else results

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить продукт") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creatingProduct = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Свой продукт") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Начните вводить название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            if (state.query.isBlank() && state.recents.isNotEmpty()) {
                Text("Недавнее", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }

            if (visible.isEmpty()) {
                EmptyState(query = state.query, onCreate = { creatingProduct = true })
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(visible, key = { it.key }) { product ->
                    ProductRow(product) { selectedProduct = product }
                }
            }
        }
    }

    selectedProduct?.let { product ->
        AddProductDialog(
            product = product,
            onDismiss = { selectedProduct = null },
            onAdd = { grams, mealType ->
                viewModel.addProductToMeal(product, grams, mealType)
                selectedProduct = null
            }
        )
    }

    if (creatingProduct) {
        CreateProductDialog(
            initialName = state.query,
            onDismiss = { creatingProduct = false },
            onCreate = { product, grams, mealType ->
                viewModel.addCustomProduct(product, grams, mealType)
                creatingProduct = false
            }
        )
    }
}

@Composable
private fun EmptyState(query: String, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(
            if (query.isBlank()) "Найдите продукт или добавьте свой" else "Ничего не нашлось",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        AssistChip(
            onClick = onCreate,
            label = {
                Text(if (query.isBlank()) "Добавить свой продукт" else "Добавить: " + query)
            },
            leadingIcon = { Icon(Icons.Default.Add, null) }
        )
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (product.source) {
                    ProductSource.CUSTOM -> Icon(
                        Icons.Default.Person,
                        contentDescription = "Свой продукт",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    ProductSource.RECENT -> Icon(
                        Icons.Default.History,
                        contentDescription = "Из недавнего",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ProductSource.BUILT_IN -> Unit
                }
                if (product.source != ProductSource.BUILT_IN) Spacer(Modifier.size(6.dp))
                Text(product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                product.caloriesPer100g.toInt().toString() + " ккал • Б " +
                    NumberFormat.compact(product.proteinPer100g) + " • Ж " +
                    NumberFormat.compact(product.fatPer100g) + " • У " +
                    NumberFormat.compact(product.carbsPer100g) + " (на 100 г)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onAdd: (grams: Float, mealType: MealType) -> Unit
) {
    var grams by remember { mutableStateOf("100") }
    var mealType by remember { mutableStateOf(MealType.SNACK) }
    val gramsValue = NumberFormat.parse(grams) ?: 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = NumberFormat.sanitizeDecimalInput(it) },
                    label = { Text("Масса, г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Приём пищи", style = MaterialTheme.typography.labelLarge)
                MealTypeRow(selected = mealType, onSelect = { mealType = it })
                Text(
                    "Итого: " + (product.caloriesPer100g * gramsValue / 100f).toInt() + " ккал",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(gramsValue, mealType) }, enabled = gramsValue > 0f) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun CreateProductDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onCreate: (Product, Float, MealType) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("100") }
    var mealType by remember { mutableStateOf(MealType.SNACK) }

    val caloriesValue = NumberFormat.parse(calories)
    val gramsValue = NumberFormat.parse(grams) ?: 0f
    val valid = name.isNotBlank() && caloriesValue != null && gramsValue > 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой продукт") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("На 100 г", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroInput("Ккал", calories, { calories = it }, Modifier.weight(1f))
                    MacroInput("Б", protein, { protein = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroInput("Ж", fat, { fat = it }, Modifier.weight(1f))
                    MacroInput("У", carbs, { carbs = it }, Modifier.weight(1f))
                }
                MacroInput("Съедено, г", grams, { grams = it }, Modifier.fillMaxWidth())
                MealTypeRow(selected = mealType, onSelect = { mealType = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onCreate(
                        Product(
                            id = 0,
                            name = name.trim(),
                            caloriesPer100g = caloriesValue ?: 0f,
                            proteinPer100g = NumberFormat.parse(protein) ?: 0f,
                            fatPer100g = NumberFormat.parse(fat) ?: 0f,
                            carbsPer100g = NumberFormat.parse(carbs) ?: 0f,
                            source = ProductSource.CUSTOM
                        ),
                        gramsValue,
                        mealType
                    )
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun MacroInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(NumberFormat.sanitizeDecimalInput(it)) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun MealTypeRow(selected: MealType, onSelect: (MealType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MealType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
