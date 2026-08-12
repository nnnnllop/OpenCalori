package com.opencalori.app.ui.scanner

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.opencalori.app.domain.model.MealType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сканер еды") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.stage) {
                ScannerStage.CAPTURE -> {
                    if (cameraPermission.status.isGranted) {
                        CameraCaptureView(
                            mealType = state.mealType,
                            onMealTypeChange = viewModel::setMealType,
                            onPhoto = viewModel::onPhotoTaken
                        )
                    } else {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Для сканера нужен доступ к камере")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                                Text("Разрешить")
                            }
                        }
                    }
                }
                ScannerStage.ANALYZING_1, ScannerStage.ANALYZING_2 -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (state.stage == ScannerStage.ANALYZING_1) "Определяю блюдо и состав…" else "Оцениваю вес и КБЖУ…",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                ScannerStage.REVIEW_DISH -> {
                    ReviewDishStage(state, viewModel)
                }
                ScannerStage.REVIEW_GRAMS -> {
                    ReviewGramsStage(state, viewModel)
                }
                ScannerStage.REVIEW_FINAL -> {
                    ReviewFinalStage(state, viewModel, onDone = onBack)
                }
                ScannerStage.ERROR -> {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Ошибка", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(state.error ?: "Неизвестная ошибка", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = viewModel::retry) { Text("Попробовать снова") }
                    }
                }
                ScannerStage.SAVED -> {
                    LaunchedEffect(Unit) { onBack() }
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureView(
    mealType: MealType,
    onMealTypeChange: (MealType) -> Unit,
    onPhoto: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (_: Exception) { }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = mealType == type,
                        onClick = { onMealTypeChange(type) },
                        label = { Text(type.label) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FloatingActionButton(
                    onClick = {
                        val file = File.createTempFile("food_", ".jpg", context.cacheDir)
                        val output = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            output,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    val bytes = file.readBytes()
                                    file.delete()
                                    onPhoto(bytes)
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    file.delete()
                                }
                            }
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Снять", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ReviewDishStage(state: ScannerUiState, vm: ScannerViewModel) {
    var newItem by remember { mutableStateOf("") }
    val dish = state.dish ?: return

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Text("Проверьте блюдо и состав", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "ИИ определил блюдо и список ингредиентов — отредактируйте при необходимости",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = dish.dishName,
            onValueChange = vm::updateDishName,
            label = { Text("Название блюда") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        Text("Ингредиенты", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(dish.ingredients) { index, item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { vm.updateIngredient(index, it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(onClick = { vm.removeIngredient(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        placeholder = { Text("Добавить ингредиент…") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = { vm.addIngredient(newItem); newItem = "" }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить")
                    }
                }
            }
        }

        Button(
            onClick = vm::confirmIngredientsAndEstimate,
            enabled = dish.ingredients.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Оценить вес и КБЖУ")
        }
    }
}

@Composable
private fun ReviewGramsStage(state: ScannerUiState, vm: ScannerViewModel) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Text("Проверьте граммовки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "ИИ оценил вес каждого ингредиента — отредактируйте при необходимости",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // Raw / Cooked toggle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.gramsEditMode == GramsEditMode.COOKED,
                onClick = { vm.setGramsEditMode(GramsEditMode.COOKED) },
                label = { Text("Готовый вес") }
            )
            FilterChip(
                selected = state.gramsEditMode == GramsEditMode.RAW,
                onClick = { vm.setGramsEditMode(GramsEditMode.RAW) },
                label = { Text("Сырой/сухой вес") }
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.estimated) { index, item ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (item.notes.isNotBlank()) {
                                Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.removeEstimated(index) }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (gramsValue, gramsLabel) = when (state.gramsEditMode) {
                                GramsEditMode.COOKED -> item.cookedGrams.toString() to "Готовый, г"
                                GramsEditMode.RAW -> item.rawGrams.toString() to "Сырой, г"
                            }
                            GramsField(gramsLabel, gramsValue, Modifier.weight(1f)) { v ->
                                val newVal = v.toFloatOrNull() ?: 0f
                                vm.updateEstimated(index, when (state.gramsEditMode) {
                                    GramsEditMode.COOKED -> item.copy(cookedGrams = newVal)
                                    GramsEditMode.RAW -> item.copy(rawGrams = newVal)
                                })
                            }
                            GramsField("Ккал/100г", item.caloriesPer100g.toString(), Modifier.weight(1f)) { v ->
                                vm.updateEstimated(index, item.copy(caloriesPer100g = v.toFloatOrNull() ?: item.caloriesPer100g))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GramsField("Б", item.proteinPer100g.toString(), Modifier.weight(1f)) { v ->
                                vm.updateEstimated(index, item.copy(proteinPer100g = v.toFloatOrNull() ?: item.proteinPer100g))
                            }
                            GramsField("Ж", item.fatPer100g.toString(), Modifier.weight(1f)) { v ->
                                vm.updateEstimated(index, item.copy(fatPer100g = v.toFloatOrNull() ?: item.fatPer100g))
                            }
                            GramsField("У", item.carbsPer100g.toString(), Modifier.weight(1f)) { v ->
                                vm.updateEstimated(index, item.copy(carbsPer100g = v.toFloatOrNull() ?: item.carbsPer100g))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Итого (${if (state.gramsEditMode == GramsEditMode.COOKED) "готовый" else "сырой"}): ${item.totalCalories.toInt()} ккал",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Button(
            onClick = vm::proceedToFinal,
            enabled = state.estimated.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Далее")
        }
    }
}

@Composable
private fun ReviewFinalStage(state: ScannerUiState, vm: ScannerViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Text("Итоговый подсчёт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Проверьте итоговые значения перед сохранением",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.estimated) { index, item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${item.effectiveGrams.toInt()} г (${item.notes})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${item.totalCalories.toInt()} ккал",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "Б ${item.totalProtein.toInt()} • Ж ${item.totalFat.toInt()} • У ${item.totalCarbs.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val totalCal = state.estimated.sumOf { it.totalCalories.toDouble() }
        val totalP = state.estimated.sumOf { it.totalProtein.toDouble() }
        val totalF = state.estimated.sumOf { it.totalFat.toDouble() }
        val totalC = state.estimated.sumOf { it.totalCarbs.toDouble() }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${totalCal.toInt()}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("ккал • ${state.mealType.label}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Б ${totalP.toInt()} г • Ж ${totalF.toInt()} г • У ${totalC.toInt()} г",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.saveMeal(onDone) },
            enabled = state.estimated.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить в дневник")
        }
    }
}

@Composable
private fun GramsField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' || c == '-' }) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier
    )
}
