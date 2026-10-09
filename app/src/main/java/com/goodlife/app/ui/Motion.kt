package com.goodlife.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Transitions inspirées de Material Motion (Google) :
 * - « fade through » entre les onglets,
 * - « shared axis X » pour entrer dans un sous-écran et en revenir.
 */
object Motion {
    const val DURATION = 300

    fun fadeThrough(): ContentTransform =
        (fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
            scaleIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing), initialScale = 0.96f))
            .togetherWith(fadeOut(tween(90, easing = FastOutSlowInEasing)))

    /** Fondu simple, pour les écrans lourds (carte) : rien ne glisse ni ne change de taille pendant l'animation. */
    fun fade(): ContentTransform =
        fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing)).togetherWith(fadeOut(tween(90, easing = FastOutSlowInEasing)))

    fun sharedAxisX(forward: Boolean): ContentTransform {
        val sign = if (forward) 1 else -1
        return (slideInHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { sign * it / 6 } +
            fadeIn(tween(DURATION / 2, delayMillis = DURATION / 3)))
            .togetherWith(
                slideOutHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { -sign * it / 6 } +
                    fadeOut(tween(DURATION / 3))
            )
    }
}

/** Bascule animée entre deux états (ex. écran principal ↔ sous-écran). [depth] donne le sens de l'animation. */
@Composable
fun <T> SlideSwitch(target: T, depth: (T) -> Int, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = { Motion.sharedAxisX(forward = depth(targetState) >= depth(initialState)) },
        modifier = Modifier.fillMaxSize(),
        label = "slideSwitch"
    ) { state -> content(state) }
}

/** Version simple pour un booléen (false = écran principal, true = sous-écran). */
@Composable
fun SlideSwitch(open: Boolean, content: @Composable (Boolean) -> Unit) =
    SlideSwitch(open, depth = { if (it) 1 else 0 }, content = content)

/**
 * Contenu lourd (carte MapLibre) dans un AnimatedContent : il n'est créé qu'une fois l'animation d'entrée finie.
 * Pendant l'animation, un simple fond le remplace : la carte apparaît d'un coup à sa taille finale, sans saccade.
 */
@Composable
fun AnimatedVisibilityScope.AfterEnter(content: @Composable () -> Unit) {
    val entered = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
    var shown by remember { mutableStateOf(entered) }
    LaunchedEffect(entered) { if (entered) shown = true }
    if (shown) content() else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow))
}
