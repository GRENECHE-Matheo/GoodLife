package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import com.goodlife.app.ui.AfterEnter
import com.goodlife.app.ui.Motion
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.ScreenTitle

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/** Espace « Forme » : programme sportif, sorties (carte, GPS, clubs) et sommeil, sans ajouter d'onglet en bas. */
@Composable
fun FormeScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val active = com.goodlife.app.track.Tracker.live.collectAsState().value != null
    androidx.compose.runtime.LaunchedEffect(active) { if (active) tab = 1 }
    val navRequest by com.goodlife.app.social.AppNav.request.collectAsState()
    androidx.compose.runtime.LaunchedEffect(navRequest) {
        if (navRequest == "forme:sommeil") { tab = 2; com.goodlife.app.social.AppNav.request.value = null }
    }
    Column(Modifier.fillMaxSize()) {
        // Sur la carte, on garde toute la place pour elle : le grand titre se replie en douceur (au lieu de disparaître d'un coup)
        // Le titre se replie AVANT que la carte n'apparaisse (elle est créée après l'animation) et la carte est retirée d'un coup quand on la quitte :
        // la carte n'est jamais redimensionnée pendant une animation (c'est ce qui la faisait saccader)
        AnimatedVisibility(
            visible = !active && tab != 1,
            // En revenant de la carte : le titre se déplie en douceur, la barre d'onglets descend avec lui (elle sautait)
            enter = expandVertically(tween(Motion.DURATION, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeIn(tween(Motion.DURATION)),
            exit = shrinkVertically(tween(Motion.DURATION)) + fadeOut(tween(Motion.DURATION / 2))
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    ScreenTitle(t("Forme"), t("Bouger, s'amuser, bien dormir"))
                }
            }
        }
        AnimatedVisibility(visible = !active, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(t("Programme"), t("Carte"), t("Sommeil")).forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { com.goodlife.app.ui.FitText(label) })
                }
            }
        }
        // Glissement latéral entre Programme et Sommeil ; simple fondu vers ou depuis la carte (trop lourde pour glisser)
        AnimatedContent(
            targetState = tab,
            transitionSpec = { if (initialState == 1) Motion.leaveHeavy() else if (targetState == 1) Motion.fade() else Motion.sharedAxisX(forward = targetState > initialState) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "forme"
        ) { current ->
            Box(Modifier.fillMaxSize()) {
                when (current) {
                    0 -> ScreenColumn { ProgramTab() }
                    1 -> AfterEnter { OutingsTab() }
                    else -> SleepScreen(showTitle = false)
                }
            }
        }
    }
}
