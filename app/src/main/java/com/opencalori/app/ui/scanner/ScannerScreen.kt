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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
    return "Запись: ${date.format(formatter)}"
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
                        Text("Сканер еды")
                        Text(
                            scannerTargetDateLabel(state.targetDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f)
                    .fillMaxHeight(0.58f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, androidx.compose.ui.graphics.Color.White, AppShapes.Medium)
                )
                Text("Снимите блюдо целиком", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                Text("Если блюд несколько, не перекрывайте их", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodySmall)
            }

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
            ) {
                Icon(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> "\u0412\u0441\u043f\u044b\u0448\u043a\u0430 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u0430. \u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0432\u044b\u043a\u043b\u044e\u0447\u0438\u0442\u044c"
                        ImageCapture.FLASH_MODE_AUTO -> "\u0410\u0432\u0442\u043e\u0432\u0441\u043f\u044b\u0448\u043a\u0430. \u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0432\u043a\u043b\u044e\u0447\u0438\u0442\u044c \u0432\u0441\u043f\u044b\u0448\u043a\u0443"
                        else -> "\u0412\u0441\u043f\u044b\u0448\u043a\u0430 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u0430. \u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0432\u043a\u043b\u044e\u0447\u0438\u0442\u044c \u0430\u0432\u0442\u043e\u0432\u0441\u043f\u044b\u0448\u043a\u0443"
                    }
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
            .clip(AppShapes.Medium)
    )
}

@Composable
private fun ScanFlowProgress(currentStep: Int) {
    val steps = listOf("\u0421\u043e\u0441\u0442\u0430\u0432", "\u0412\u0435\u0441", "\u0418\u0442\u043e\u0433")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        steps.forEachIndexed { index, label ->
            val reached = index < currentStep
            Surface(
                modifier = Modifier.weight(1f),
                shape = AppShapes.Pill,
                color = if (reached) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    val step = if (firstStage) 1 else 2
    val progress = if (firstStage) 0.5f else 0.85f
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
            if (firstStage) "\u041e\u043f\u0440\u0435\u0434\u0435\u043b\u044f\u044e блюдо и состав…" else "\u0421\u043e\u043f\u043e\u0441\u0442\u0430\u0432\u043b\u044f\u044e с локальной базой…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "Шаг $step из 2 · обычно 5–15 секунд",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        OutlinedButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Key, null)
            Spacer(Modifier.size(8.dp))
            Text("\u041d\u0430\u0441\u0442\u0440\u043e\u0438\u0442\u044c \u0418\u0418")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRetake) { Text("\u0421\u0434\u0435\u043b\u0430\u0442\u044c \u043d\u043e\u0432\u043e\u0435 \u0444\u043e\u0442\u043e") }
    }
}

@Composable
private fun ReviewDishStage(state: ScannerUiState, viewModel: ScannerViewModel) {
    var newItem by remember { mutableStateOf("") }
    val dish = state.dish ?: return
    val nutritionSourceDescription = when (state.nutritionSourceMode) {
        com.opencalori.app.domain.model.NutritionSourceMode.AI_ONLY -> "КБЖУ и граммовки оценит ИИ по фото и подтверждённому составу."
        com.opencalori.app.domain.model.NutritionSourceMode.LOCAL_DATABASE -> "Состав определит ИИ, а КБЖУ найдём в локальном каталоге."
        com.opencalori.app.domain.model.NutritionSourceMode.HYBRID -> "ИИ определит блюдо, каталог поможет уточнить КБЖУ."
    }
    val nutritionAction = when (state.nutritionSourceMode) {
        com.opencalori.app.domain.model.NutritionSourceMode.AI_ONLY -> "Рассчитать КБЖУ с ИИ"
        com.opencalori.app.domain.model.NutritionSourceMode.LOCAL_DATABASE -> "Найти КБЖУ в локальной базе"
        com.opencalori.app.domain.model.NutritionSourceMode.HYBRID -> "Сверить КБЖУ по базе"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ScanFlowProgress(currentStep = 1)
        Spacer(Modifier.height(16.dp))
        PhotoPreview(state.photo)
        Spacer(Modifier.height(12.dp))
        Text("\u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 блюдо и состав", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            nutritionSourceDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.nutritionSourceMode != com.opencalori.app.domain.model.NutritionSourceMode.AI_ONLY && state.isLocalDraft) {
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = AppShapes.Small
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0439 черновик", fontWeight = FontWeight.Bold)
                    Text(
                        if (state.unmatchedIngredients.isEmpty()) {
                            "\u0411\u043b\u044e\u0434\u043e не найдено в общем каталоге. Оно не будет добавлено туда автоматически."
                        } else {
                            "\u041d\u0435 найдено в локальной базе: " + state.unmatchedIngredients.joinToString()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
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
            Text(nutritionAction)
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
        ScanFlowProgress(currentStep = 2)
        Spacer(Modifier.height(16.dp))
        Text("Проверьте граммовки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "КБЖУ указаны на 100 г готового продукта. Сырой и готовый вес связаны увариванием: " +
                "меняете один - второй пересчитывается.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        when {
            state.nutritionSourceMode != com.opencalori.app.domain.model.NutritionSourceMode.AI_ONLY && state.localDish != null -> {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = AppShapes.Small
                ) {
                    Text(
                        "\u041d\u0430\u0439\u0434\u0435\u043d\u043e локальное блюдо: " + state.localDish.name,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            state.nutritionSourceMode != com.opencalori.app.domain.model.NutritionSourceMode.AI_ONLY && state.isLocalDraft -> {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = AppShapes.Small
                ) {
                    Text(
                        "\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0439 черновик: в общий каталог ничего не добавляется. КБЖУ ниже взяты из найденных локальных продуктов.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

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
                    shape = AppShapes.Small,
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
                                enabled = false,
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
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                label = "Ж",
                                value = item.fatPer100g,
                                onValueChange = { viewModel.updateEstimated(index, item.copy(fatPer100g = it)) },
                                resetKey = item.id,
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                label = "У",
                                value = item.carbsPer100g,
                                onValueChange = { viewModel.updateEstimated(index, item.copy(carbsPer100g = it)) },
                                resetKey = item.id,
                                enabled = false,
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
        ScanFlowProgress(currentStep = 3)
        Spacer(Modifier.height(16.dp))
        Text("\u0418\u0442\u043e\u0433\u043e\u0432\u044b\u0439 \u0440\u0430\u0441\u0447\u0451\u0442", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "\u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f \u043f\u0435\u0440\u0435\u0434 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0435\u043c",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                "\u0420\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442 AI \u043e\u0440\u0438\u0435\u043d\u0442\u0438\u0440\u043e\u0432\u043e\u0447\u043d\u044b\u0439: \u043f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u043f\u043e\u0440\u0446\u0438\u044e, \u0441\u043e\u0441\u0442\u0430\u0432 \u0438 \u041a\u0411\u0416\u0423 \u043f\u0435\u0440\u0435\u0434 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0435\u043c.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.estimated, key = { _, item -> item.id }) { _, item ->
                Card(Modifier.fillMaxWidth(), shape = AppShapes.Small) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                NumberFormat.compact(item.effectiveGrams) + " \u0433" +
                                    if (item.notes.isBlank()) "" else " (" + item.notes + ")",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                item.totalCalories.toInt().toString() + " \u043a\u043a\u0430\u043b",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "\u0411 " + item.totalProtein.toInt() + " \u2022 \u0416 " + item.totalFat.toInt() +
                                " \u2022 \u0423 " + item.totalCarbs.toInt(),
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
                Text("\u043a\u043a\u0430\u043b \u2022 " + state.mealType.label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "\u0411 " + state.totalProtein.toInt() + " \u0433 \u2022 \u0416 " + state.totalFat.toInt() +
                        " \u0433 \u2022 \u0423 " + state.totalCarbs.toInt() + " \u0433",
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
            Text(if (state.saving) "\u0421\u043e\u0445\u0440\u0430\u043d\u044f\u0435\u043c\u2026" else "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0432 \u0434\u043d\u0435\u0432\u043d\u0438\u043a")
        }
    }
}
