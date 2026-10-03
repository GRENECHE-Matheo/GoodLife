package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext

/**
 * Mentions exigées par les sources de l'altitude (Mapzen Terrain Tiles), reprises telles quelles :
 * https://github.com/tilezen/joerd/blob/master/docs/attribution.md
 */
private val TERRAIN_SOURCES = listOf(
    "Mapzen",
    "ArcticDEM terrain data DEM(s) were created from DigitalGlobe, Inc., imagery and funded under National Science Foundation awards 1043681, 1559691, and 1542736",
    "Australia terrain data © Commonwealth of Australia (Geoscience Australia) 2017",
    "Austria terrain data © offene Daten Österreichs – Digitales Geländemodell (DGM) Österreich",
    "Canada terrain data contains information licensed under the Open Government Licence – Canada",
    "Europe terrain data produced using Copernicus data and information funded by the European Union - EU-DEM layers",
    "Global ETOPO1 terrain data U.S. National Oceanic and Atmospheric Administration",
    "Mexico terrain data source: INEGI, Continental relief, 2016",
    "New Zealand terrain data Copyright 2011 Crown copyright (c) Land Information New Zealand and the New Zealand Government (All rights reserved)",
    "Norway terrain data © Kartverket",
    "United Kingdom terrain data © Environment Agency copyright and/or database right 2015. All rights reserved",
    "United States 3DEP (formerly NED) and global GMTED2010 and SRTM terrain data courtesy of the U.S. Geological Survey"
)

/** Données de la carte : fond de carte, rues, recherche de lieux et altitude, avec leurs liens. */
@Composable
internal fun MapCreditsDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Données de la carte")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Fond de carte : OpenFreeMap © OpenMapTiles. Données © les contributeurs d'OpenStreetMap, disponibles sous licence ODbL."),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { runCatching { uri.openUri("https://www.openstreetmap.org/copyright") } }) { Text(t("Droits d'OpenStreetMap")) }
                TextButton(onClick = { runCatching { uri.openUri("https://openfreemap.org") } }) { Text("OpenFreeMap") }
                TextButton(onClick = { runCatching { uri.openUri("https://openmaptiles.org") } }) { Text("OpenMapTiles") }
                Text(t("Recherche de lieux : Nominatim (OpenStreetMap). Clubs : API Overpass (OpenStreetMap)."), style = MaterialTheme.typography.bodySmall)
                Text(t("Altitude : Mapzen Terrain Tiles (hébergées par Amazon Web Services). Sources :"), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
                TERRAIN_SOURCES.forEach { Text("• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { runCatching { uri.openUri("https://github.com/tilezen/joerd/blob/master/docs/attribution.md") } }) {
                    Text(t("Détail des sources d'altitude"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Fermer")) } }
    )
}

/**
 * Licences des bibliothèques open source utilisées par l'app, générées à chaque compilation (plugin AboutLibraries) :
 * nom, version, auteur, et le texte de la licence (ou son lien pour les conditions de Google).
 */
@Composable
internal fun LicensesScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val libs = remember { runCatching { Libs.Builder().withContext(context).build().libraries.sortedBy { it.name.lowercase() } }.getOrDefault(emptyList()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, t("Fermer")) }
                    Text(t("Licences open source"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Text(
                    t("GoodLife utilise ces bibliothèques ; merci à leurs auteurs. Touche une ligne pour lire sa licence."),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (libs.isEmpty()) Text(t("Liste indisponible."), modifier = Modifier.padding(16.dp))
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(libs, key = { it.uniqueId }) { lib -> LibraryRow(lib); HorizontalDivider() }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(lib: Library) {
    val uri = LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(lib.name, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(lib.artifactVersion, lib.organization?.name ?: lib.developers.firstOrNull()?.name, lib.licenses.joinToString { it.name }.ifBlank { null })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (open) lib.licenses.forEach { l ->
            val text = l.licenseContent
            if (!text.isNullOrBlank()) Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            l.url?.takeIf { it.startsWith("https://") }?.let { u ->
                TextButton(onClick = { runCatching { uri.openUri(u) } }) { Text(t("Lire « %1\$s »", l.name)) }
            }
        }
    }
}
