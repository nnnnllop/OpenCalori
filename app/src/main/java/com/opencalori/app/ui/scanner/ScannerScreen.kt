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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.opencalori.app.domain.model.NutritionSourceMode
import com.opencalori.app.ui.components.NumberField
import com.opencalori.app.ui.theme.AppShapes
import com.opencalori.app.ui.util.NumberFormat
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun scannerTargetDateLabel(date: LocalDate): String {
    if (date == LocalDate.now()) return "Запись: сегодня"
    val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
    return "Запись: " + date.format(formatter)
}

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
                title = {
                    Column {
                        Text("Сканер еды", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            scannerTargetDateLabel(state.targetDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
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

                ScannerStage.REVIEW_DISHES -> ReviewDishesStage(state, viewModel)
                ScannerStage.REVIEW_DISH -> ReviewDishStage(state, viewModel)
                ScannerStage.REVIEW_GRAMS -> ReviewGramsStage(state, viewModel)
                ScannerStage.REVIEW_FINAL -> ReviewFinalStage(state, viewModel)

                ScannerStage.ERROR -> ErrorStage(
                    message = state.error,
                    canRetry = state.photo != null,
                    onRetry = viewModel::retry,
                    onRetake = viewModel::retake,
                    onOpenSettings = onOpenSettings
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

            // Guide overlay only: the captured photo is never cropped to this frame, so a
            // second dish just outside it is still sent to the model.
            FramingOverlay(Modifier.fillMaxSize())

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
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(48.dp)
            ) {
                Icon(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    tint = Color.White,
                    contentDescription = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> "Вспышка включена. Нажмите, чтобы выключить"
                        ImageCapture.FLASH_MODE_AUTO -> "Автовспышка. Нажмите, чтобы включить вспышку"
                        else -> "Вспышка выключена. Нажмите, чтобы включить автовспышку"
                    }
                )
            }
        }

        Column(
            Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hints live below the viewfinder on purpose: at font scale 1.5 they would be
            // clipped by the preview, and a clipped instruction is no instruction.
            Text(
                "Снимите блюдо целиком",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Если блюд несколько, не перекрывайте их. Рамка — только подсказка, фото не обрезается.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = mealType == type,
                        onClick = { onMealTypeChange(type) },
                        label = { Text(type.label, maxLines = 1) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPickFromGallery, modifier = Modifier.size(48.dp)) {
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

/** Dimmed area plus a centred frame. Pure decoration: it never crops the captured image. */
@Composable
private fun FramingOverlay(modifier: Modifier = Modifier) {
    val scrim = Color.Black.copy(alpha = 0.38f)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(scrim)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .weight(6f)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(scrim)
            )
            Box(
                Modifier
                    .weight(12f)
                    .fillMaxHeight()
                    .border(2.dp, Color.White, AppShapes.Medium)
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(scrim)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(2f)
                .background(scrim)
        )
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
            .clip(AppShapes.Medium)
    )
}

@Composable
private fun ScanFlowProgress(currentStep: Int) {
    val steps = listOf("Блюда", "Состав", "Вес", "Итог")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { index, label ->
            val reached = index < currentStep
            Surface(
                modifier = Modifier.weight(1f),
                shape = AppShapes.Pill,
                color = if (reached) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (reached) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (reached) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzingStage(state: ScannerUiState, onCancel: () -> Unit) {
    val firstStage = state.stage == ScannerStage.ANALYZING_1
    val progress = if (firstStage) 0.4f else 0.8f
    val localMode = state.nutritionSourceMode != NutritionSourceMode.AI_ONLY
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
            when {
                firstStage -> "Ищу блюда и состав…"
                localMode -> "Сопоставляю с локальной базой…"
                else -> "Оцениваю порции и КБЖУ…"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            if (firstStage) "Обычно 5–15 секунд" else "Считаю каждое блюдо отдельно",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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
    onRetake: () -> Unit,
    onOpenSettings: () -> Unit
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (canRetry) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.size(8.dp))
                Text("Повторить с этим фото")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
            Text("Сделать новое фото")
        }
    }
}

/** Stage 3: the list of recognised dishes. Each one can be renamed, dropped or added by hand. */
@Composable
private fun ReviewDishesStage(state: ScannerUiState, viewModel: ScannerViewModel) {
    var newDish by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        ScanFlowProgress(currentStep = 1)
        Spacer(Modifier.height(16.dp))
        PhotoPreview(state.photo)
        Spacer(Modifier.height(12.dp))
        Text("Найдено блюд: " + state.dishes.size, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Проверьте список. Каждое блюдо попадёт в дневник отдельной записью со своими продуктами.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.dishes, key = { _, dish -> dish.id }) { index, dish ->
                Card(Modifier.fillMaxWidth(), shape = AppShapes.Small) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dish.name,
                            onValueChange = { viewModel.renameDish(index, it) },
                            label = { Text("Название блюда") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                        Text(
                            ingredientSummary(dish),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dish.isLowConfidence) {
                            Text(
                                "ИИ не уверен в этом блюде — проверьте название и состав.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        // Column, not Row: three actions never fit one line at 360 dp / font 1.5.
                        Column {
                            TextButton(onClick = { viewModel.reviewDish(index) }) {
                                Icon(Icons.Default.Edit, null)
                                Spacer(Modifier.size(8.dp))
                                Text("Проверить состав")
                            }
                            TextButton(onClick = { viewModel.markDishUnknown(index) }) {
                                Text("Отметить как неизвестное")
                            }
                            TextButton(onClick = { viewModel.removeDish(index) }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.size(8.dp))
                                Text("Удалить блюдо", color = MaterialTheme.colorScheme.error)
                            }
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

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::confirmDishList,
            enabled = state.dishes.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text("Перейти к составу")
        }
    }
}

private fun ingredientSummary(dish: DishDraft): String {
    if (dish.ingredients.isEmpty()) return "Состав пока пуст — добавьте продукты вручную"
    return "Продукты: " + dish.ingredients.joinToString { it.name }
}

/** Stage 4: ingredients of one dish. */
@Composable
private fun ReviewDishStage(state: ScannerUiState, viewModel: ScannerViewModel) {
    var newItem by remember { mutableStateOf("") }
    val dish = state.currentDish ?: return
    val nutritionSourceDescription = when (state.nutritionSourceMode) {
        NutritionSourceMode.AI_ONLY -> "КБЖУ и граммовки оценит ИИ по фото и подтверждённому составу."
        NutritionSourceMode.LOCAL_DATABASE -> "Состав определит ИИ, а КБЖУ найдём в локальном каталоге."
        NutritionSourceMode.HYBRID -> "ИИ определит блюдо, каталог поможет уточнить КБЖУ."
    }
    val nutritionAction = when (state.nutritionSourceMode) {
        NutritionSourceMode.AI_ONLY -> "Рассчитать КБЖУ с ИИ"
        NutritionSourceMode.LOCAL_DATABASE -> "Найти КБЖУ в локальной базе"
        NutritionSourceMode.HYBRID -> "Сверить КБЖУ по базе"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        ScanFlowProgress(currentStep = 2)
        Spacer(Modifier.height(16.dp))
        PhotoPreview(state.photo)
        Spacer(Modifier.height(12.dp))

        Text("Проверьте блюдо и состав", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (state.hasMultipleDishes) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = viewModel::backToDishList,
                    label = { Text("Блюдо " + state.dishPositionLabel, maxLines = 1) }
                )
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = viewModel::backToDishList) { Text("К списку блюд") }
            }
        }
        Text(
            nutritionSourceDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        // Local-catalogue wording only exists outside AI-only mode.
        if (state.nutritionSourceMode != NutritionSourceMode.AI_ONLY && state.isLocalDraft) {
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = AppShapes.Small
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Локальный черновик", fontWeight = FontWeight.Bold)
                    Text(
                        if (state.unmatchedIngredients.isEmpty()) {
                            "Блюдо не найдено в общем каталоге. Оно не будет добавлено туда автоматически."
                        } else {
                            "Не найдено в локальной базе: " + state.unmatchedIngredients.joinToString()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = dish.name,
            onValueChange = viewModel::updateDishName,
            label = { Text("Название блюда") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
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
                        supportingText = if (item.isLowConfidence) {
                            { Text("Низкая уверенность ИИ") }
                        } else {
                            null
                        },
                        maxLines = 2
                    )
                    IconButton(
                        onClick = { viewModel.removeIngredient(index) },
                        modifier = Modifier.size(48.dp)
                    ) {
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
                        maxLines = 2
                    )
                    IconButton(
                        onClick = {
                            viewModel.addIngredient(newItem)
                            newItem = ""
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить ингредиент")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::confirmIngredientsAndEstimate,
            enabled = dish.ingredients.any { it.name.isNotBlank() },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(if (state.isLastDish) nutritionAction else "Далее: следующее блюдо")
        }
    }
}

/** Stage 6: grams per product, grouped by dish so nothing gets mixed up. */
@Composable
private fun ReviewGramsStage(state: ScannerUiState, viewModel: ScannerViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        ScanFlowProgress(currentStep = 3)
        Spacer(Modifier.height(16.dp))
        Text("Проверьте граммовки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "КБЖУ указаны на 100 г готового продукта. Сырой и готовый вес связаны увариванием: " +
                "меняете один - второй пересчитывается.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (state.nutritionSourceMode != NutritionSourceMode.AI_ONLY && state.isLocalDraft) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = AppShapes.Small
            ) {
                Text(
                    "Локальный черновик: в общий каталог ничего не добавляется. КБЖУ ниже взяты из найденных локальных продуктов.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.gramsEditMode == GramsEditMode.COOKED,
                onClick = { viewModel.setGramsEditMode(GramsEditMode.COOKED) },
                label = { Text("Готовый вес", maxLines = 1) }
            )
            FilterChip(
                selected = state.gramsEditMode == GramsEditMode.RAW,
                onClick = { viewModel.setGramsEditMode(GramsEditMode.RAW) },
                label = { Text("Сырой/сухой вес", maxLines = 1) }
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.dishes.forEachIndexed { dishIndex, dish ->
                if (dish.estimated.isEmpty()) return@forEachIndexed
                item(key = "dish-" + dish.id) {
                    DishSectionHeader(
                        name = dish.name,
                        subtitle = dish.estimated.size.toString() + " " + productWord(dish.estimated.size) +
                            " • " + dish.totalCalories.toInt() + " ккал"
                    )
                }
                itemsIndexed(
                    items = dish.estimated,
                    key = { _, item -> dish.id + "-" + item.id }
                ) { itemIndex, item ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = AppShapes.Small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.notes.isNotBlank()) {
                                        Text(
                                            item.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.removeEstimatedFor(dishIndex, itemIndex) },
                                    modifier = Modifier.size(48.dp)
                                ) {
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
                                    onValueChange = { viewModel.setGramsFor(dishIndex, itemIndex, it) },
                                    resetKey = item.id + state.gramsEditMode.name,
                                    modifier = Modifier.weight(1f)
                                )
                                NumberField(
                                    label = "Ккал/100 г",
                                    value = item.caloriesPer100g,
                                    onValueChange = {
                                        viewModel.updateEstimatedFor(
                                            dishIndex,
                                            itemIndex,
                                            item.copy(caloriesPer100g = it)
                                        )
                                    },
                                    resetKey = item.id,
                                    enabled = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField(
                                    label = "Б",
                                    value = item.proteinPer100g,
                                    onValueChange = {
                                        viewModel.updateEstimatedFor(dishIndex, itemIndex, item.copy(proteinPer100g = it))
                                    },
                                    resetKey = item.id,
                                    enabled = false,
                                    modifier = Modifier.weight(1f)
                                )
                                NumberField(
                                    label = "Ж",
                                    value = item.fatPer100g,
                                    onValueChange = {
                                        viewModel.updateEstimatedFor(dishIndex, itemIndex, item.copy(fatPer100g = it))
                                    },
                                    resetKey = item.id,
                                    enabled = false,
                                    modifier = Modifier.weight(1f)
                                )
                                NumberField(
                                    label = "У",
                                    value = item.carbsPer100g,
                                    onValueChange = {
                                        viewModel.updateEstimatedFor(dishIndex, itemIndex, item.copy(carbsPer100g = it))
                                    },
                                    resetKey = item.id,
                                    enabled = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(6.dp))

                            Text(
                                if (item.effectiveGrams > 0f) {
                                    "Съедено " + NumberFormat.compact(item.effectiveGrams) + " г • " +
                                        item.totalCalories.toInt() + " ккал"
                                } else {
                                    "Укажите вес, чтобы посчитать калории"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (item.effectiveGrams > 0f) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::proceedToFinal,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text("Далее")
        }
    }
}

@Composable
private fun DishSectionHeader(name: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun productWord(count: Int): String = when {
    count % 100 in 11..14 -> "продуктов"
    count % 10 == 1 -> "продукт"
    count % 10 in 2..4 -> "продукта"
    else -> "продуктов"
}

/** Stage 7: the final summary, still grouped per dish. */
@Composable
private fun ReviewFinalStage(state: ScannerUiState, viewModel: ScannerViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ScanFlowProgress(currentStep = 4)
        Spacer(Modifier.height(16.dp))
        Text("Итоговый расчёт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (state.hasMultipleDishes) {
                "Каждое блюдо сохранится отдельной записью в " + state.mealType.label.lowercase() + "."
            } else {
                "Проверьте значения перед сохранением"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                "Результат ИИ ориентировочный: проверьте порцию, состав и КБЖУ перед сохранением.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.dishes.forEachIndexed { _, dish ->
                if (dish.estimated.isEmpty()) return@forEachIndexed
                item(key = "final-dish-" + dish.id) {
                    DishSectionHeader(
                        name = dish.name,
                        subtitle = dish.totalCalories.toInt().toString() + " ккал"
                    )
                }
                itemsIndexed(
                    items = dish.estimated,
                    key = { _, item -> "final-" + dish.id + "-" + item.id }
                ) { _, item ->
                    Card(Modifier.fillMaxWidth(), shape = AppShapes.Small) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            // Column instead of a Row: grams and kcal must never squeeze each other.
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
        }
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    state.totalCalories.toInt().toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "ккал • " + state.mealType.label,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Б " + state.totalProtein.toInt() + " г • Ж " + state.totalFat.toInt() +
                        " г • У " + state.totalCarbs.toInt() + " г",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::saveMeal,
            enabled = state.canSave && !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(if (state.saving) "Сохраняем…" else "Сохранить в дневник")
        }
    }
}
