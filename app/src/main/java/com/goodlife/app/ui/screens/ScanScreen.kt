@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.FlowRow
import com.goodlife.app.i18n.t

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.goodlife.app.food.detectFoodBarcode
import com.goodlife.app.net.FoodProduct
import com.goodlife.app.net.OpenFoodFacts
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.toNumber
import com.goodlife.app.ui.AiContentFooter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }

    val aiReady = settings.aiEnabled && com.goodlife.app.ai.AiAccess.ready(settings)
    var mode by rememberSaveable { mutableStateOf(if (aiReady) "photo" else "barcode") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<FoodAnalysis?>(null) }
    var product by remember { mutableStateOf<FoodProduct?>(null) }
    var askConsent by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }
    var newDex by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastJpeg by remember { mutableStateOf<ByteArray?>(null) }
    var chat by remember { mutableStateOf(false) }
    var showDex by remember { mutableStateOf(false) }
    var fridgeAsk by remember { mutableStateOf<List<Pair<String, Double>>?>(null) }

    fun reset() { photo = null; result = null; product = null; error = null; loading = null; newDex = emptyList() }

    fun analyze(bmp: Bitmap) {
        photo = bmp; result = null; product = null; error = null
        scope.launch {
            try {
                if (mode == "photo") {
                    loading = t("Analyse du repas par l'IA…")
                    val jpeg = withContext(Dispatchers.Default) { bmp.toJpeg() }
                    lastJpeg = jpeg
                    val fridgeList = if (settings.fridgeAutoRemove) com.goodlife.app.data.Fridge.promptList() else ""
                    val r = Gemini(settings.apiKey, settings.model).analyzeFood(jpeg, profile, fridgeList)
                    result = r
                    // Nutridex : les aliments reconnus se débloquent avec une petite vignette de la photo
                    if (r.dexIds.isNotEmpty()) {
                        val thumb = withContext(Dispatchers.Default) { bmp.dexThumbnail() }
                        newDex = Repo.unlockDex(r.dexIds, thumb)
                    }
                } else {
                    loading = t("Lecture du code-barres…")
                    val code = detectFoodBarcode(bmp)
                    if (code == null) {
                        error = t("Aucun code-barres trouvé. Rapproche-toi et cadre bien le code.")
                    } else {
                        loading = t("Recherche du produit…")
                        product = OpenFoodFacts.product(code)
                        if (product == null) error = t("Produit %1\$s introuvable dans Open Food Facts. Tu peux le saisir à la main depuis l'accueil.", code)
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: t("Analyse impossible.")
            } finally {
                loading = null
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            val bmp = loadBitmap(context, picked)
            if (bmp != null) analyze(bmp) else error = t("Image illisible.")
        }
    }

    val header: @Composable () -> Unit = {
        ScreenTitle(t("Scanner"), if (mode == "photo") t("Photo du repas : l'IA estime les calories") else t("Code-barres d'un produit emballé"))
        // Les deux modes passent à la ligne si la place manque (paysage, grande police) au lieu de couper les mots
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = mode == "photo", onClick = { mode = "photo"; reset() },
                label = { Text(t("Photo (IA)")) },
                leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = mode == "barcode", onClick = { mode = "barcode"; reset() },
                label = { Text(t("Code-barres")) },
                leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp)) }
            )
        }
    }

    val current = photo
    val cameraReady = current == null && hasCamera &&
        !(mode == "photo" && (!settings.aiEnabled || !com.goodlife.app.ai.AiAccess.ready(settings)))

    if (showDex) {
        NutridexScreen(onBack = { showDex = false })
        return
    }

    if (cameraReady) {
        // Écran caméra sans défilement : l'aperçu prend la place restante, le déclencheur reste
        // toujours visible au-dessus de la barre de navigation, quelle que soit la taille du téléphone.
        var capture by remember { mutableStateOf<ImageCapture?>(null) }
        // Aperçu : 3:4 en portrait, 4:3 en paysage, toujours le plus grand possible
        val viewfinder: @Composable (Modifier, Float) -> Unit = { modifier, ratio ->
            Box(modifier, contentAlignment = Alignment.Center) {
                Box(
                    Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black)
                ) {
                    InAppCamera(onReady = { capture = it })
                    Box(
                        Modifier.align(Alignment.Center)
                            .then(if (ratio < 1f) Modifier.fillMaxWidth(if (mode == "barcode") 0.85f else 0.75f)
                                  else Modifier.fillMaxHeight(if (mode == "barcode") 0.6f else 0.75f))
                            .aspectRatio(if (mode == "barcode") 2f else 1f)
                            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)), RoundedCornerShape(24.dp))
                    )
                }
            }
        }
        val galleryButton: @Composable () -> Unit = {
            FilledTonalIconButton(
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.size(56.dp)
            ) { Icon(Icons.Filled.PhotoLibrary, t("Galerie")) }
        }
        val shutterButton: @Composable () -> Unit = {
            FilledIconButton(
                onClick = {
                    capture?.let { ic ->
                        takePhoto(context, ic, onPhoto = { analyze(it) }, onFail = { error = it })
                    }
                },
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors()
            ) { Icon(Icons.Filled.PhotoCamera, t("Prendre la photo"), Modifier.size(34.dp)) }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                // Paysage : textes et modes à gauche, aperçu au centre sur toute la hauteur, déclencheur à droite
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.widthIn(max = 260.dp).weight(0.8f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        header()
                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    viewfinder(Modifier.weight(2f).fillMaxHeight(), 4f / 3f)
                    Column(
                        Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.size(56.dp))
                        shutterButton()
                        galleryButton()
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().widthIn(max = 640.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    header()
                    viewfinder(Modifier.weight(1f).fillMaxWidth(), 3f / 4f)
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        galleryButton()
                        shutterButton()
                        Spacer(Modifier.size(56.dp))
                    }
                }
            }
        }
        if (askConsent) AiConsentDialog(onDismiss = { askConsent = false })
        return
    }

    ScreenColumn {
        header()
        when {
            mode == "photo" && !settings.aiEnabled -> SectionCard(title = t("IA désactivée")) {
                Text(
                    if (com.goodlife.app.ai.AiAccess.viaRelay) t("L'analyse des photos envoie la photo à Google Gemini (via le service IA de Lifoody), c'est pourquoi elle demande ton accord. Le mode Code-barres fonctionne sans IA.") else t("L'analyse des photos envoie la photo à Google Gemini avec ta propre clé, c'est pourquoi elle demande ton accord. Le mode Code-barres fonctionne sans IA ni clé."),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { askConsent = true }) { Text(t("Activer l'IA")) }
            }
            mode == "photo" && com.goodlife.app.ai.AiAccess.viaRelay && !com.goodlife.app.ai.AiAccess.ready(settings) -> SectionCard(title = t("Bientôt disponible")) {
                Text(t("L'analyse des photos arrive très vite dans cette version. En attendant, le mode Code-barres fonctionne."), style = MaterialTheme.typography.bodyMedium)
            }
            mode == "photo" && !com.goodlife.app.ai.AiAccess.ready(settings) -> SectionCard(title = t("Ajoute ta clé Gemini")) {
                Text(
                    t("Chaque utilisateur utilise sa propre clé, créée chez Google. Elle reste chiffrée sur ce téléphone et n'est envoyée qu'à Google."),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text(t("Créer ma clé chez Google")) }
                OutlinedTextField(
                    value = keyDraft, onValueChange = { keyDraft = it.trim() },
                    label = { Text(t("Clé API Gemini")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KEY_KEYBOARD,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = keyDraft.length >= 20,
                    onClick = { saveApiKey(context, keyDraft); keyDraft = "" }
                ) { Text(t("Enregistrer la clé")) }
            }
            current == null && !hasCamera -> SectionCard(title = t("Accès à la caméra")) {
                Text(t("Lifoody a besoin de la caméra pour photographier directement dans l'app."))
                Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text(t("Autoriser la caméra")) }
            }
            current != null -> {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = t("Photo"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp))
                )
                when {
                    loading != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(loading!!)
                    }
                    error != null -> {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = { analyze(current) }) { Text(t("Réessayer")) }
                            OutlinedButton(onClick = { reset() }) { Text(t("Nouvelle photo")) }
                        }
                    }
                    result != null -> {
                        DexUnlockedBanner(newDex, onOpen = { showDex = true })
                        val res = result!!
                        ResultCard(
                        r = res,
                        onAdd = { meal ->
                            // Boisson seule sans calories (eau, thé, café nature) : seulement l'eau du jour
                            if (meal != null) Repo.addMeal(meal)
                            if (res.waterMl > 0) Repo.addWater(res.waterMl)
                            if (res.fridgeUsed.isNotEmpty() && settings.fridgeAutoRemove) fridgeAsk = res.fridgeUsed
                            else { reset(); onDone() }
                        },
                        onRetake = { reset() },
                        onAsk = { chat = true }
                    )
                    }
                    product != null -> ProductCard(
                        p = product!!,
                        onAdd = { meal -> Repo.addMeal(meal); reset(); onDone() },
                        onRetake = { reset() }
                    )
                }
            }
        }
        if (mode == "barcode") {
            Text(
                t("Le code-barres est lu sur ton téléphone (ML Kit). Seul son numéro est envoyé à Open Food Facts, base de données alimentaire ouverte, pour trouver les valeurs nutritionnelles."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (askConsent) AiConsentDialog(onDismiss = { askConsent = false })
    fridgeAsk?.let { used ->
        FridgeRemoveDialog(used,
            onRemove = { chosen -> com.goodlife.app.data.Fridge.removeWithUndo(chosen); fridgeAsk = null; reset(); onDone() },
            onNotHome = { fridgeAsk = null; reset(); onDone() })
    }
    val r = result
    if (chat && r != null) {
        AiChatDialog(
            title = r.dish,
            context = "L'utilisateur a photographié un repas (photo jointe). Analyse de l'IA : ${r.dish}, environ ${r.kcal} kcal, protéines ${r.proteinG.toInt()} g, glucides ${r.carbsG.toInt()} g, lipides ${r.fatG.toInt()} g. Aliments : ${r.items.joinToString("; ")}. Conseil déjà donné : ${r.advice}",
            image = lastJpeg,
            suggestions = listOf(t("C'est équilibré ?"), t("Comment le rendre plus léger ?"), t("Riche en protéines ?"), t("Quoi manger avec ?")),
            onDismiss = { chat = false }
        )
    }
}

@Composable
private fun ProductCard(p: FoodProduct, onAdd: (Meal) -> Unit, onRetake: () -> Unit) {
    var grams by remember(p) { mutableStateOf((p.servingGrams ?: 100.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }) }
    val g = grams.toNumber() ?: 0.0
    val kcal = (p.kcal100 * g / 100).toInt()
    SectionCard {
        Text(p.name, style = MaterialTheme.typography.titleLarge)
        if (p.brand.isNotBlank()) Text(p.brand, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            t("Pour 100 g : %1\$s kcal · P %2\$s g · G %3\$s g · L %4\$s g", p.kcal100.toInt(), "%.1f".format(p.protein100), "%.1f".format(p.carbs100), "%.1f".format(p.fat100)),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = grams, onValueChange = { grams = it },
            label = { Text(t("Quantité mangée (g ou ml)")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Text("$kcal kcal", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                enabled = g > 0 && g <= 5000,
                onClick = {
                    onAdd(
                        Meal(
                            name = p.name, kcal = kcal,
                            proteinG = p.protein100 * g / 100, carbsG = p.carbs100 * g / 100, fatG = p.fat100 * g / 100,
                            details = t("Code-barres %1\$s · %2\$s g · Open Food Facts", p.barcode, g.toInt()),
                            source = "code-barres"
                        )
                    )
                }
            ) { Text(t("Ajouter au journal")) }
            OutlinedButton(onClick = onRetake) { Text(t("Autre produit")) }
        }
        Text(
            t("Données : Open Food Facts (licence ODbL), à vérifier sur l'emballage."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultCard(r: FoodAnalysis, onAdd: (Meal?) -> Unit, onRetake: () -> Unit, onAsk: () -> Unit) {
    val waterOnly = r.drinkOnly && r.kcal == 0 && r.waterMl > 0
    var name by remember(r) { mutableStateOf(r.dish) }
    var kcal by remember(r) { mutableStateOf(r.kcal.toString()) }
    // On corrige le POIDS de chaque aliment ; ses calories suivent (même densité que l'estimation de l'IA)
    val grams = remember(r) { androidx.compose.runtime.mutableStateListOf(*r.foods.map { it.grams.toString() }.toTypedArray()) }
    val editable = r.foods.isNotEmpty() && r.foods.all { it.grams > 0 }
    fun partKcal(i: Int): Int {
        val f = r.foods[i]
        val g = grams.getOrNull(i)?.toNumber() ?: return f.kcal
        return if (f.grams > 0) (f.kcal * g / f.grams).roundToInt().coerceAtLeast(0) else f.kcal
    }
    // Total : l'estimation globale de l'IA, ajustée de la différence due aux poids modifiés
    val totalKcal = if (editable) (r.kcal + r.foods.indices.sumOf { partKcal(it) - r.foods[it].kcal }).coerceAtLeast(0) else (kcal.toNumber()?.toInt() ?: r.kcal)

    if (r.allergens.isNotEmpty()) {
        SectionCard(
            title = t("Attention allergies"),
            icon = Icons.Filled.Warning,
            container = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(r.allergens.joinToString(", "), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }

    SectionCard {
        Text("$totalKcal kcal", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
        Text(
            t("Protéines %1\$s g · Glucides %2\$s g · Lipides %3\$s g", r.proteinG.toInt(), r.carbsG.toInt(), r.fatG.toInt()),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            t("Confiance de l'IA : %1\$s %%", (r.confidence * 100).toInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (editable) {
            Text(t("Corrige le poids si besoin : les calories se recalculent."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            r.foods.forEachIndexed { i, f ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = grams[i], onValueChange = { v -> grams[i] = v.filter { c -> c.isDigit() }.take(4) },
                        suffix = { Text("g") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp)
                    )
                    Text(t("%1\$s kcal", partKcal(i)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                }
            }
        } else r.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        if (r.waterMl > 0) Text(
            t("💧 %1\$s ml d'eau seront ajoutés à ton suivi de l'eau.", r.waterMl),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
        )
        if (r.advice.isNotBlank()) {
            Text(r.advice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        AiContentFooter(
            "Analyse photo : ${r.dish}, ${r.kcal} kcal\n${r.items.joinToString("\n")}\n${r.advice}"
        )
        FilledTonalButton(onClick = onAsk) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("Poser une question sur ce repas"))
        }
        if (!waterOnly) OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(t("Nom du repas")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        // Sans détail par aliment (rare), on garde la correction directe des calories
        if (!waterOnly && !editable) OutlinedTextField(
            value = kcal, onValueChange = { kcal = it },
            label = { Text(t("Calories (modifiable)")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (waterOnly) Button(onClick = { onAdd(null) }) { Text(t("Ajouter %1\$s ml d'eau", r.waterMl)) }
            else Button(
                enabled = totalKcal > 0,
                onClick = {
                    val finalKcal = totalKcal
                    // Si l'utilisateur corrige les kcal, on ajuste les macros au prorata
                    val ratio = if (r.kcal > 0) finalKcal.toDouble() / r.kcal else 1.0
                    onAdd(
                        Meal(
                            name = name.ifBlank { r.dish },
                            kcal = finalKcal,
                            proteinG = r.proteinG * ratio,
                            carbsG = r.carbsG * ratio,
                            fatG = r.fatG * ratio,
                            details = if (editable) r.foods.indices.joinToString("\n") { i -> "${r.foods[i].name} · ${grams[i].toNumber()?.toInt() ?: r.foods[i].grams} g · ${partKcal(i)} kcal" }
                                      else r.items.joinToString("\n"),
                            source = "photo"
                        )
                    )
                }
            ) { Text(t("Ajouter au journal")) }
            OutlinedButton(onClick = onRetake) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Reprendre"))
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
                    onFail(t("Photo illisible, réessaie."))
                    return
                }
                val bmp = if (rotation != 0) {
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                        Matrix().apply { postRotate(rotation.toFloat()) }, true)
                } else raw
                onPhoto(bmp.scaledTo(1600))
            }

            override fun onError(exception: ImageCaptureException) {
                onFail(t("Erreur caméra : %1\$s", exception.message))
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

/** Petite vignette carrée (256 px, JPEG) pour le Nutridex. */
private fun Bitmap.dexThumbnail(): ByteArray {
    val side = minOf(width, height)
    val square = Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    val small = Bitmap.createScaledBitmap(square, 256, 256, true)
    return ByteArrayOutputStream().use { out ->
        small.compress(Bitmap.CompressFormat.JPEG, 82, out)
        out.toByteArray()
    }
}

/**
 * Option « retirer du frigo » : ce que l'IA pense avoir été utilisé pour ce repas. On coche ce qui est juste ;
 * « Pas mangé chez moi » ne retire rien. L'accueil propose ensuite d'annuler.
 */
@Composable
private fun FridgeRemoveDialog(used: List<Pair<String, Double>>, onRemove: (List<Pair<String, Double>>) -> Unit, onNotHome: () -> Unit) {
    val fridge = com.goodlife.app.data.Fridge.items.collectAsState().value
    var keep by remember { mutableStateOf(used.indices.toSet()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onNotHome,
        title = { Text(t("Retirer du frigo ?")) },
        text = {
            Column {
                Text(t("Ce repas semble avoir utilisé :"), style = MaterialTheme.typography.bodyMedium)
                used.forEachIndexed { i, (n, q) ->
                    val unit = fridge.firstOrNull { it.name.equals(n, ignoreCase = true) }?.unit ?: com.goodlife.app.data.Fridge.PIECE
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(i in keep, { c -> keep = if (c) keep + i else keep - i })
                        Text(n + " · " + com.goodlife.app.data.FridgeItem(n, q, unit, "").qtyText().let { if (unit == com.goodlife.app.data.Fridge.PIECE) "× $it" else it },
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = keep.isNotEmpty(), onClick = { onRemove(used.filterIndexed { i, _ -> i in keep }) }) { Text(t("Retirer")) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onNotHome) { Text(t("Pas mangé chez moi")) } }
    )
}
