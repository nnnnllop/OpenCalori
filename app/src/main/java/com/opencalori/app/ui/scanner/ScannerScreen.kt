package com.opencalori.app.ui.scanner

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.ui.components.NumberField
import com.opencalori.app.ui.util.NumberFormat
import java.io.File
import java.nio.ByteBuffer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.stage) {
        if (state.stage == ScannerStage.SAVED) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сканер еды") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (state.photo != null && state.stage != ScannerStage.CAPTURE) {
                        TextButton(onClick = viewModel::retake) { Text("Переснять") }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.stage) {
                ScannerStage.NOT_CONFIGURED -> NotConfiguredStage(onOpenSettings)

                ScannerStage.CAPTURE -> CaptureStage(
                    mealType = state.mealType,
                    onMealTypeChange = viewModel::setMealType,
                    onPhoto = viewModel::onPhotoTaken,
                    onPickedFromGallery = viewModel::onImagePicked,
                    onCaptureFailed = viewModel::onCaptureFailed
                )

                ScannerStage.ANALYZING_1, ScannerStage.ANALYZING_2 -> AnalyzingStage(
                    state = state,
                    onCancel = viewModel::cancelAnalysis
                )

                ScannerStage.REVIEW_DISH -> ReviewDishStage(state, viewModel)
                ScannerStage.REVIEW_GRAMS -> ReviewGramsStage(state, viewModel)
                ScannerStage.REVIEW_FINAL -> ReviewFinalStage(state, viewModel)

                ScannerStage.ERROR -> ErrorStage(
                    message = state.error,
                    canRetry = state.photo != null,
                    onRetry = viewModel::retry,
                    onRetake = viewModel::retake
                )

                ScannerStage.SAVED -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun NotConfiguredStage(onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Key, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("ИИ ещё не подключён", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Сканер работает через ваш собственный ключ к любому OpenAI-совместимому API. " +
                "Добавьте Base URL, ключ и Model ID - это займёт минуту.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings) { Text("Настроить ИИ") }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CaptureStage(
    mealType: MealType,
    onMealTypeChange: (MealType) -> Unit,
    onPhoto: (ByteArray) -> Unit,
    onPickedFromGallery: (Uri) -> Unit,
    onCaptureFailed: (String?) -> Unit
) {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    var requested by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPickedFromGallery) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            requested = true
            cameraPermission.launchPermissionRequest()
        }
    }

    if (cameraPermission.status.isGranted) {
        CameraCaptureView(
            mealType = mealType,
            onMealTypeChange = onMealTypeChange,
            onPhoto = onPhoto,
            onCaptureFailed = onCaptureFailed,
            onPickFromGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
        return
    }

    // "Deny" twice on Android and the system stops showing the dialog entirely, so the
    // old "Разрешить" button silently did nothing. Offer the settings route instead.
    val permanentlyDenied = requested && !cameraPermission.status.shouldShowRationale

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Нужен доступ к камере", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied) {
                "Разрешение отключено. Включите камеру для OpenCalori в настройках системы " +
                    "или выберите готовое фото из галереи."
            } else {
                "Сканер снимает еду камерой. Можно и выбрать готовое фото из галереи."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        if (permanentlyDenied) {
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            ) { Text("Открыть настройки") }
        } else {
            Button(onClick = { cameraPermission.launchPermissionRequest() }) { Text("Разрешить") }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        ) {
            Icon(Icons.Default.PhotoLibrary, null)
            Spacer(Modifier.size(8.dp))
            Text("Выбрать из галереи")
        }
    }
}

@Composable
private fun CameraCaptureView(
    mealType: MealType,
    onMealTypeChange: (MealType) -> Unit,
    onPhoto: (ByteArray) -> Unit,
    onCaptureFailed: (String?) -> Unit,
    onPickFromGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var capturing by remember { mutableStateOf(false) }

    imageCapture.flashMode = flashMode

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val cameraProvider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            }.onFailure { onCaptureFailed("Камера недоступна на этом устройстве") }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Вспышка"
                )
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = mealType == type,
                        onClick = { onMealTypeChange(type) },
                        label = { Text(type.label) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPickFromGallery) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Выбрать из галереи")
                }

                FloatingActionButton(
                    onClick = {
                        if (capturing) return@FloatingActionButton
                        capturing = true
                        val file = File.createTempFile("food_", ".jpg", context.cacheDir)
                        val output = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            output,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    capturing = false
                                    val bytes = runCatching { file.readBytes() }.getOrNull()
                                    file.delete()
                                    if (bytes == null) {
                                        onCaptureFailed(null)
                                    } else {
                                        onPhoto(bytes)
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    capturing = false
                                    file.delete()
                                    // Previously this failed in total silence.
                                    onCaptureFailed(exception.message)
                                }
                            }
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Снять", modifier = Modifier.size(32.dp))
                }

                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun PhotoPreview(photo: ByteArray?, modifier: Modifier = Modifier) {
    if (photo == null) return
    val model = remember(photo) { ByteBuffer.wrap(photo) }
    AsyncImage(
        model = model,
        contentDescription = "Снятое фото",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
private fun AnalyzingStage(state: ScannerUiState, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PhotoPreview(state.photo)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            if (state.stage == ScannerStage.ANALYZING_1) "Определяю блюдо и состав…"
            else "Оцениваю вес и КБЖУ…",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text("Отменить") }
    }
}

@Composable
private fun ErrorStage(
    message: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onRetake: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Не получилось", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(
            message ?: "Неизвестная ошибка",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        if (canRetry) {
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.size(8.dp))
                Text("Повторить с этим фото")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onRetake) { Text("Сделать новое фото") }
    }
}

@Composable
private fun ReviewDishStage(state: ScannerUiState, viewModel: ScannerViewModel) {
    var newItem by remember { mutableStateOf("") }
    val dish = state.dish ?: return

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PhotoPreview(state.photo)
        Spacer(Modifier.height(12.dp))
        Text("Проверьте блюдо и состав", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "ИИ определил блюдо и список ингредиентов - поправьте, если что-то не так",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = dish.dishName,
            onValueChange = viewModel::updateDishName,
            label = { Text("Название блюда") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        Text("Ингредиенты", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(dish.ingredients, key = { _, item -> item.id }) { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = item.name,
                        onValueChange = { viewModel.updateIngredient(index, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = { viewModel.removeIngredient(index) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить " + item.name,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        placeholder = { Text("Добавить ингредиент") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            viewModel.addIngredient(newItem)
                            newItem = ""
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить ингредиент")
                    }
                }
            }
        }

        Button(
            onClick = viewModel::confirmIngredientsAndEstimate,
            enabled = dish.ingredients.any { it.name.isNotBlank() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Оценить вес и КБЖУ")
        }
    }
}

@Composable
private fun ReviewGramsStage(state: ScannerUiState, viewModel: ScannerViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Проверьте граммовки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "КБЖУ указаны на 100 г готового продукта. Сырой и готовый вес связаны увариванием: " +
                "меняете один - второй пересчитывается.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.gramsEditMode == GramsEditMode.COOKED,
                onClick = { viewModel.setGramsEditMode(GramsEditMode.COOKED) },
                label = { Text("Готовый вес") }
            )
            FilterChip(
                selected = state.gramsEditMode == GramsEditMode.RAW,
                onClick = { viewModel.setGramsEditMode(GramsEditMode.RAW) },
                label = { Text("Сырой/сухой вес") }
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.estimated, key = { _, item -> item.id }) { index, item ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.notes.isNotBlank()) {
                                Text(
                                    item.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.removeEstimated(index) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Убрать " + item.name,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (gramsValue, gramsLabel) = when (state.gramsEditMode) {
                                GramsEditMode.COOKED -> item.cookedGrams to "Готовый, г"
                                GramsEditMode.RAW -> item.rawGrams to "Сырой, г"
                            }
                            NumberField(
                                label = gramsLabel,
                                value = gramsValue,
                                onValueChange = { viewModel.setGrams(index, it) },
                                resetKey = item.id.toString() + state.gramsEditMode.name,
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                label = "Ккал/100 г",
                                value = item.caloriesPer100g,
                                onValueChange = {
                                    viewModel.updateEstimated(index, item.copy(caloriesPer100g = it))
                                },
                                resetKey = item.id,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                label = "Б",
                                value = item.proteinPer100g,
                                onValueChange = { viewModel.updateEstimated(index, item.copy(proteinPer100g = it)) },
                                resetKey = item.id,
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                label = "Ж",
                                value = item.fatPer100g,
                                onValueChange = { viewModel.updateEstimated(index, item.copy(fatPer100g = it)) },
                                resetKey = item.id,
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                label = "У",
                                value = item.carbsPer100g,
                                onValueChange = { viewModel.updateEstimated(index, item.copy(carbsPer100g = it)) },
                                resetKey = item.id,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))

                        Text(
                            "Съедено " + NumberFormat.compact(item.effectiveGrams) + " г • " +
                                item.totalCalories.toInt() + " ккал",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Button(
            onClick = viewModel::proceedToFinal,
            enabled = state.estimated.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Далее")
        }
    }
}

@Composable
private fun ReviewFinalStage(state: ScannerUiState, viewModel: ScannerViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Итоговый подсчёт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Проверьте значения перед сохранением",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.estimated, key = { _, item -> item.id }) { _, item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                NumberFormat.compact(item.effectiveGrams) + " г" +
                                    if (item.notes.isBlank()) "" else " (" + item.notes + ")",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                item.totalCalories.toInt().toString() + " ккал",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "Б " + item.totalProtein.toInt() + " • Ж " + item.totalFat.toInt() +
                                " • У " + item.totalCarbs.toInt(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.totalCalories.toInt().toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text("ккал • " + state.mealType.label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Б " + state.totalProtein.toInt() + " г • Ж " + state.totalFat.toInt() +
                        " г • У " + state.totalCarbs.toInt() + " г",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::saveMeal,
            enabled = state.estimated.isNotEmpty() && !state.saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.saving) "Сохраняем…" else "Сохранить в дневник")
        }
    }
}
