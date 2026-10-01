@file:OptIn(ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Nutrition
import com.goodlife.app.data.ActivityLevel
import com.goodlife.app.data.Goal
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Repo
import com.goodlife.app.data.Sex
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.Motion
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.SlideSwitch
import com.goodlife.app.ui.toNumber

private const val PAGES = 8

@Composable
fun OnboardingScreen() {
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    SlideSwitch(showPolicy) { open ->
        if (open) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                PrivacyScreen(onBack = { showPolicy = false })
            }
        } else {
            OnboardingPages(onOpenPolicy = { showPolicy = true })
        }
    }
}

/**
 * Inscription en plusieurs pages courtes : bienvenue, confidentialité, toi, ton corps, ton objectif,
 * récapitulatif (consentement santé) et notifications du coach. Rien n'est enregistré avant la dernière page.
 */
@Composable
private fun OnboardingPages(onOpenPolicy: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var policyOk by rememberSaveable { mutableStateOf(false) }
    var healthOk by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(Sex.HOMME) }
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable { mutableStateOf(ActivityLevel.MODERE) }
    var goal by rememberSaveable { mutableStateOf(Goal.MAINTIEN) }
    var habits by rememberSaveable { mutableStateOf("") }
    var allergies by rememberSaveable { mutableStateOf("") }
    var notifs by rememberSaveable { mutableStateOf(listOf(true, true, true, true)) }
    // Pas : compter ou non, avec quoi, et quel objectif
    val canSensor = remember { com.goodlife.app.steps.Steps.sensorAvailable(context) }
    val canHc = remember { com.goodlife.app.steps.Steps.healthConnectAvailable(context) }
    var stepsOn by rememberSaveable { mutableStateOf(canSensor || canHc) }
    var stepsSource by rememberSaveable { mutableStateOf(if (canSensor) "sensor" else "hc") }
    var stepsMode by rememberSaveable { mutableStateOf("auto") }
    var stepsManual by rememberSaveable { mutableStateOf("8000") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun profile() = Profile(
        name = name.trim(), age = age.toNumber()?.toInt() ?: 0, sex = sex,
        weightKg = weight.toNumber() ?: 0.0, heightCm = height.toNumber() ?: 0.0,
        activity = activity, goal = goal, habits = habits.trim(), allergies = allergies.trim()
    )

    fun finish(prefs: NotifPrefs) {
        Repo.updateSettings {
            it.copy(
                privacyAcceptedAt = System.currentTimeMillis(),
                stepsGoalMode = stepsMode,
                stepsGoalManual = stepsManual.toNumber()?.toInt()?.coerceIn(1000, 40_000) ?: 8000
            )
        }
        Repo.saveProfile(Nutrition.formulaTarget(profile()))
        saveNotifPrefs(context, prefs)
    }
    val prefs = NotifPrefs(notifs[0], notifs[1], notifs[2], notifs[3])
    val askNotif = rememberNotifPermission { granted -> finish(if (granted) prefs else NotifPrefs(false, false, false, false)) }

    // Autorisations pour compter les pas (demandées en quittant la page « Tes pas »)
    val askSensor = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (ok) { Repo.updateSettings { it.copy(stepsEnabled = true, stepsSource = "sensor") }; com.goodlife.app.steps.Steps.schedule(context) }
        else stepsOn = false
        error = null; page++
    }
    val askHc = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(com.goodlife.app.steps.Steps.HC_PERMISSIONS)) Repo.updateSettings { it.copy(stepsEnabled = true, stepsSource = "hc") }
        else stepsOn = false
        error = null; page++
    }
    fun leaveStepsPage() {
        when {
            !stepsOn -> { Repo.updateSettings { it.copy(stepsEnabled = false) }; page++ }
            stepsSource == "hc" && canHc -> askHc.launch(com.goodlife.app.steps.Steps.HC_PERMISSIONS)
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                !com.goodlife.app.steps.Steps.hasActivityPermission(context) -> askSensor.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
            else -> { Repo.updateSettings { it.copy(stepsEnabled = true, stepsSource = "sensor") }; com.goodlife.app.steps.Steps.schedule(context); page++ }
        }
    }

    fun next() {
        if (page == 5) {
            error = if (stepsOn && stepsMode == "manual" && (stepsManual.toNumber()?.toInt() ?: 0) !in 1000..40_000)
                t("Choisis un objectif entre 1 000 et 40 000 pas.") else null
            if (error == null) leaveStepsPage()
            return
        }
        error = when (page) {
            1 -> if (!policyOk) t("Coche la case pour accepter la politique de confidentialité.") else null
            2 -> ageError(age.toNumber()?.toInt())
            3 -> bodyError(weight.toNumber(), height.toNumber())
            4 -> goalError(goal, profile())
            6 -> if (!healthOk) t("Coche la case pour que GoodLife puisse enregistrer tes données.") else null
            else -> null
        }
        if (error == null && page < PAGES - 1) page++
    }
    fun back() { error = null; if (page > 0) page-- }
    BackHandler(enabled = page > 0) { back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                // Barre de progression
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Retour")) }
                    else Spacer(Modifier.size(48.dp))
                    val progress by animateFloatAsState((page + 1f) / PAGES, label = "progress")
                    LinearProgressIndicator(
                        progress = { progress }, strokeCap = StrokeCap.Round,
                        modifier = Modifier.weight(1f).height(8.dp)
                    )
                    Text("${page + 1}/$PAGES", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { Motion.sharedAxisX(forward = targetState > initialState) },
                    modifier = Modifier.weight(1f),
                    label = "page"
                ) { p ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (p) {
                            0 -> WelcomePage()
                            1 -> PolicyPage(policyOk, { policyOk = it; error = null }, onOpenPolicy)
                            2 -> {
                                PageTitle(ChefMood.CONTENT, t("Faisons connaissance"), t("Pour personnaliser tes conseils. Ton prénom reste sur ton téléphone."))
                                OutlinedTextField(name, { name = it.take(40) }, label = { Text(t("Prénom (facultatif)")) },
                                    singleLine = true, modifier = Modifier.fillMaxWidth())
                                Field(age, { age = it; error = null }, t("Âge"), decimal = false)
                                Chips(t("Sexe (pour le calcul des besoins)"), Sex.entries, sex, { it.label }) { sex = it }
                            }
                            3 -> {
                                PageTitle(ChefMood.QUESTION, t("Ton corps"), t("Pour estimer tes besoins en énergie. Tu pourras tout modifier plus tard."))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Field(weight, { weight = it; error = null }, t("Poids (kg)"), Modifier.weight(1f))
                                    Field(height, { height = it; error = null }, t("Taille (cm)"), Modifier.weight(1f))
                                }
                                Chips(t("Au quotidien, tu es plutôt…"), ActivityLevel.entries, activity, { it.label }) { activity = it }
                                Text(ACTIVITY_HELP[activity] ?: "", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            4 -> {
                                PageTitle(ChefMood.BRAVO, t("Ton objectif"), t("Le chef adapte le score, les idées de repas et les conseils."))
                                Chips(t("Objectif"), Goal.entries, goal, { it.label }) { goal = it; error = null }
                                OutlinedTextField(habits, { habits = it.take(300) }, label = { Text(t("Habitudes alimentaires (facultatif)")) },
                                    placeholder = { Text(t("Ex : végétarien, je saute souvent le petit-déj…")) },
                                    minLines = 2, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(allergies, { allergies = it.take(200) }, label = { Text(t("Allergies / intolérances (facultatif)")) },
                                    placeholder = { Text(t("Ex : arachides, lactose, gluten")) }, modifier = Modifier.fillMaxWidth())
                            }
                            5 -> {
                                PageTitle(ChefMood.SPORT, t("Tes pas"), t("Bouger un peu plus chaque jour compte autant que bien manger."))
                                Row(Modifier.fillMaxWidth().toggleable(stepsOn, role = Role.Switch) { stepsOn = it }, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t("Compter mes pas"), style = MaterialTheme.typography.bodyLarge)
                                        Text(t("Comptés sur le téléphone, jamais envoyés (sauf ta moyenne à l'IA si tu choisis son conseil)."),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    androidx.compose.material3.Switch(checked = stepsOn, onCheckedChange = null)
                                }
                                if (stepsOn) {
                                    if (canSensor && canHc) Chips(t("Avec quoi ?"), listOf("sensor", "hc"), stepsSource,
                                        { if (it == "sensor") t("Capteur du téléphone") else t("Health Connect (montre, Samsung Health…)") }) { stepsSource = it }
                                    else Text(if (canSensor) t("Avec le capteur de pas du téléphone.") else t("Avec Health Connect (ton téléphone n'a pas de capteur de pas)."),
                                        style = MaterialTheme.typography.bodyMedium)
                                    Chips(t("Mon objectif par jour"), listOf("auto", "manual", "ia"), stepsMode, {
                                        when (it) { "auto" -> t("Automatique"); "manual" -> t("Je choisis"); else -> t("Conseil de l'IA") }
                                    }) { stepsMode = it }
                                    Text(
                                        when (stepsMode) {
                                            "auto" -> t("Ta moyenne des 7 derniers jours + 10 %, pour progresser doucement (6 000 pas au début).")
                                            "manual" -> t("Tu fixes ton objectif, tu pourras le changer quand tu veux.")
                                            else -> t("Chaque jour, l'IA regarde tes vrais pas de la semaine et ajuste ton objectif petit à petit (jamais plus de 15 % d'un jour à l'autre). Il faut activer l'IA (18 ans et plus) à l'étape suivante ; en attendant, l'objectif est automatique.")
                                        },
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (stepsMode == "manual") Field(stepsManual, { stepsManual = it; error = null }, t("Pas par jour"), decimal = false)
                                }
                            }
                            6 -> SummaryPage(profile(), healthOk) { healthOk = it; error = null }
                            else -> {
                                PageTitle(ChefMood.BRAVO, t("Le chef t'accompagne"), t("Des petits messages pour garder le rythme, sans t'embêter."))
                                NotifChoices(prefs) { n -> notifs = listOf(n.morning, n.noon, n.evening, n.weekly) }
                                Text(
                                    t("Préparés sur ton téléphone, jamais plus d'un à la fois. Sur l'écran verrouillé, seul « Un message du chef » s'affiche. Tu peux tout changer dans Paramètres."),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                // Boutons du bas
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (error != null) Text(
                        error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    when (page) {
                        0 -> {
                            Button(onClick = { next() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("C'est parti !")) }
                        }
                        PAGES - 1 -> {
                            Button(
                                onClick = { if (prefs.any) askNotif() else finish(prefs) },
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text(t("Terminer")) }
                            TextButton(onClick = { finish(NotifPrefs(false, false, false, false)) }, modifier = Modifier.fillMaxWidth()) {
                                Text(t("Pas de notifications pour l'instant"))
                            }
                        }
                        else -> Button(onClick = { next() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("Continuer")) }
                    }
                }
            }
        }
    }
}

private val ACTIVITY_HELP = mapOf(
    ActivityLevel.SEDENTAIRE to t("Surtout assis (bureau, cours), peu de marche."),
    ActivityLevel.LEGER to t("Un peu de marche chaque jour, ou du sport 1 à 2 fois par semaine."),
    ActivityLevel.MODERE to t("Souvent debout ou en mouvement, ou du sport 3 à 4 fois par semaine."),
    ActivityLevel.ACTIF to t("Métier physique, ou du sport presque tous les jours."),
    ActivityLevel.TRES_ACTIF to t("Entraînement intense chaque jour ou travail très physique.")
)

@Composable
private fun ColumnScope.WelcomePage() {
    ChefMascot(size = 120.dp, mood = ChefMood.BRAVO, modifier = Modifier.align(Alignment.CenterHorizontally))
    Text(t("Bienvenue sur GoodLife"), style = MaterialTheme.typography.headlineLarge)
    Text(t("Ton coach pour mieux manger et bouger plus, à ton rythme."), style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Feature(Icons.Filled.Restaurant, t("Mange mieux, sans te priver"), t("Photo de ton assiette, idées de repas, planning de la semaine."))
    Feature(Icons.Filled.DirectionsRun, t("Bouge plus"), t("Pas, programme sportif, course, marche et vélo avec la carte."))
    Feature(Icons.Filled.LocalFireDepartment, t("Reste motivé"), t("Séries, niveaux, quiz et les petits mots du chef."))
    Feature(Icons.Filled.Lock, t("Tes données restent chez toi"), t("Aucun compte, aucune pub, tout est chiffré sur ton téléphone."))
    Text(
        t("Réservé aux %1\$s ans et plus. GoodLife est une app de bien-être, pas un dispositif médical : demande l'avis d'un professionnel de santé avant de changer ton alimentation.", Nutrition.MIN_AGE),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SectionCard(title = t("Tu changes de téléphone ?"), icon = Icons.Filled.Restore) {
        Text(
            t("Restaure ta sauvegarde GoodLife (fichier .goodlife) avec son mot de passe pour tout retrouver."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RestoreButton(outlined = true)
    }
}

@Composable
private fun PolicyPage(accepted: Boolean, onAccept: (Boolean) -> Unit, onOpenPolicy: () -> Unit) {
    PageTitle(ChefMood.CONTENT, t("Tes données restent chez toi"), t("Voici exactement ce qui peut quitter ton téléphone, et quand."))
    SectionCard {
        DataFlowSummary()
        TextButton(onClick = onOpenPolicy) { Text(t("Lire la politique de confidentialité complète")) }
    }
    CheckLine(accepted, onAccept, t("J'ai lu et j'accepte la politique de confidentialité de GoodLife."))
}

@Composable
private fun SummaryPage(p: Profile, consent: Boolean, onConsent: (Boolean) -> Unit) {
    val t = Nutrition.formulaTarget(p)
    PageTitle(ChefMood.BRAVO, if (p.name.isBlank()) t("C'est presque fini !") else t("C'est presque fini, %1\$s !", p.name), t("Voici ton point de départ."))
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Ton objectif quotidien"), style = MaterialTheme.typography.labelLarge)
            Text("${com.goodlife.app.coach.Coach.fmt(t.targetKcal)} kcal", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
            Text(t("Protéines %1\$s g · Glucides %2\$s g · Lipides %3\$s g", t.proteinG, t.carbsG, t.fatG), style = MaterialTheme.typography.bodyMedium)
            Text(
                t("Calculé sur ton téléphone (formule de Mifflin-St Jeor) selon ton âge, ton poids, ta taille, ton activité et ton objectif « %1\$s ». Tu pourras l'affiner plus tard.", p.goal.label),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    CheckLine(
        consent, onConsent,
        t("J'accepte que GoodLife enregistre sur ce téléphone mes données de santé (poids, taille, repas, pas, sommeil, allergies) pour calculer mes besoins. Je peux retirer cet accord en effaçant mes données (Paramètres).")
    )
}

@Composable
private fun PageTitle(mood: ChefMood, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChefMascot(size = 64.dp, mood = mood)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CheckLine(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun <T> Chips(label: String, values: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v -> FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(name(v)) }) }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), decimal: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(6)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
    )
}
