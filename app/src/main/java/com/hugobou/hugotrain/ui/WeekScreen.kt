package com.hugobou.hugotrain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hugobou.hugotrain.data.Activity
import com.hugobou.hugotrain.data.PlanItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val ES = Locale("es", "ES")

fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Minutos reales de una semana (actividades + 1 h por cada sesión de fuerza registrada en la app). */
private fun weekMinutes(vm: MainViewModel, start: LocalDate): Double {
    val end = start.plusDays(6)
    val act = vm.activities.filter { it.date in start..end }
    val actMin = act.sumOf { it.durationMin ?: 0.0 }
    val stravaStrengthDays = act.filter { it.sport == "Fuerza" }.map { it.date }.toSet()
    val appSessions = vm.strength.filter { it.date in start..end && it.date !in stravaStrengthDays }
        .map { it.sessionId }.distinct().size
    return actMin + appSessions * 60.0
}

private fun isDone(vm: MainViewModel, p: PlanItem): Boolean {
    if (p.status.equals("Hecho", true)) return true
    if (p.sport == "Descanso") return p.date < LocalDate.now()
    if (vm.activities.any { it.date == p.date && it.sport == p.sport }) return true
    if (p.sport == "Fuerza" && vm.strength.any { it.date == p.date }) return true
    return false
}

@Composable
fun WeekScreen(vm: MainViewModel) {
    var offset by rememberSaveable { mutableStateOf(0L) }
    val start = weekStart(LocalDate.now()).plusWeeks(offset)
    val end = start.plusDays(6)
    val weekPlan = vm.plan.filter { it.date in start..end }
    val weekActs = vm.activities.filter { it.date in start..end }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) { Icon(Icons.Default.ChevronLeft, "Semana anterior") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (offset == 0L) "Esta semana" else "Semana del ${start.dayOfMonth}/${start.monthValue}",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${start.dayOfMonth}/${start.monthValue} – ${end.dayOfMonth}/${end.monthValue}",
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { offset++ }) { Icon(Icons.Default.ChevronRight, "Semana siguiente") }
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Actualizar") }
            }
        }
        item { WeekSummary(vm, start, weekPlan, weekActs) }
        if (vm.plan.isEmpty()) item {
            Text("Aún no hay plan. Rellena la pestaña Plan de tu hoja y pulsa actualizar.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        items((0..6).map { start.plusDays(it.toLong()) }) { day ->
            DayCard(vm, day, weekPlan.filter { it.date == day }, weekActs.filter { it.date == day })
        }
    }
}

@Composable
private fun WeekSummary(vm: MainViewModel, start: LocalDate, plan: List<PlanItem>, acts: List<Activity>) {
    val plannedMin = plan.sumOf { it.durationMin ?: 0.0 }
    val doneMin = weekMinutes(vm, start)
    val prevMin = weekMinutes(vm, start.minusWeeks(1))
    val sessions = plan.filter { it.sport != "Descanso" }
    val done = sessions.count { isDone(vm, it) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Cumplimiento: $done de ${sessions.size} sesiones", fontWeight = FontWeight.Bold)
            Text("Tiempo: ${doneMin.roundToInt()} min hecho / ${plannedMin.roundToInt()} min planificado")
            val byKm = acts.groupBy { it.sport }.mapValues { (_, l) -> l.sumOf { it.distanceKm ?: 0.0 } }.filter { it.value > 0 }
            if (byKm.isNotEmpty()) Text(byKm.entries.joinToString("  ·  ") { "${it.key} ${"%.1f".format(it.value)} km" })
            if (prevMin > 0 && doneMin > 0) {
                val change = (doneMin - prevMin) / prevMin * 100
                val warn = change > 10
                Text(
                    "Carga vs semana anterior: ${if (change >= 0) "+" else ""}${change.roundToInt()} %" +
                        if (warn) "  ⚠ más del 10 %" else "",
                    color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DayCard(vm: MainViewModel, day: LocalDate, plan: List<PlanItem>, acts: List<Activity>) {
    val today = day == LocalDate.now()
    val dayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() }
    Card(
        colors = if (today) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text("$dayName ${day.dayOfMonth}" + if (today) " · hoy" else "", fontWeight = FontWeight.Bold)
            if (plan.isEmpty() && acts.isEmpty()) Text("Libre", style = MaterialTheme.typography.bodySmall)
            plan.forEach { PlanRow(vm, it) }
            val planned = plan.map { it.sport }.toSet()
            val extra = acts.filter { it.sport !in planned }
            if (extra.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                extra.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(a.sport); Spacer(Modifier.width(8.dp))
                        Text("Extra: ${a.sport} · ${activityLine(a)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // Lo realizado para cada deporte planificado
            acts.filter { it.sport in planned }.forEach { a ->
                Text("✓ ${a.name}: ${activityLine(a)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PlanRow(vm: MainViewModel, p: PlanItem) {
    val done = isDone(vm, p)
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 6.dp)) {
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                if (done) Icon(Icons.Default.CheckCircle, "Hecho", tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Outlined.Circle, "Pendiente", tint = sportColor(p.sport))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf("Hecho", "Saltado", "Modificado", "").forEach { s ->
                    DropdownMenuItem(text = { Text(if (s.isBlank()) "Quitar estado" else "Marcar: $s") },
                        onClick = { menu = false; vm.setPlanStatus(p, s) })
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val meta = listOfNotNull(
                p.type.ifBlank { null },
                p.durationMin?.let { "${it.roundToInt()} min" },
                p.distanceKm?.let { "${"%.1f".format(it)} km" },
                p.intensity.ifBlank { null },
            ).joinToString(" · ")
            Text("${p.sport}${if (meta.isNotBlank()) " · $meta" else ""}", fontWeight = FontWeight.SemiBold)
            if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall)
            if (p.template.isNotBlank()) Text("Plantilla: ${p.template}", style = MaterialTheme.typography.bodySmall)
            if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodySmall)
            if (p.status.isNotBlank() && !p.status.equals("Hecho", true))
                Text("Estado: ${p.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun Dot(sport: String) {
    Box(Modifier.size(10.dp).background(sportColor(sport), CircleShape))
}

fun activityLine(a: Activity): String = listOfNotNull(
    a.distanceKm?.takeIf { it > 0 }?.let { "${"%.2f".format(it)} km" },
    a.durationMin?.let { "${it.roundToInt()} min" },
    a.pace.ifBlank { null }?.let { "$it /km" },
    a.elevationM?.takeIf { it > 0 }?.let { "+${it.roundToInt()} m" },
    a.hrAvg?.let { "FC ${it.roundToInt()}" },
).joinToString(" · ")
