package com.hugobou.hugotrain.data

import java.time.LocalDate

/** Una fila de la pestaña Plan. */
data class PlanItem(
    val row: Int,
    val date: LocalDate,
    val sport: String,
    val type: String,
    val description: String,
    val durationMin: Double?,
    val distanceKm: Double?,
    val intensity: String,
    val template: String,
    val notes: String,
    val status: String,
)

/** Una fila de la pestaña Actividades (normalmente importada de Strava). */
data class Activity(
    val stravaId: String,
    val date: LocalDate,
    val sport: String,
    val name: String,
    val distanceKm: Double?,
    val durationMin: Double?,
    val pace: String,
    val elevationM: Double?,
    val hrAvg: Double?,
    val hrMax: Double?,
    val rpe: Double?,
)

/** Una serie registrada en la pestaña Fuerza. */
data class StrengthSet(
    val date: LocalDate,
    val sessionId: String,
    val exercise: String,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double?,
    val notes: String,
)

/** Una fila de la pestaña Plantillas: un ejercicio dentro de una plantilla de fuerza. */
data class TemplateExercise(
    val template: String,
    val exercise: String,
    val kind: String, // "Gimnasio" o "Peso corporal"
    val sets: Int,
    val reps: Int,
    val weightKg: Double,
    val restSec: Int,
)

object Sheets {
    const val PLAN = "Plan"
    const val ACTIVITIES = "Actividades"
    const val STRENGTH = "Fuerza"
    const val TEMPLATES = "Plantillas"

    val HEADERS: Map<String, List<String>> = linkedMapOf(
        PLAN to listOf("Fecha", "Deporte", "Tipo", "Descripción", "Duración (min)", "Distancia (km)", "Intensidad", "Plantilla", "Notas", "Estado"),
        ACTIVITIES to listOf("Strava ID", "Fecha", "Deporte", "Nombre", "Distancia (km)", "Duración (min)", "Ritmo (min/km)", "Desnivel (m)", "FC media", "FC máx", "RPE"),
        STRENGTH to listOf("Fecha", "Sesión", "Ejercicio", "Serie", "Reps", "Peso (kg)", "RPE", "Notas"),
        TEMPLATES to listOf("Plantilla", "Ejercicio", "Tipo", "Series", "Reps", "Peso (kg)", "Descanso (s)"),
    )

    /** Deportes reconocidos (columna Deporte del plan y de actividades). */
    val SPORTS = listOf("Carrera", "Trail", "Natación", "Fuerza", "Caminata", "Senderismo", "Bici", "Descanso")
}
