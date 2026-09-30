package com.goodlife.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.FoodAnalysis
import com.goodlife.app.data.Meal
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.ScreenTitle
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.toNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ScanScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val scope = rememberCoroutineScope()

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<FoodAnalysis?>(null) }

    fun analyze(bmp: Bitmap) {
        photo = bmp; result = null; error = null; loading = true
        scope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) { bmp.toJpeg() }
                result = Gemini(settings.apiKey, settings.model).analyzeFood(jpeg, profile)
            } catch (e: Exception) {
                error = e.message ?: "Analyse impossible."
            } finally {
                loading = false
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = loadBitmap(context, uri)
            if (bmp != null) analyze(bmp) else error = "Image illisible."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("Scanner un repas", "Cadre bien ton assiette, l'IA estime les calories")
        if (settings.apiKey.isBlank()) {
            val left = settings.relayRemainingToday()
            Text(
                if (left > 0) "Analyses gratuites restantes aujourd'hui : $left/${com.goodlife.app.data.Settings.RELAY_DAILY_LIMIT}"
                else "Plus d'analyses gratuites aujourd'hui. Ajoute ta clé dans Paramètres pour continuer.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (left > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        val current = photo
        if (current == null) {
            if (!hasCamera) {
                SectionCard(title = "Accès à la caméra") {
                    Text("GoodLife a besoin de la caméra pour photographier tes repas directement dans l'app.")
                    Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
                }
            } else {
                var capture by remember { mutableStateOf<ImageCapture?>(null) }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black)
                ) {
                    InAppCamera(onReady = { capture = it })
                    Box(
                        Modifier.align(Alignment.Center).fillMaxWidth(0.75f).aspectRatio(1f)
                            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)), RoundedCornerShape(32.dp))
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.size(56.dp)
                    ) { Icon(Icons.Filled.PhotoLibrary, "Galerie") }
                    FilledIconButton(
                        onClick = {
                            capture?.let { ic ->
                                takePhoto(context, ic, onPhoto = { analyze(it) }, onFail = { error = it })
                            }
                        },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors()
                    ) { Icon(Icons.Filled.PhotoCamera, "Prendre la photo", Modifier.size(36.dp)) }
                    Spacer(Modifier.size(56.dp))
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Photo du repas",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(28.dp))
            )
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Analyse en cours…")
                }
                error != null -> {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { analyze(current) }) { Text("Réessayer") }
                        OutlinedButton(onClick = { photo = null; error = null }) { Text("Nouvelle photo") }
                    }
                }
                result != null -> ResultCard(
                    r = result!!,
                    onAdd = { meal ->
                        Repo.addMeal(meal)
                        photo = null; result = null
                        onDone()
                    },
                    onRetake = { photo = null; result = null }
                )
            }
        }
    }
}

@Composable
private fun ResultCard(r: FoodAnalysis, onAdd: (Meal) -> Unit, onRetake: () -> Unit) {
    var name by remember(r) { mutableStateOf(r.dish) }
    var kcal by remember(r) { mutableStateOf(r.kcal.toString()) }

    if (r.allergens.isNotEmpty()) {
        SectionCard(
            title = "Attention allergies",
            icon = Icons.Filled.Warning,
            container = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(r.allergens.joinToString(", "), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }

    SectionCard {
        Text("${r.kcal} kcal", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
        Text(
            "Protéines ${r.proteinG.toInt()} g · Glucides ${r.carbsG.toInt()} g · Lipides ${r.fatG.toInt()} g",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Confiance de l'IA : ${(r.confidence * 100).toInt()} %",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        r.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        if (r.advice.isNotBlank()) {
            Text(r.advice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Nom du repas") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = kcal, onValueChange = { kcal = it },
            label = { Text("Calories (modifiable)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = r.kcal > 0 || (kcal.toNumber() ?: 0.0) > 0,
                onClick = {
                    val finalKcal = kcal.toNumber()?.toInt() ?: r.kcal
                    // Si l'utilisateur corrige les kcal, on ajuste les macros au prorata
                    val ratio = if (r.kcal > 0) finalKcal.toDouble() / r.kcal else 1.0
                    onAdd(
                        Meal(
                            name = name.ifBlank { r.dish },
                            kcal = finalKcal,
                            proteinG = r.proteinG * ratio,
                            carbsG = r.carbsG * ratio,
                            fatG = r.fatG * ratio,
                            details = r.items.joinToString("\n"),
                            source = "photo"
                        )
                    )
                }
            ) { Text("Ajouter au journal") }
            OutlinedButton(onClick = onRetake) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reprendre")
            }
        }
    }
}

/** Caméra intégrée à l'app (CameraX) : l'appli photo du téléphone n'est jamais ouverte. */
@Composable
private fun InAppCamera(onReady: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                onReady(capture)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) runCatching { future.get().unbindAll() }
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun takePhoto(
    context: Context,
    capture: ImageCapture,
    onPhoto: (Bitmap) -> Unit,
    onFail: (String) -> Unit
) {
    capture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val raw = runCatching { image.toBitmap() }.getOrNull()
                image.close()
                if (raw == null) {
                    onFail("Photo illisible, réessaie.")
                    return
                }
                val bmp = if (rotation != 0) {
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                        Matrix().apply { postRotate(rotation.toFloat()) }, true)
                } else raw
                onPhoto(bmp.scaledTo(1600))
            }

            override fun onError(exception: ImageCaptureException) {
                onFail("Erreur caméra : ${exception.message}")
            }
        }
    )
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = 1600f / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.scaledTo(1600)
    }
}.getOrNull()

private fun Bitmap.scaledTo(maxSide: Int): Bitmap {
    val scale = maxSide.toFloat() / maxOf(width, height)
    return if (scale < 1f) Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    else this
}

private fun Bitmap.toJpeg(maxSide: Int = 1024): ByteArray {
    val b = scaledTo(maxSide)
    return ByteArrayOutputStream().use { out ->
        b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}
