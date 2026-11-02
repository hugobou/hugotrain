package com.hugobou.hugotrain.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun StrengthScreen(vm: MainViewModel) {
    if (vm.session.isEmpty() && vm.sessionTitle.isBlank()) StartSession(vm) else ActiveSession(vm)
}

@Composable
private fun StartSession(vm: MainViewModel) {
    val today = LocalDate.now()
    val todayTemplates = vm.plan.filter { it.date == today && it.template.isNotBlank() }.map { it.template }.distinct()
    val all = vm.templates.map { it.template }.distinct()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Nueva sesión de fuerza", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (todayTemplates.isNotEmpty()) {
            item { Text("Planificado para hoy", style = MaterialTheme.typography.titleSmall) }
            todayTemplates.forEach { t ->
                item { Button(onClick = { vm.startSession(t) }, Modifier.fillMaxWidth()) { Text(t) } }
            }
        }
        if (all.isNotEmpty()) {
            item { Text("Plantillas", style = MaterialTheme.typography.titleSmall) }
            all.filter { it !in todayTemplates }.forEach { t ->
                item {
                    val n = vm.templates.count { it.template == t }
                    OutlinedButton(onClick = { vm.startSession(t) }, Modifier.fillMaxWidth()) { Text("$t ($n ejercicios)") }
                }
            }
        } else item {
            Text("Crea plantillas en la pestaña Plantillas de tu hoja (Plantilla, Ejercicio, Tipo, Series, Reps, Peso, Descanso).",
                style = MaterialTheme.typography.bodySmall)
        }
        item { OutlinedButton(onClick = { vm.startSession(null) }, Modifier.fillMaxWidth()) { Text("Sesión libre") } }
        val recent = vm.strength.groupBy { it.sessionId }.values.sortedByDescending { it.first().date }.take(5)
        if (recent.isNotEmpty()) {
            item { Spacer(Modifier.padding(4.dp)); Text("Últimas sesiones", style = MaterialTheme.typography.titleSmall) }
            recent.forEach { sets ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${sets.first().date}", fontWeight = FontWeight.Bold)
                            sets.groupBy { it.exercise }.forEach { (ex, s) ->
                                Text("$ex: " + s.joinToString(" · ") { "${it.reps}×${fmtKg(it.weightKg)}" },
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if (vm.pending > 0) item {
            Text("${vm.pending} series pendientes de subir (se subirán al tener conexión).",
                color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ActiveSession(vm: MainViewModel) {
    var newExercise by remember { mutableStateOf("") }
    var finishing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }

    Column {
        RestTimer(vm)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)) {
            item { Text(vm.sessionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            itemsIndexed(vm.session, key = { i, e -> "$i-${e.name}" }) { i, ex -> ExerciseCard(vm, i, ex) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newExercise, { newExercise = it }, Modifier.weight(1f),
                        label = { Text("Añadir ejercicio") }, singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.addExercise(newExercise); newExercise = "" }) { Text("Añadir") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { finishing = true }, Modifier.weight(1f)) { Text("Terminar y guardar") }
                    OutlinedButton(onClick = { discarding = true }) { Text("Descartar") }
                }
            }
        }
    }

    if (finishing) {
        var rpe by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Terminar sesión") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(rpe, { rpe = it }, label = { Text("Esfuerzo percibido (RPE 1-10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notas") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    finishing = false
                    vm.finishSession(rpe.replace(',', '.').toDoubleOrNull(), notes)
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text("Seguir") } },
        )
    }
    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text("¿Descartar la sesión?") },
            text = { Text("Se perderán las series registradas.") },
            confirmButton = { TextButton(onClick = { discarding = false; vm.discardSession() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { discarding = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ExerciseCard(vm: MainViewModel, index: Int, ex: SessionExercise) {
    val lastKg = ex.sets.lastOrNull()?.second ?: ex.suggestedKg
    var reps by remember(ex.name, ex.sets.size) { mutableStateOf(ex.targetReps.toString()) }
    var kg by remember(ex.name, ex.sets.size) { mutableStateOf(if (lastKg == 0.0) "0" else lastKg.toString().removeSuffix(".0")) }
    val complete = ex.sets.size >= ex.targetSets
    Card(colors = if (complete) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    else CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ex.name, fontWeight = FontWeight.Bold)
                    Text("Objetivo: ${ex.targetSets}×${ex.targetReps} · ${fmtKg(ex.suggestedKg)} · descanso ${ex.restSec}s",
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { vm.removeExercise(index) }) { Icon(Icons.Default.Close, "Quitar ejercicio") }
            }
            if (ex.hint.isNotBlank()) Text(ex.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            ex.sets.forEachIndexed { i, (r, k) -> Text("Serie ${i + 1}: $r reps × ${fmtKg(k)}") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(reps, { reps = it }, Modifier.weight(1f), label = { Text("Reps") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(kg, { kg = it }, Modifier.weight(1f), label = { Text("Kg") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Button(onClick = {
                    reps.toIntOrNull()?.let { r -> vm.logSet(index, r, kg.replace(',', '.').toDoubleOrNull() ?: 0.0) }
                }) { Text("+ Serie") }
                if (ex.sets.isNotEmpty()) IconButton(onClick = { vm.removeLastSet(index) }) {
                    Icon(Icons.Default.Undo, "Deshacer última serie")
                }
            }
        }
    }
}

@Composable
private fun RestTimer(vm: MainViewModel) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.restEndsAt) {
        var vibrated = false
        while (vm.restEndsAt > 0) {
            now = System.currentTimeMillis()
            if (now >= vm.restEndsAt && !vibrated) {
                vibrated = true
                vibrate(context)
                vm.skipRest()
            }
            delay(250)
        }
    }
    if (vm.restEndsAt <= 0) return
    val left = ((vm.restEndsAt - now) / 1000).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Descanso  %d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.addRest(15) }) { Text("+15 s") }
                TextButton(onClick = { vm.skipRest() }) { Text("Saltar") }
            }
            LinearProgressIndicator(
                progress = { if (vm.restTotal > 0) 1f - left.toFloat() / vm.restTotal else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun vibrate(context: Context) {
    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
}
