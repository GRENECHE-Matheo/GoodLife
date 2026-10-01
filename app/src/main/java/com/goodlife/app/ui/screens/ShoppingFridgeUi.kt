package com.goodlife.app.ui.screens

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.goodlife.app.ai.FridgeIdea
import com.goodlife.app.ai.FridgeResult
import com.goodlife.app.ai.Gemini
import com.goodlife.app.ai.ShopItem
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.PlannedMeal
import com.goodlife.app.data.Recipe
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.SubScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

// ====================================================================== liste de courses

/** Liste de courses gardée chiffrée sur le téléphone (et dans la sauvegarde). */
object Shopping {
    private const val KEY = "shopping"

    fun load(): List<ShopItem> = runCatching {
        val a = JSONObject(Repo.getExtra(KEY) ?: "{}").optJSONArray("items") ?: JSONArray()
        (0 until a.length()).map { i -> a.getJSONObject(i).let { ShopItem(it.optString("n"), it.optString("q"), it.optString("r", "Autres"), it.optBoolean("c")) } }
    }.getOrDefault(emptyList())

    fun save(items: List<ShopItem>) {
        Repo.putExtra(KEY, JSONObject().put("items", JSONArray().apply {
            items.take(150).forEach { put(JSONObject().put("n", it.name).put("q", it.qty).put("r", it.aisle).put("c", it.checked)) }
        }).toString())
    }

    /** Ajoute des articles (sans doublon de nom). */
    fun add(more: List<ShopItem>) {
        val cur = load()
        save(cur + more.filter { m -> cur.none { it.name.equals(m.name, ignoreCase = true) } })
    }

    /** Sans IA : ingrédients des recettes enregistrées, et le nom des repas qui n'en ont pas. */
    fun fromRecipes(meals: List<PlannedMeal>): List<ShopItem> =
        meals.flatMap { m -> m.recipe?.ingredients?.map { ShopItem(it, "", "D'après tes recettes") } ?: listOf(ShopItem("Pour « ${m.name} »", "", "Repas sans recette")) }
            .distinctBy { it.name.lowercase() }

    fun text(items: List<ShopItem>): String = "Liste de courses GoodLife 🛒\n" + items.groupBy { it.aisle }.entries.joinToString("\n") { (aisle, list) ->
        "\n$aisle :\n" + list.joinToString("\n") { "${if (it.checked) "☑" else "☐"} ${it.name}${if (it.qty.isNotBlank()) " — ${it.qty}" else ""}" }
    }
}

@Composable
fun ShoppingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plan by Repo.plan.collectAsState()
    val settings by Repo.settings.collectAsState()
    var items by remember { mutableStateOf(Shopping.load()) }
    var people by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var extra by remember { mutableStateOf("") }
    val upcoming = plan.filter { it.date >= localDay(0) && it.date <= localDay(6) && !it.done }
    val cost = upcoming.sumOf { it.costEur }
    fun update(l: List<ShopItem>) { items = l; Shopping.save(l) }
    val aiReady = Repo.aiAllowed() && settings.apiKey.isNotBlank()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader("Liste de courses", onBack)
            SectionCard(title = "Pour les 7 prochains jours", icon = Icons.Filled.ShoppingCart) {
                Text(
                    if (upcoming.isEmpty()) "Aucun repas prévu : ajoute des repas dans le planning, ou écris tes articles ci-dessous."
                    else "${upcoming.size} repas prévus" + if (cost > 0) " · budget estimé lors du planning : ${"%.2f".format(cost).replace('.', ',')} €" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (upcoming.isNotEmpty()) {
                    Text("Pour combien de personnes ?", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { n -> FilterChip(people == n, { people = n }, label = { Text("$n") }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (aiReady) Button(enabled = !loading, onClick = {
                            loading = true; error = null
                            scope.launch {
                                try { update(Gemini(settings.apiKey, settings.model).shoppingList(upcoming, people)) }
                                catch (e: Exception) { error = e.message } finally { loading = false }
                            }
                        }) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Préparer avec l'IA")
                        }
                        OutlinedButton(onClick = { update(Shopping.fromRecipes(upcoming)) }) { Text(if (aiReady) "Sans IA" else "Préparer") }
                    }
                    Text(
                        if (aiReady) "L'IA additionne les ingrédients des repas prévus et les range par rayon (quantités estimées)."
                        else "Sans IA : les ingrédients des recettes enregistrées dans ton planning.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(extra, { extra = it.take(60) }, label = { Text("Ajouter un article") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(enabled = extra.isNotBlank(), onClick = { update(items + ShopItem(extra.trim(), "", "Ajouté par toi")); extra = "" }) { Text("Ajouter") }
            }
            if (items.isNotEmpty()) {
                items.groupBy { it.aisle }.forEach { (aisle, list) ->
                    SectionCard(title = aisle) {
                        list.forEach { it2 ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(it2.checked, { c -> update(items.map { x -> if (x == it2) x.copy(checked = c) else x }) })
                                Column(Modifier.weight(1f)) {
                                    Text(it2.name, style = MaterialTheme.typography.bodyLarge,
                                        color = if (it2.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                                    if (it2.qty.isNotBlank()) Text(it2.qty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { update(items - it2) }) { Icon(Icons.Filled.Delete, "Retirer") }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, Shopping.text(items)), "Partager la liste"))
                        }
                    }) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Partager") }
                    TextButton(onClick = { update(items.filter { !it.checked }) }) { Text("Retirer les cochés") }
                    TextButton(onClick = { update(emptyList()) }) { Text("Tout effacer") }
                }
            }
        }
    }
}

// ====================================================================== « J'ai ça dans mon frigo »

/** Photo réduite (1280 px max, JPEG) pour l'envoyer à l'IA. */
private fun readPhoto(context: android.content.Context, uri: Uri): ByteArray? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 1280) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val scale = 1280f / maxOf(bmp.width, bmp.height)
    val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}.getOrNull()

/** Fournisseur de fichiers pour l'appareil photo (photo du frigo, gardée seulement le temps de l'analyse). */
class CameraFiles : FileProvider()

@Composable
fun FridgeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FridgeContent(onDismiss) }
    }
}

@Composable
private fun FridgeContent(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val meals by Repo.meals.collectAsState()
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var written by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<FridgeResult?>(null) }
    var planFor by remember { mutableStateOf<FridgeIdea?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val shotUri = remember {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        FileProvider.getUriForFile(context, context.packageName + ".camera", File(dir, "frigo.jpg"))
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            photo = withContext(Dispatchers.IO) { readPhoto(context, shotUri) }
            runCatching { File(File(context.cacheDir, "camera"), "frigo.jpg").delete() }   // la photo n'est pas gardée
        }
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) takePhoto.launch(shotUri) else error = "Sans l'accès à la caméra, choisis plutôt une photo dans ta galerie."
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = withContext(Dispatchers.IO) { readPhoto(context, uri) } }
    }
    val p = profile ?: return
    val remaining = (p.targetKcal - Repo.mealsOfDay(meals).sumOf { it.kcal }).coerceAtLeast(0)
    val aiReady = Repo.aiAllowed() && settings.apiKey.isNotBlank()

    ScreenColumn {
        SubScreenHeader("J'ai ça dans mon frigo", onClose)
        if (!aiReady) {
            Text("Cette fonction utilise l'IA : active-la et ajoute ta clé Gemini dans Profil › Paramètres (18 ans et plus).",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ScreenColumn
        }
        SectionCard {
            Text("Prends en photo ton frigo ou tes placards, et/ou écris ce que tu as. Le chef te propose des recettes anti-gaspi.",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) takePhoto.launch(shotUri)
                    else askCamera.launch(Manifest.permission.CAMERA)
                }) { Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Photo") }
                OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Galerie")
                }
            }
            photo?.let { bytes ->
                val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                if (bmp != null) Image(bmp, "Photo du frigo", Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                TextButton(onClick = { photo = null }) { Text("Retirer la photo") }
            }
            OutlinedTextField(written, { written = it.take(400) }, label = { Text("Ce que tu as (facultatif)") },
                placeholder = { Text("Ex : 3 œufs, épinards, riz, une tomate…") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(enabled = !loading && (photo != null || written.isNotBlank()), onClick = {
                loading = true; error = null; result = null
                scope.launch {
                    try { result = Gemini(settings.apiKey, settings.model).fridge(photo, written, p, remaining) }
                    catch (e: Exception) { error = e.message } finally { loading = false }
                }
            }) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Que cuisiner ?")
            }
            Text("La photo et ta liste sont envoyées à Google Gemini avec ta clé, avec tes allergies, habitudes et calories restantes. La photo n'est pas gardée.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        result?.let { r ->
            if (r.seen.isNotEmpty()) SectionCard(title = "Le chef a repéré") { Text(r.seen.joinToString(", "), style = MaterialTheme.typography.bodyMedium) }
            r.ideas.forEach { idea ->
                SectionCard(title = idea.name) {
                    Text("${idea.moment.replaceFirstChar { it.uppercase() }} · ${idea.kcal} kcal · ${idea.minutes} min",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (idea.uses.isNotEmpty()) Text("Avec : ${idea.uses.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    if (idea.missing.isNotEmpty()) Text("Il te manque : ${idea.missing.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    idea.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
                    AiContentFooter("Recette du frigo : ${idea.name}\n${idea.steps.joinToString("\n")}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { planFor = idea }) { Text("Planifier") }
                        if (idea.missing.isNotEmpty()) TextButton(onClick = {
                            Shopping.add(idea.missing.map { ShopItem(it, "", "Pour « ${idea.name} »") })
                            info = "Ajouté à ta liste de courses : ${idea.missing.joinToString(", ")}"
                        }) { Text("Ajouter les manquants à la liste") }
                    }
                }
            }
            if (r.tip.isNotBlank()) Text(r.tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (info != null) Text(info!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    planFor?.let { idea ->
        AddToPlanDialog(
            initialName = idea.name, initialKcal = idea.kcal, initialSlot = MealSlot.guess(idea.moment),
            description = "Avec : " + idea.uses.joinToString(", "),
            recipe = Recipe(1, idea.minutes, idea.kcal, idea.uses + idea.missing, idea.steps, ""),
            editableName = false, onDismiss = { planFor = null }
        )
    }
}
