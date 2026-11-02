package com.hugobou.hugotrain.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class AuthExpiredException : IOException("La sesión de Google ha caducado")

/** Cliente mínimo de la API REST de Google Sheets v4. */
class SheetsClient(private val http: OkHttpClient, private val token: () -> String) {

    private val base = "https://sheets.googleapis.com/v4/spreadsheets"
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun call(req: Request.Builder): JSONObject {
        val r = req.header("Authorization", "Bearer ${token()}").build()
        http.newCall(r).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) throw AuthExpiredException()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrDefault(body)
                throw IOException("Sheets ${resp.code}: $msg")
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    /** Crea una hoja nueva con las pestañas y cabeceras de la app. Devuelve su ID. */
    fun createSpreadsheet(title: String): String {
        val sheets = JSONArray()
        Sheets.HEADERS.keys.forEach { name ->
            sheets.put(JSONObject().put("properties", JSONObject().put("title", name)
                .put("gridProperties", JSONObject().put("frozenRowCount", 1))))
        }
        val body = JSONObject()
            .put("properties", JSONObject().put("title", title).put("locale", "es_ES").put("timeZone", "Europe/Madrid"))
            .put("sheets", sheets)
        val res = call(Request.Builder().url(base).post(body.toString().toRequestBody(json)))
        val id = res.getString("spreadsheetId")
        ensureHeaders(id)
        return id
    }

    /** Lista las pestañas existentes. */
    fun sheetTitles(id: String): List<String> {
        val res = call(Request.Builder().url("$base/$id?fields=sheets.properties.title").get())
        val arr = res.optJSONArray("sheets") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it).getJSONObject("properties").getString("title") }
    }

    /** Crea las pestañas que falten y escribe las cabeceras. */
    fun ensureHeaders(id: String) {
        val existing = sheetTitles(id).toSet()
        val missing = Sheets.HEADERS.keys.filter { it !in existing }
        if (missing.isNotEmpty()) {
            val reqs = JSONArray()
            missing.forEach { name ->
                reqs.put(JSONObject().put("addSheet", JSONObject().put("properties", JSONObject().put("title", name)
                    .put("gridProperties", JSONObject().put("frozenRowCount", 1)))))
            }
            call(Request.Builder().url("$base/$id:batchUpdate")
                .post(JSONObject().put("requests", reqs).toString().toRequestBody(json)))
        }
        val data = JSONArray()
        Sheets.HEADERS.forEach { (name, headers) ->
            data.put(JSONObject().put("range", "'$name'!A1").put("values", JSONArray().put(JSONArray(headers))))
        }
        call(Request.Builder().url("$base/$id/values:batchUpdate").post(
            JSONObject().put("valueInputOption", "RAW").put("data", data).toString().toRequestBody(json)))
    }

    /** Lee todas las filas de una pestaña (sin la cabecera). */
    fun readRows(id: String, sheet: String): List<List<Any?>> {
        val range = enc("'$sheet'!A2:Z")
        val res = call(Request.Builder().url(
            "$base/$id/values/$range?valueRenderOption=UNFORMATTED_VALUE&dateTimeRenderOption=SERIAL_NUMBER").get())
        val values = res.optJSONArray("values") ?: return emptyList()
        return (0 until values.length()).map { i ->
            val row = values.getJSONArray(i)
            (0 until row.length()).map { j -> row.opt(j).let { if (it == JSONObject.NULL) null else it } }
        }
    }

    /** Añade filas al final de una pestaña. Los números se envían como números. */
    fun appendRows(id: String, sheet: String, rows: List<List<Any?>>) {
        if (rows.isEmpty()) return
        val values = JSONArray()
        rows.forEach { r -> values.put(JSONArray(r.map { it ?: "" })) }
        val range = enc("'$sheet'!A1")
        call(Request.Builder().url("$base/$id/values/$range:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS")
            .post(JSONObject().put("values", values).toString().toRequestBody(json)))
    }

    /** Escribe un valor en una celda concreta (p. ej. el Estado de una fila del plan). */
    fun updateCell(id: String, a1: String, value: Any) {
        val range = enc(a1)
        call(Request.Builder().url("$base/$id/values/$range?valueInputOption=USER_ENTERED")
            .put(JSONObject().put("values", JSONArray().put(JSONArray().put(value))).toString().toRequestBody(json)))
    }
}
