package com.goodlife.app.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
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
