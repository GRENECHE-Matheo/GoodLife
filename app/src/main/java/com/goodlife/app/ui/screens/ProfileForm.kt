@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.ActivityLevel
import com.goodlife.app.data.Goal
import com.goodlife.app.data.Profile
import com.goodlife.app.ai.Nutrition
import com.goodlife.app.data.Sex
import com.goodlife.app.ui.toNumber

@Composable
fun ProfileForm(initial: Profile, saveLabel: String, consentText: String? = null, onSave: (Profile) -> Unit) {
    // Consentement explicite (RGPD art. 9, données de santé) : case à cocher obligatoire si [consentText]
    var consent by remember { mutableStateOf(consentText == null) }
    var name by remember { mutableStateOf(initial.name) }
    var age by remember { mutableStateOf(initial.age.toString()) }
    var sex by remember { mutableStateOf(initial.sex) }
    var weight by remember { mutableStateOf(trim(initial.weightKg)) }
    var height by remember { mutableStateOf(trim(initial.heightCm)) }
    var activity by remember { mutableStateOf(initial.activity) }
    var goal by remember { mutableStateOf(initial.goal) }
    var habits by remember { mutableStateOf(initial.habits) }
    var allergies by remember { mutableStateOf(initial.allergies) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Prénom") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField(age, { age = it }, "Âge", Modifier.weight(1f), decimal = false)
            NumField(weight, { weight = it }, "Poids (kg)", Modifier.weight(1f))
            NumField(height, { height = it }, "Taille (cm)", Modifier.weight(1f))
        }
        Label("Sexe")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { s ->
                FilterChip(selected = sex == s, onClick = { sex = s }, label = { Text(s.label) })
            }
        }
        Label("Niveau d'activité")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityLevel.entries.forEach { a ->
                FilterChip(selected = activity == a, onClick = { activity = a }, label = { Text(a.label) })
            }
        }
        Label("Objectif")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.entries.forEach { g ->
                FilterChip(selected = goal == g, onClick = { goal = g }, label = { Text(g.label) })
            }
        }
        OutlinedTextField(
            value = habits, onValueChange = { habits = it },
            label = { Text("Habitudes alimentaires") },
            placeholder = { Text("Ex : végétarien, je saute souvent le petit-déj, j'aime la cuisine asiatique…") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = allergies, onValueChange = { allergies = it },
            label = { Text("Allergies / intolérances") },
            placeholder = { Text("Ex : arachides, lactose, gluten") },
            modifier = Modifier.fillMaxWidth()
        )
        if (consentText != null) {
            Row(
                Modifier.fillMaxWidth().toggleable(consent, role = Role.Checkbox) { consent = it },
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(checked = consent, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(consentText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            enabled = consent,
            onClick = {
                val a = age.toNumber()?.toInt()
                val w = weight.toNumber()
                val h = height.toNumber()
                error = when {
                    a != null && a in 1 until Nutrition.MIN_AGE ->
                        "GoodLife est réservée aux ${Nutrition.MIN_AGE} ans et plus."
                    a == null || a !in Nutrition.MIN_AGE..110 -> "Âge invalide (${Nutrition.MIN_AGE} à 110 ans)."
                    w == null || w !in 25.0..350.0 -> "Poids invalide."
                    h == null || h !in 100.0..250.0 -> "Taille invalide (en cm)."
                    goal == Goal.PERTE && !Nutrition.weightLossAllowed(
                        initial.copy(age = a, weightKg = w, heightCm = h)
                    ) -> if (a < 18)
                        "L'objectif « Perdre du poids » n'est pas proposé avant 18 ans : pendant la croissance, " +
                            "parles-en plutôt à un médecin. Choisis « Maintenir » ou « Prendre du poids »."
                    else
                        "Ton IMC est déjà sous 18,5 : l'objectif « Perdre du poids » n'est pas proposé. " +
                            "Si tu veux perdre du poids malgré tout, parles-en à un médecin."
                    else -> null
                }
                if (error == null) {
                    onSave(
                        initial.copy(
                            name = name.trim(), age = a!!, sex = sex, weightKg = w!!, heightCm = h!!,
                            activity = activity, goal = goal,
                            habits = habits.trim(), allergies = allergies.trim()
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(saveLabel) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, decimal: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
    )
}

private fun trim(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
