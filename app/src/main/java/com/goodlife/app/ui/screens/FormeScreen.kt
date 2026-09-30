package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.ScreenTitle

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/** Espace « Forme » : programme sportif, sorties (carte, GPS, clubs) et sommeil, sans ajouter d'onglet en bas. */
@Composable
fun FormeScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val active = com.goodlife.app.track.Tracker.live.collectAsState().value != null
    androidx.compose.runtime.LaunchedEffect(active) { if (active) tab = 1 }
    Column(Modifier.fillMaxSize()) {
        if (!active) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                ScreenTitle("Forme", "Bouger, s'amuser, bien dormir")
            }
        }
        if (!active) PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Programme", "Carte", "Sommeil").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> ScreenColumn { ProgramTab() }
                1 -> OutingsTab()
                else -> SleepScreen(showTitle = false)
            }
        }
    }
}
