@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.FlowRow
import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp
import androidx.compose.foundation.clickable
import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.goodlife.app.ai.FridgeIdea
import com.goodlife.app.ai.FridgeResult
import com.goodlife.app.ai.Gemini
import com.goodlife.app.ai.ShopItem
import com.goodlife.app.data.Fridge
import com.goodlife.app.data.FridgeItem
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Recipe
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.SubScreenHeader
import com.goodlife.app.ui.toNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Pas des boutons − et + selon l'unité. */
private fun step(unit: String): Double = when (unit) {
    "g" -> 50.0
    "ml" -> 100.0
    "kg", "L" -> 0.25
    else -> 1.0
}

/**
 * « Mon frigo » : l'inventaire de ce qu'on a à la maison (par rayon, avec + et −), rempli à la main, par la photo
 * d'un ticket de caisse ou par le chef ; puis « Que cuisiner ? » avec ce qu'il contient.
 */
@Composable
internal fun FridgeContent(onClose: () -> Unit, embedded: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val meals by Repo.meals.collectAsState()
    remember { Fridge.load() }
    val items by Fridge.items.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<FridgeItem?>(null) }
    var receiptLoading by remember { mutableStateOf(false) }
    var receiptFound by remember { mutableStateOf<List<FridgeItem>?>(null) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var photoFor by remember { mutableStateOf("ideas") }   // « ideas » (que cuisiner) ou « receipt » (ticket)
    var written by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<FridgeResult?>(null) }
    var planFor by remember { mutableStateOf<FridgeIdea?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val p = profile ?: return
    val remaining = (p.targetKcal - Repo.mealsOfDay(meals).sumOf { it.kcal }).coerceAtLeast(0)
    val aiReady = Repo.aiAllowed() && com.goodlife.app.ai.AiAccess.ready(settings)

    fun readReceipt(bytes: ByteArray) {
        receiptLoading = true; error = null
        scope.launch {
            try { receiptFound = Gemini(settings.apiKey, settings.model).groceries(bytes) }
            catch (e: Exception) { error = e.message } finally { receiptLoading = false }
        }
    }

    val shotUri = remember {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        FileProvider.getUriForFile(context, context.packageName + ".camera", File(dir, "frigo.jpg"))
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            val bytes = withContext(Dispatchers.IO) { readPhoto(context, shotUri) }
            runCatching { File(File(context.cacheDir, "camera"), "frigo.jpg").delete() }   // la photo n'est pas gardée
            if (bytes != null) { if (photoFor == "receipt") readReceipt(bytes) else photo = bytes }
        }
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) takePhoto.launch(shotUri) else error = t("Sans l'accès à la caméra, choisis plutôt une photo dans ta galerie.")
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { readPhoto(context, uri) }
            if (bytes != null) { if (photoFor == "receipt") readReceipt(bytes) else photo = bytes }
        }
    }
    fun camera(forWhat: String) {
        photoFor = forWhat
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) takePhoto.launch(shotUri)
        else askCamera.launch(Manifest.permission.CAMERA)
    }
    fun gallery(forWhat: String) { photoFor = forWhat; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ScreenColumn {
        if (!embedded) SubScreenHeader(t("Mon frigo"), onClose)

        // ---- Inventaire ----
        SectionCard(title = if (items.isEmpty()) t("Ce que j'ai à la maison") else t("Ce que j'ai à la maison · %1\$s", items.size), icon = Icons.Filled.Kitchen) {
            if (items.isEmpty()) Text(
                t("Ton frigo est vide pour l'instant. Ajoute ce que tu as, prends en photo ton ticket ou ton frigo, ou dis-le simplement au chef."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Ajouter")) }
                // Une seule photo : ticket de caisse, frigo, placards ou conserves
                if (aiReady) FilledTonalButton(enabled = !receiptLoading, onClick = { camera("receipt") }) {
                    if (receiptLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(t("Photo"))
                }
                if (aiReady) OutlinedButton(enabled = !receiptLoading, onClick = { gallery("receipt") }) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Galerie"))
                }
            }
            if (aiReady) Text(
                t("Photo de ton ticket de caisse, de ton frigo, de tes placards ou de tes conserves : l'IA liste les aliments, tu valides avant l'ajout."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Fridge.AISLES.forEach { aisle ->
            val list = items.filter { it.aisle == aisle }
            if (list.isNotEmpty()) SectionCard(title = Fridge.aisleLabel(aisle)) {
                list.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider()
                    FridgeRow(item, onEdit = { edit = item })
                }
            }
        }

        // ---- Que cuisiner ? ----
        if (!aiReady) {
            Text(if (com.goodlife.app.ai.AiAccess.viaRelay) t("Les idées de recettes et la lecture du ticket utilisent l'IA : active-la dans Moi › Paramètres (18 ans et plus).") else t("Les idées de recettes et la lecture du ticket utilisent l'IA : active-la et ajoute ta clé Gemini dans Moi › Paramètres (18 ans et plus)."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ScreenColumn
        }
        SectionCard(title = t("Que cuisiner ?"), icon = Icons.Filled.AutoAwesome) {
            Text(
                if (items.isNotEmpty()) tp(items.size, "Le chef part de ton frigo (%1\$s article). Tu peux ajouter une photo ou préciser.", "Le chef part de ton frigo (%1\$s articles). Tu peux ajouter une photo ou préciser.")
                else t("Prends en photo ton frigo ou tes placards, et/ou écris ce que tu as. Le chef te propose des recettes anti-gaspi."),
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = { camera("ideas") }) { Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Photo")) }
                OutlinedButton(onClick = { gallery("ideas") }) { Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Galerie")) }
            }
            photo?.let { bytes ->
                val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                if (bmp != null) Image(bmp, t("Photo du frigo"), Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                TextButton(onClick = { photo = null }) { Text(t("Retirer la photo")) }
            }
            OutlinedTextField(written, { written = it.take(400) }, label = { Text(t("Précisions (facultatif)")) },
                placeholder = { Text(t("Ex : 3 œufs, épinards, riz, une tomate…")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(enabled = !loading && (photo != null || written.isNotBlank() || items.isNotEmpty()), onClick = {
                loading = true; error = null; result = null
                val all = listOf(Fridge.promptList(), written.trim()).filter { it.isNotBlank() }.joinToString(" ; ")
                scope.launch {
                    try { result = Gemini(settings.apiKey, settings.model).fridge(photo, all, p, remaining) }
                    catch (e: Exception) { error = e.message } finally { loading = false }
                }
            }) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(t("Que cuisiner ?"))
            }
            Text(if (com.goodlife.app.ai.AiAccess.viaRelay) t("Ton frigo, la photo et tes précisions sont envoyés à Google Gemini via le serveur de Lifoody (qui n'enregistre rien), avec tes allergies, habitudes et calories restantes. La photo n'est pas gardée.") else t("Ton frigo, la photo et tes précisions sont envoyés à Google Gemini avec ta clé, avec tes allergies, habitudes et calories restantes. La photo n'est pas gardée."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        result?.let { r ->
            if (r.seen.isNotEmpty()) SectionCard(title = t("Le chef a repéré")) { Text(r.seen.joinToString(", "), style = MaterialTheme.typography.bodyMedium) }
            r.ideas.forEach { idea ->
                SectionCard(title = idea.name) {
                    Text(t("%1\$s · %2\$s kcal · %3\$s min", idea.moment.replaceFirstChar { it.uppercase() }, idea.kcal, idea.minutes),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (idea.uses.isNotEmpty()) Text(t("Avec : %1\$s", idea.uses.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                    if (idea.missing.isNotEmpty()) Text(t("Il te manque : %1\$s", idea.missing.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    idea.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
                    AiContentFooter("Recette du frigo : ${idea.name}\n${idea.steps.joinToString("\n")}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { planFor = idea }) { Text(t("Planifier")) }
                        if (idea.missing.isNotEmpty()) TextButton(onClick = {
                            Shopping.add(idea.missing.map { ShopItem(it, "", t("Pour « %1\$s »", idea.name)) })
                            info = t("Ajouté à ta liste de courses : %1\$s", idea.missing.joinToString(", "))
                        }) { Text(t("Ajouter les manquants à la liste")) }
                    }
                }
            }
            if (r.tip.isNotBlank()) Text(r.tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (info != null) Text(info!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (adding) FridgeItemDialog(null, onDismiss = { adding = false }) { Fridge.add(listOf(it)); adding = false }
    edit?.let { item ->
        FridgeItemDialog(item, onDismiss = { edit = null }, onDelete = { Fridge.delete(item); edit = null }) { changed ->
            Fridge.delete(item); Fridge.add(listOf(changed.copy(addedAt = item.addedAt))); edit = null
        }
    }
    receiptFound?.let { found -> ReceiptDialog(found, onDismiss = { receiptFound = null }) { chosen -> Fridge.add(chosen); receiptFound = null } }
    planFor?.let { idea ->
        AddToPlanDialog(
            initialName = idea.name, initialKcal = idea.kcal, initialSlot = MealSlot.guess(idea.moment),
            description = t("Avec : ") + idea.uses.joinToString(", "),
            recipe = Recipe(1, idea.minutes, idea.kcal, idea.uses + idea.missing, idea.steps, ""),
            editableName = false, onDismiss = { planFor = null }
        )
    }
}

/** Une ligne de l'inventaire : nom, quantité, et − / + pour ajuster en un geste. */
@Composable
private fun FridgeRow(item: FridgeItem, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // Toucher le nom : modifier (quantité, unité, rayon) ou retirer
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onEdit).padding(vertical = 6.dp, horizontal = 4.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(item.qtyText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { Fridge.setQty(item, item.qty - step(item.unit)) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Remove, t("Moins"), Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = { Fridge.setQty(item, item.qty + step(item.unit)) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Add, t("Plus"), Modifier.size(18.dp))
        }
    }
}

/** Ajouter (ou modifier) un article : nom, quantité, unité et rayon. */
@Composable
private fun FridgeItemDialog(item: FridgeItem?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (FridgeItem) -> Unit) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var qty by remember { mutableStateOf(item?.qty?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "1") }
    var unit by remember { mutableStateOf(item?.unit ?: Fridge.PIECE) }
    var aisle by remember { mutableStateOf(item?.aisle ?: "Produits frais") }
    val q = qty.toNumber()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) t("Ajouter au frigo") else t("Modifier")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text(t("Aliment")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(qty, { qty = it.take(8) }, label = { Text(t("Quantité")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fridge.UNITS.forEach { u -> FilterChip(unit == u, { unit = u }, label = { Text(Fridge.unitLabel(u)) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fridge.AISLES.forEach { a -> FilterChip(aisle == a, { aisle = a }, label = { Text(Fridge.aisleLabel(a)) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && q != null && q > 0.0, onClick = { onSave(FridgeItem(name.trim(), q!!, unit, aisle)) }) {
                Text(if (item == null) t("Ajouter") else t("Enregistrer"))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, t("Retirer du frigo")) }
                TextButton(onClick = onDismiss) { Text(t("Annuler")) }
            }
        }
    )
}

/** Résultat du ticket de caisse : tout est coché, on décoche ce qu'on ne veut pas ajouter. */
@Composable
private fun ReceiptDialog(found: List<FridgeItem>, onDismiss: () -> Unit, onAdd: (List<FridgeItem>) -> Unit) {
    var keep by remember { mutableStateOf(found.indices.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Ajouter au frigo ?")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                found.forEachIndexed { i, f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i in keep, { c -> keep = if (c) keep + i else keep - i })
                        Column {
                            Text(f.name, style = MaterialTheme.typography.bodyMedium)
                            Text(f.qtyText() + " · " + Fridge.aisleLabel(f.aisle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(t("Généré par l'IA à partir de ta photo : vérifie avant d'ajouter."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = keep.isNotEmpty(), onClick = { onAdd(found.filterIndexed { i, _ -> i in keep }) }) { Text(t("Ajouter (%1\$s)", keep.size)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } }
    )
}

/**
 * Article de la liste de courses → article du frigo : « 500 g » → 500 g, « 2 » → 2 pièces, « 1,5 L » → 1,5 L ;
 * sinon une pièce.
 */
internal fun ShopItem.toFridgeItem(): FridgeItem {
    val m = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*([A-Za-zéèêûôâîç]*)""").find(qty)
    val raw = m?.groupValues?.get(2).orEmpty()
    val n = (m?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 1.0) * (if (raw.equals("cl", ignoreCase = true)) 10.0 else 1.0)
    val unit = Fridge.cleanUnit(raw)
    return FridgeItem(name, n.coerceAtLeast(0.01), unit, Fridge.cleanAisle(aisle))
}
