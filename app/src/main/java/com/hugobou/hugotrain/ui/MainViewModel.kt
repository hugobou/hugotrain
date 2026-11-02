package com.hugobou.hugotrain.ui

import android.app.Application
import android.app.PendingIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hugobou.hugotrain.data.Activity
import com.hugobou.hugotrain.data.AuthExpiredException
import com.hugobou.hugotrain.data.GoogleAuth
import com.hugobou.hugotrain.data.PlanItem
import com.hugobou.hugotrain.data.Prefs
import com.hugobou.hugotrain.data.Repository
import com.hugobou.hugotrain.data.StrengthSet
import com.hugobou.hugotrain.data.TemplateExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Un ejercicio dentro de la sesión de fuerza en curso. */
data class SessionExercise(
    val name: String,
    val kind: String,
    val targetSets: Int,
    val targetReps: Int,
    val suggestedKg: Double,
    val restSec: Int,
    val hint: String,
    val sets: List<Pair<Int, Double>> = emptyList(), // (reps, kg)
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    val repo = Repository(app, prefs)

    var plan by mutableStateOf<List<PlanItem>>(emptyList())
        private set
    var activities by mutableStateOf<List<Activity>>(emptyList())
        private set
    var strength by mutableStateOf<List<StrengthSet>>(emptyList())
        private set
    var templates by mutableStateOf<List<TemplateExercise>>(emptyList())
        private set
    var pending by mutableStateOf(0)
        private set

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    /** Si Google necesita que el usuario acepte permisos, la UI lanza este intent. */
    var googleConsent by mutableStateOf<PendingIntent?>(null)
    var googleConnected by mutableStateOf(false)
        private set

    val session = mutableStateListOf<SessionExercise>()
    var sessionTitle by mutableStateOf("")
        private set
    var restEndsAt by mutableStateOf(0L)
    var restTotal by mutableStateOf(0)

    private var afterConsent: (() -> Unit)? = null

    init {
        reloadLocal()
        restoreSession()
        if (prefs.spreadsheetId.isNotBlank()) refresh(silent = true)
    }

    fun reloadLocal() {
        plan = repo.plan()
        activities = repo.activities()
        strength = repo.strength()
        templates = repo.templates()
        pending = repo.pendingCount()
    }

    /** Ejecuta [block] con un token de Google válido; pide permisos si hace falta. */
    private fun withGoogle(label: String, silent: Boolean = false, block: suspend () -> String?) {
        viewModelScope.launch {
            busy = true
            try {
                when (val r = GoogleAuth.authorize(getApplication())) {
                    is GoogleAuth.Result.NeedsUser -> {
                        if (silent) { googleConnected = false; return@launch }
                        afterConsent = { withGoogle(label, false, block) }
                        googleConsent = r.intent
                        return@launch
                    }
                    is GoogleAuth.Result.Token -> { repo.googleToken = r.token; googleConnected = true }
                }
                val msg = try {
                    withContext(Dispatchers.IO) { block() }
                } catch (e: AuthExpiredException) {
                    (GoogleAuth.authorize(getApplication()) as? GoogleAuth.Result.Token)?.let { repo.googleToken = it.token }
                    withContext(Dispatchers.IO) { block() }
                }
                reloadLocal()
                if (msg != null) message = msg
            } catch (e: Exception) {
                reloadLocal()
                if (!silent) message = "$label: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun onGoogleConsent(token: String?) {
        googleConsent = null
        if (token != null) {
            repo.googleToken = token
            googleConnected = true
            afterConsent?.invoke()
        } else message = "No se concedió el acceso a Google"
        afterConsent = null
    }

    fun connectGoogle() = withGoogle("Google") { "Cuenta de Google conectada" }

    fun refresh(silent: Boolean = false) = withGoogle("Actualizar", silent) {
        repo.flushPending()
        repo.refreshAll()
        if (silent) null else "Datos actualizados"
    }

    fun createSheet() = withGoogle("Crear hoja") { repo.createSpreadsheet(); "Hoja creada en tu Google Drive" }

    fun repairSheet() = withGoogle("Revisar hoja") { repo.ensureSheet(); "Hoja revisada: pestañas y cabeceras correctas" }

    fun syncStrava() = withGoogle("Strava") {
        val n = repo.syncStrava()
        if (n == 0) "No hay actividades nuevas en Strava" else "$n actividades nuevas importadas"
    }

    fun setPlanStatus(item: PlanItem, status: String) = withGoogle("Plan") { repo.setPlanStatus(item, status); null }

    fun stravaCode(code: String) {
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { repo.strava.exchangeCode(code) }
                message = "Strava conectado" + prefs.stravaAthlete.let { if (it.isBlank()) "" else " ($it)" }
                syncStrava()
            } catch (e: Exception) {
                message = "Strava: ${e.message}"
            } finally { busy = false }
        }
    }

    // ---------------- Sesión de fuerza ----------------

    /** Último registro de un ejercicio y sugerencia de progresión. */
    private fun suggestion(name: String, kind: String, targetSets: Int, targetReps: Int, baseKg: Double): Pair<Double, String> {
        val today = LocalDate.now()
        val history = strength.filter { it.exercise.equals(name, ignoreCase = true) && it.date < today }
        val lastDate = history.maxOfOrNull { it.date } ?: return baseKg to "Primera vez: empieza con ${fmtKg(baseKg)}"
        val last = history.filter { it.date == lastDate }
        val topKg = last.maxOf { it.weightKg }
        val allDone = last.size >= targetSets && last.all { it.reps >= targetReps }
        val summary = last.sortedBy { it.setNumber }.joinToString(" · ") { "${it.reps}×${fmtKg(it.weightKg)}" }
        val bodyweight = kind.contains("corporal", ignoreCase = true) || topKg == 0.0
        return when {
            allDone && bodyweight -> topKg to "Último ($lastDate): $summary → completaste todo: sube 1-2 reps por serie"
            allDone -> (topKg + 2.5) to "Último ($lastDate): $summary → completaste todo: sube a ${fmtKg(topKg + 2.5)}"
            else -> topKg to "Último ($lastDate): $summary → repite ${fmtKg(topKg)} hasta completar ${targetSets}×$targetReps"
        }
    }

    fun startSession(template: String?) {
        session.clear()
        sessionTitle = template ?: "Sesión libre"
        templates.filter { it.template == template }.forEach { t ->
            val (kg, hint) = suggestion(t.exercise, t.kind, t.sets, t.reps, t.weightKg)
            session += SessionExercise(t.exercise, t.kind, t.sets, t.reps, kg, t.restSec, hint)
        }
        saveSession()
    }

    fun addExercise(name: String) {
        if (name.isBlank()) return
        val known = templates.firstOrNull { it.exercise.equals(name.trim(), ignoreCase = true) }
        val kind = known?.kind ?: "Gimnasio"
        val sets = known?.sets ?: 3; val reps = known?.reps ?: 10
        val (kg, hint) = suggestion(name.trim(), kind, sets, reps, known?.weightKg ?: 0.0)
        session += SessionExercise(name.trim(), kind, sets, reps, kg, known?.restSec ?: 90, hint)
        if (sessionTitle.isBlank()) sessionTitle = "Sesión libre"
        saveSession()
    }

    fun logSet(index: Int, reps: Int, kg: Double) {
        val ex = session[index]
        session[index] = ex.copy(sets = ex.sets + (reps to kg))
        restTotal = ex.restSec
        restEndsAt = System.currentTimeMillis() + ex.restSec * 1000L
        saveSession()
    }

    fun removeLastSet(index: Int) {
        val ex = session[index]
        if (ex.sets.isNotEmpty()) session[index] = ex.copy(sets = ex.sets.dropLast(1))
        saveSession()
    }

    fun removeExercise(index: Int) { session.removeAt(index); saveSession() }

    fun addRest(seconds: Int) { restEndsAt += seconds * 1000L; restTotal += seconds }
    fun skipRest() { restEndsAt = 0 }

    fun discardSession() { session.clear(); sessionTitle = ""; restEndsAt = 0; saveSession() }

    fun finishSession(rpe: Double?, notes: String) {
        val today = LocalDate.now()
        val id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val rows = session.flatMap { ex ->
            ex.sets.mapIndexed { i, (reps, kg) ->
                StrengthSet(today, id, ex.name, i + 1, reps, kg, rpe, if (i == 0) notes else "")
            }
        }
        if (rows.isEmpty()) { message = "No hay series registradas"; return }
        repo.queueStrength(rows)
        discardSession()
        reloadLocal()
        message = "Sesión guardada (${rows.size} series)"
        withGoogle("Subir sesión", silent = true) { repo.flushPending(); "Sesión subida a la hoja" }
    }

    private fun saveSession() {
        val arr = JSONArray()
        session.forEach { e ->
            arr.put(JSONObject().put("name", e.name).put("kind", e.kind).put("ts", e.targetSets).put("tr", e.targetReps)
                .put("kg", e.suggestedKg).put("rest", e.restSec).put("hint", e.hint)
                .put("sets", JSONArray(e.sets.map { JSONArray(listOf(it.first, it.second)) })))
        }
        getApplication<Application>().getSharedPreferences("session", 0).edit()
            .putString("title", sessionTitle).putString("data", arr.toString()).apply()
    }

    private fun restoreSession() {
        val sp = getApplication<Application>().getSharedPreferences("session", 0)
        sessionTitle = sp.getString("title", "") ?: ""
        runCatching {
            val arr = JSONArray(sp.getString("data", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val s = o.getJSONArray("sets")
                session += SessionExercise(o.getString("name"), o.getString("kind"), o.getInt("ts"), o.getInt("tr"),
                    o.getDouble("kg"), o.getInt("rest"), o.optString("hint"),
                    (0 until s.length()).map { j -> s.getJSONArray(j).let { it.getInt(0) to it.getDouble(1) } })
            }
        }
    }
}

fun fmtKg(kg: Double): String = if (kg == 0.0) "peso corporal" else
    (if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.1f".format(kg)) + " kg"
