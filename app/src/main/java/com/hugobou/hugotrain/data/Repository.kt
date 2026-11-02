package com.hugobou.hugotrain.data

import android.content.Context
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Punto único de acceso a los datos. Guarda una copia local de cada pestaña
 * para poder usar la app sin conexión, y una cola de series de fuerza pendientes.
 */
class Repository(context: Context, val prefs: Prefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile var googleToken: String = ""
    val sheets = SheetsClient(http) { googleToken }
    val strava = StravaClient(http, prefs)
    private val cacheDir = File(context.filesDir, "cache").apply { mkdirs() }

    private val sheetId get() = prefs.spreadsheetId.ifBlank { throw IllegalStateException("Configura primero la hoja en Ajustes") }

    // ---------- caché local ----------

    private fun cacheFile(sheet: String) = File(cacheDir, "$sheet.json")

    private fun saveCache(sheet: String, rows: List<List<Any?>>) {
        val arr = JSONArray()
        rows.forEach { r -> arr.put(JSONArray(r.map { it ?: "" })) }
        cacheFile(sheet).writeText(arr.toString())
    }

    private fun loadCache(sheet: String): List<List<Any?>> {
        val f = cacheFile(sheet)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val r = arr.getJSONArray(i)
                (0 until r.length()).map { j -> r.opt(j) }
            }
        }.getOrDefault(emptyList())
    }

    // ---------- lectura ----------

    /** Descarga todas las pestañas y actualiza la caché. */
    fun refreshAll() {
        Sheets.HEADERS.keys.forEach { sheet -> saveCache(sheet, sheets.readRows(sheetId, sheet)) }
    }

    fun plan(): List<PlanItem> = loadCache(Sheets.PLAN).mapIndexedNotNull { i, r ->
        val date = Cells.date(Cells.get(r, 0)) ?: return@mapIndexedNotNull null
        PlanItem(
            row = i + 2, date = date,
            sport = Cells.str(Cells.get(r, 1)), type = Cells.str(Cells.get(r, 2)),
            description = Cells.str(Cells.get(r, 3)),
            durationMin = Cells.num(Cells.get(r, 4)), distanceKm = Cells.num(Cells.get(r, 5)),
            intensity = Cells.str(Cells.get(r, 6)), template = Cells.str(Cells.get(r, 7)),
            notes = Cells.str(Cells.get(r, 8)), status = Cells.str(Cells.get(r, 9)),
        )
    }

    fun activities(): List<Activity> = loadCache(Sheets.ACTIVITIES).mapNotNull { r ->
        val date = Cells.date(Cells.get(r, 1)) ?: return@mapNotNull null
        Activity(
            stravaId = Cells.str(Cells.get(r, 0)), date = date,
            sport = Cells.str(Cells.get(r, 2)), name = Cells.str(Cells.get(r, 3)),
            distanceKm = Cells.num(Cells.get(r, 4)), durationMin = Cells.num(Cells.get(r, 5)),
            pace = Cells.str(Cells.get(r, 6)), elevationM = Cells.num(Cells.get(r, 7)),
            hrAvg = Cells.num(Cells.get(r, 8)), hrMax = Cells.num(Cells.get(r, 9)),
            rpe = Cells.num(Cells.get(r, 10)),
        )
    }

    fun strength(): List<StrengthSet> = (loadCache(Sheets.STRENGTH) + pendingRows()).mapNotNull { r ->
        val date = Cells.date(Cells.get(r, 0)) ?: return@mapNotNull null
        StrengthSet(
            date = date, sessionId = Cells.str(Cells.get(r, 1)), exercise = Cells.str(Cells.get(r, 2)),
            setNumber = Cells.int(Cells.get(r, 3)) ?: 0, reps = Cells.int(Cells.get(r, 4)) ?: 0,
            weightKg = Cells.num(Cells.get(r, 5)) ?: 0.0, rpe = Cells.num(Cells.get(r, 6)),
            notes = Cells.str(Cells.get(r, 7)),
        )
    }

    fun templates(): List<TemplateExercise> = loadCache(Sheets.TEMPLATES).mapNotNull { r ->
        val name = Cells.str(Cells.get(r, 0)); val ex = Cells.str(Cells.get(r, 1))
        if (name.isBlank() || ex.isBlank()) return@mapNotNull null
        TemplateExercise(
            template = name, exercise = ex, kind = Cells.str(Cells.get(r, 2)).ifBlank { "Gimnasio" },
            sets = Cells.int(Cells.get(r, 3)) ?: 3, reps = Cells.int(Cells.get(r, 4)) ?: 10,
            weightKg = Cells.num(Cells.get(r, 5)) ?: 0.0, restSec = Cells.int(Cells.get(r, 6)) ?: 90,
        )
    }

    // ---------- fuerza (con cola offline) ----------

    private fun pendingRows(): List<List<Any?>> = runCatching {
        val arr = JSONArray(prefs.pendingStrength)
        (0 until arr.length()).map { i -> val r = arr.getJSONArray(i); (0 until r.length()).map { r.opt(it) } }
    }.getOrDefault(emptyList())

    fun pendingCount() = pendingRows().size

    fun queueStrength(sets: List<StrengthSet>) {
        val arr = JSONArray(prefs.pendingStrength)
        sets.forEach { s ->
            arr.put(JSONArray(listOf(s.date.toString(), s.sessionId, s.exercise, s.setNumber, s.reps, s.weightKg,
                s.rpe ?: "", s.notes)))
        }
        prefs.pendingStrength = arr.toString()
    }

    /** Sube las series pendientes. Si falla, se quedan en la cola. */
    fun flushPending(): Int {
        val rows = pendingRows()
        if (rows.isEmpty()) return 0
        sheets.appendRows(sheetId, Sheets.STRENGTH, rows)
        prefs.pendingStrength = "[]"
        saveCache(Sheets.STRENGTH, sheets.readRows(sheetId, Sheets.STRENGTH))
        return rows.size
    }

    // ---------- Strava ----------

    /** Trae de Strava las actividades nuevas y las añade a la pestaña Actividades. */
    fun syncStrava(): Int {
        val existing = sheets.readRows(sheetId, Sheets.ACTIVITIES)
        val ids = existing.map { Cells.str(Cells.get(it, 0)) }.toSet()
        val lastDate = existing.mapNotNull { Cells.date(Cells.get(it, 1)) }.maxOrNull()
        val from = (lastDate?.minusDays(3) ?: LocalDate.now().minusDays(60))
        val after = from.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val fresh = strava.activitiesSince(after).filter { it.stravaId !in ids }.sortedBy { it.date }
        if (fresh.isNotEmpty()) {
            sheets.appendRows(sheetId, Sheets.ACTIVITIES, fresh.map { a ->
                listOf(a.stravaId, a.date.toString(), a.sport, a.name, a.distanceKm, a.durationMin,
                    if (a.pace.isBlank()) "" else "'" + a.pace, // apóstrofo: que Sheets no lo convierta en hora
                    a.elevationM, a.hrAvg ?: "", a.hrMax ?: "", "")
            })
        }
        saveCache(Sheets.ACTIVITIES, sheets.readRows(sheetId, Sheets.ACTIVITIES))
        return fresh.size
    }

    // ---------- plan ----------

    fun setPlanStatus(item: PlanItem, status: String) {
        sheets.updateCell(sheetId, "'${Sheets.PLAN}'!J${item.row}", status)
        saveCache(Sheets.PLAN, sheets.readRows(sheetId, Sheets.PLAN))
    }

    fun createSpreadsheet(): String {
        val id = sheets.createSpreadsheet("HugoTrain")
        prefs.spreadsheetId = id
        refreshAll()
        return id
    }

    fun ensureSheet() {
        sheets.ensureHeaders(sheetId)
        refreshAll()
    }
}
