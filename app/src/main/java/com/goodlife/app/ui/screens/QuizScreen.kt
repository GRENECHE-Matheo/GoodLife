package com.goodlife.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.game.Game
import com.goodlife.app.game.QuizBank
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood

private val GOOD = Color(0xFF1E8E3E)
private val BAD = Color(0xFFD93025)

/**
 * Quiz du jour, présenté par le petit cuisto. Le premier essai du jour donne de l'XP ;
 * s'il y a une série cassée hier, 4 bonnes réponses sur 5 la récupèrent.
 */
@Composable
fun QuizScreen(recoverableStreak: Int, alreadyDone: Boolean, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val questions = remember { QuizBank.forDay() }
    var index by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var correct by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    // Valeurs figées au début du quiz (elles changent dès que le résultat est enregistré)
    val startRecoverable = remember { recoverableStreak }
    val startDone = remember { alreadyDone }

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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fermer") }
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { (index + if (selected != null || finished) 1 else 0).toFloat() / questions.size },
                    modifier = Modifier.weight(1f).height(12.dp),
                    color = GOOD,
                    strokeCap = StrokeCap.Round
                )
            }

            if (!finished) {
                val q = questions[index]
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
                                "Question ${index + 1} sur ${questions.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(q.question, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                q.options.forEachIndexed { i, opt ->
                    val isRight = i == q.correctIndex
                    val color = when {
                        answered == null -> MaterialTheme.colorScheme.outline
                        isRight -> GOOD
                        i == answered -> BAD
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    OutlinedButton(
                        onClick = {
                            if (answered == null) {
                                selected = i
                                if (isRight) correct++
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, color),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = when {
                                answered != null && isRight -> GOOD.copy(alpha = 0.12f)
                                answered == i -> BAD.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) {
                        Text(opt, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (answered != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = (if (answered == q.correctIndex) GOOD else BAD).copy(alpha = 0.10f)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (answered == q.correctIndex) "Bien joué !" else "Pas tout à fait…",
                                fontWeight = FontWeight.Bold,
                                color = if (answered == q.correctIndex) GOOD else BAD
                            )
                            Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(
                        onClick = {
                            if (index < questions.lastIndex) { index++; selected = null } else finished = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GOOD),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text(if (index < questions.lastIndex) "Continuer" else "Voir le résultat") }
                }
            } else {
                val passed = correct >= QuizBank.PASS
                val rescued = startRecoverable > 0 && passed && !startDone
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ChefMascot(size = 160.dp, mood = if (passed) ChefMood.BRAVO else ChefMood.TRISTE)
                }
                Text(
                    "$correct / ${questions.size} bonnes réponses",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    when {
                        startDone -> "Entraînement terminé ! (L'XP du quiz n'est comptée qu'une fois par jour.)"
                        rescued -> "Série sauvée ! Tes $startRecoverable jours continuent. +${Game.quizXp(correct)} XP"
                        startRecoverable > 0 -> "Il fallait ${QuizBank.PASS} bonnes réponses pour sauver ta série. " +
                            "Elle repart de zéro, mais tu gagnes quand même +${Game.quizXp(correct)} XP. Demain est un nouveau jour !"
                        else -> "+${Game.quizXp(correct)} XP. Reviens demain pour de nouvelles questions !"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GOOD),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Terminer") }
            }
        }
    }
}
