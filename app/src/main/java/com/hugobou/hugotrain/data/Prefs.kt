package com.hugobou.hugotrain.data

import android.content.Context
import android.content.SharedPreferences

/** Ajustes guardados en el móvil (nunca en la hoja ni en el repositorio). */
class Prefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("hugotrain", Context.MODE_PRIVATE)

    private fun s(key: String) = p.getString(key, "") ?: ""
    private fun put(key: String, v: String) = p.edit().putString(key, v).apply()

    var spreadsheetId: String get() = s("sheet_id"); set(v) = put("sheet_id", v.trim())
    var googleEmail: String get() = s("google_email"); set(v) = put("google_email", v)
    var stravaClientId: String get() = s("strava_client_id"); set(v) = put("strava_client_id", v.trim())
    var stravaClientSecret: String get() = s("strava_client_secret"); set(v) = put("strava_client_secret", v.trim())
    var stravaAccess: String get() = s("strava_access"); set(v) = put("strava_access", v)
    var stravaRefresh: String get() = s("strava_refresh"); set(v) = put("strava_refresh", v)
    var stravaAthlete: String get() = s("strava_athlete"); set(v) = put("strava_athlete", v)
    var stravaExpires: Long
        get() = p.getLong("strava_expires", 0)
        set(v) = p.edit().putLong("strava_expires", v).apply()

    /** Filas de fuerza pendientes de subir (JSON), para poder entrenar sin conexión. */
    var pendingStrength: String get() = p.getString("pending_strength", "[]") ?: "[]"; set(v) = put("pending_strength", v)

    fun stravaConnected() = stravaRefresh.isNotBlank()
    fun disconnectStrava() = p.edit().remove("strava_access").remove("strava_refresh").remove("strava_expires").remove("strava_athlete").apply()
}
