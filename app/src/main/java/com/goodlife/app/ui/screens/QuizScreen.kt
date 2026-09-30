package com.goodlife.app.ui.screens

import com.goodlife.app.ui.ScreenColumn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import com.goodlife.app.ui.Motion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.game.Game
import com.goodlife.app.game.QuizBank
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.Sfx
import com.goodlife.app.ui.Sounds
import com.goodlife.app.ui.XpGainCard
import com.goodlife.app.ui.days
import com.goodlife.app.ui.theme.failColor
import com.goodlife.app.ui.theme.successColor
import kotlinx.coroutines.delay

/**
 * Quiz du jour, présenté par le petit cuisto. Le premier essai du jour donne de l'XP ;
 * s'il y a une série cassée hier, 4 bonnes réponses sur 5 la récupèrent.
 * [totalXp] est l'XP totale actuelle : elle sert à animer la barre d'XP à la fin.
 */
@Composable
fun QuizScreen(recoverableStreak: Int, alreadyDone: Boolean, totalXp: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val questions = remember { QuizBank.today(appContext) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var answers by rememberSaveable { mutableStateOf("") }   // "1" bonne réponse, "0" mauvaise
    var finished by rememberSaveable { mutableStateOf(false) }
    // Valeurs figées au début du quiz (elles changent dès que le résultat est enregistré)
    val startRecoverable = rememberSaveable { recoverableStreak }
    val startDone = rememberSaveable { alreadyDone }
    val startXp = rememberSaveable { totalXp }
    val haptics = LocalHapticFeedback.current
    val good = successColor
    val bad = failColor

    LaunchedEffect(finished) {
        if (finished && !startDone) {
            val rescue = startRecoverable > 0 && correct >= QuizBank.PASS
            Repo.updateGame { g ->
                g.copy(
                    quizResults = g.quizResults + (localDay(0) to correct),
                    recoveredDays = if (rescue) g.recoveredDays + localDay(-1) else g.recoveredDays
                )
            }
        }
    }

    val quizProgress by animateFloatAsState(
        (index + if (selected != null || finished) 1 else 0).toFloat() / questions.size,
        animationSpec = spring(stiffness = Spring.StiffnessLow), label = "quizProgress"
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fermer") }
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { quizProgress },
                    modifier = Modifier.weight(1f).height(12.dp),
                    color = good,
                    strokeCap = StrokeCap.Round
                )
            }

            if (!finished) {
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { Motion.sharedAxisX(forward = true) },
                    label = "question"
                ) { idx ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val q = questions[idx]
                        val answered = selected
                        Row(verticalAlignment = Alignment.Top) {
                            ChefMascot(
                                size = 96.dp,
                                mood = when {
                                    answered == null -> ChefMood.QUESTION
                                    answered == q.correctIndex -> ChefMood.BRAVO
                                    else -> ChefMood.TRISTE
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        "Question ${idx + 1} sur ${questions.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(q.question, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        q.options.forEachIndexed { i, opt ->
                            AnswerButton(
                                text = opt,
                                state = when {
                                    answered == null -> AnswerState.IDLE
                                    i == q.correctIndex -> AnswerState.RIGHT
                                    i == answered -> AnswerState.WRONG
                                    else -> AnswerState.OTHER
                                },
                                onClick = {
                                    if (answered == null) {
                                        selected = i
                                        val ok = i == q.correctIndex
                                        if (ok) correct++
                                        answers += if (ok) "1" else "0"
                                        Sounds.play(if (ok) Sfx.CORRECT else Sfx.WRONG)
                                        haptics.performHapticFeedback(
                                            if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress
                                        )
                                    }
                                }
                            )
                        }
                        AnimatedVisibility(
                            visible = answered != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                        if (answered != null) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            val ok = answered == q.correctIndex
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = (if (ok) good else bad).copy(alpha = 0.12f)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        if (ok) "Bien joué !" else "Pas tout à fait…",
                                        fontWeight = FontWeight.Bold,
                                        color = if (ok) good else bad
                                    )
                                    Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Button(
                                onClick = {
                                    if (index < questions.lastIndex) { index++; selected = null } else finished = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text(if (index < questions.lastIndex) "Continuer" else "Voir le résultat") }
                        }
                        }
                    }
                }
            } else {
                QuizResult(
                    correct = correct,
                    total = questions.size,
                    answers = answers,
                    startDone = startDone,
                    startRecoverable = startRecoverable,
                    startXp = startXp,
                    endXp = if (startDone) startXp else totalXp,
                    onClose = onClose
                )
            }
        }
    }
}

private enum class AnswerState { IDLE, RIGHT, WRONG, OTHER }

/** Réponse : rebondit si c'est la bonne, tremble si c'est une erreur. */
@Composable
private fun AnswerButton(text: String, state: AnswerState, onClick: () -> Unit) {
    val good = successColor
    val bad = failColor
    val border by animateColorAsState(
        when (state) {
            AnswerState.IDLE -> MaterialTheme.colorScheme.outline
            AnswerState.RIGHT -> good
            AnswerState.WRONG -> bad
            AnswerState.OTHER -> MaterialTheme.colorScheme.outlineVariant
        }, label = "answerBorder"
    )
    val fill by animateColorAsState(
        when (state) {
            AnswerState.RIGHT -> good.copy(alpha = 0.14f)
            AnswerState.WRONG -> bad.copy(alpha = 0.14f)
            else -> Color.Transparent
        }, label = "answerFill"
    )
    val scale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            AnswerState.RIGHT -> {
                scale.animateTo(1.05f, tween(110))
                scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
            }
            AnswerState.WRONG -> shake.animateTo(0f, keyframes {
                durationMillis = 420
                -14f at 60; 12f at 130; -9f at 200; 6f at 270; -3f at 340; 0f at 420
            })
            else -> Unit
        }
    }
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = fill),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).graphicsLayer {
            scaleX = scale.value; scaleY = scale.value
            translationX = shake.value * density
        }
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (state == AnswerState.RIGHT || state == AnswerState.WRONG) {
            Icon(
                if (state == AnswerState.RIGHT) Icons.Filled.Check else Icons.Filled.Close, null,
                tint = if (state == AnswerState.RIGHT) good else bad
            )
        }
    }
}

@Composable
private fun QuizResult(
    correct: Int,
    total: Int,
    answers: String,
    startDone: Boolean,
    startRecoverable: Int,
    startXp: Int,
    endXp: Int,
    onClose: () -> Unit
) {
    val passed = correct >= QuizBank.PASS
    val rescued = startRecoverable > 0 && passed && !startDone
    val good = successColor
    val bad = failColor

    // Apparition en cascade des éléments du résultat
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(4) { delay(if (it == 0) 80 else 160); step++ }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedVisibility(
            step >= 1,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 300f)) + fadeIn()
        ) {
            ChefMascot(size = 150.dp, mood = if (passed) ChefMood.BRAVO else ChefMood.TRISTE)
        }
        AnimatedVisibility(step >= 2, enter = fadeIn() + slideInVertically { it / 2 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "$correct / $total bonnes réponses",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                // Une pastille par question, qui apparaissent l'une après l'autre
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    answers.forEachIndexed { i, c ->
                        val pop = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            delay(120L * i)
                            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (c == '1') good else bad,
                            modifier = Modifier.size(28.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        ) {
                            Icon(
                                if (c == '1') Icons.Filled.Check else Icons.Filled.Close, null,
                                tint = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(5.dp)
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(step >= 3, enter = fadeIn() + slideInVertically { it / 2 }) {
            Text(
                when {
                    startDone -> "Entraînement terminé ! (L'XP du quiz n'est comptée qu'une fois par jour.)"
                    rescued -> "Série sauvée ! Ta série de ${days(startRecoverable)} continue."
                    startRecoverable > 0 -> "Il fallait ${QuizBank.PASS} bonnes réponses pour sauver ta série. " +
                        "Elle repart de zéro, mais tu gagnes quand même de l'XP. Demain est un nouveau jour !"
                    else -> "Reviens demain pour de nouvelles questions !"
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        AnimatedVisibility(step >= 4, enter = fadeIn() + slideInVertically { it / 2 }) {
            SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                // La barre part de l'XP d'avant le quiz et monte jusqu'à la nouvelle valeur
                XpGainCard(startXp = startXp, endXp = maxOf(startXp, endXp))
                if (!startDone) {
                    Text(
                        "Quiz : +${Game.quizXp(correct)} XP" + if (rescued) " · jour d'hier rattrapé" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Button(
            onClick = onClose,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Terminer") }
    }
}
