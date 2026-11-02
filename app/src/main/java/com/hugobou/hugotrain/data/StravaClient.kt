package com.hugobou.hugotrain.data

import android.net.Uri
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** Cliente de la API de Strava (OAuth + lectura de actividades). */
class StravaClient(private val http: OkHttpClient, private val prefs: Prefs) {

    companion object {
        const val REDIRECT = "hugotrain://localhost/strava"

        /** Traduce el sport_type de Strava al deporte de la app. */
        fun sportOf(type: String): String = when (type) {
            "Run", "VirtualRun" -> "Carrera"
            "TrailRun" -> "Trail"
            "Swim" -> "Natación"
            "Walk" -> "Caminata"
            "Hike" -> "Senderismo"
            "WeightTraining", "Crossfit", "Workout", "HighIntensityIntervalTraining" -> "Fuerza"
            "Ride", "VirtualRide", "MountainBikeRide", "GravelRide", "EBikeRide" -> "Bici"
            else -> type
        }
    }

    fun authorizeUri(): Uri = Uri.parse("https://www.strava.com/oauth/mobile/authorize").buildUpon()
        .appendQueryParameter("client_id", prefs.stravaClientId)
        .appendQueryParameter("redirect_uri", REDIRECT)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("approval_prompt", "auto")
        .appendQueryParameter("scope", "activity:read_all")
        .build()

    private fun tokenRequest(form: FormBody): JSONObject {
        val req = Request.Builder().url("https://www.strava.com/oauth/token").post(form).build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Strava ${resp.code}: $body")
            return JSONObject(body)
        }
    }

    private fun saveTokens(o: JSONObject) {
        prefs.stravaAccess = o.getString("access_token")
        prefs.stravaRefresh = o.getString("refresh_token")
        prefs.stravaExpires = o.getLong("expires_at")
    }

    /** Intercambia el código que devuelve Strava por los tokens. */
    fun exchangeCode(code: String) {
        val o = tokenRequest(FormBody.Builder()
            .add("client_id", prefs.stravaClientId)
            .add("client_secret", prefs.stravaClientSecret)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .build())
        saveTokens(o)
        o.optJSONObject("athlete")?.let { a ->
            prefs.stravaAthlete = listOf(a.optString("firstname"), a.optString("lastname")).joinToString(" ").trim()
        }
    }

    private fun accessToken(): String {
        if (prefs.stravaRefresh.isBlank()) throw IOException("Strava no está conectado")
        if (Instant.now().epochSecond > prefs.stravaExpires - 120) {
            saveTokens(tokenRequest(FormBody.Builder()
                .add("client_id", prefs.stravaClientId)
                .add("client_secret", prefs.stravaClientSecret)
                .add("refresh_token", prefs.stravaRefresh)
                .add("grant_type", "refresh_token")
                .build()))
        }
        return prefs.stravaAccess
    }

    /** Descarga las actividades posteriores a [afterEpoch] (segundos). */
    fun activitiesSince(afterEpoch: Long): List<Activity> {
        val out = mutableListOf<Activity>()
        var page = 1
        while (page <= 10) {
            val req = Request.Builder()
                .url("https://www.strava.com/api/v3/athlete/activities?after=$afterEpoch&per_page=100&page=$page")
                .header("Authorization", "Bearer ${accessToken()}")
                .build()
            val arr = http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("Strava ${resp.code}: $body")
                JSONArray(body)
            }
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) out += parse(arr.getJSONObject(i))
            if (arr.length() < 100) break
            page++
        }
        return out
    }

    private fun parse(o: JSONObject): Activity {
        val distKm = o.optDouble("distance", 0.0) / 1000.0
        val movingMin = o.optDouble("moving_time", 0.0) / 60.0
        val sport = sportOf(o.optString("sport_type", o.optString("type")))
        val pace = if (distKm > 0.05 && sport != "Bici" && sport != "Natación") {
            val p = movingMin / distKm
            val m = p.toInt(); val s = ((p - m) * 60).roundToInt()
            if (s == 60) "${m + 1}:00" else "%d:%02d".format(m, s)
        } else ""
        return Activity(
            stravaId = o.optLong("id").toString(),
            date = LocalDate.parse(o.optString("start_date_local").take(10)),
            sport = sport,
            name = o.optString("name"),
            distanceKm = round2(distKm),
            durationMin = round2(movingMin),
            pace = pace,
            elevationM = o.optDouble("total_elevation_gain", 0.0).roundToInt().toDouble(),
            hrAvg = o.optDouble("average_heartrate").takeUnless { it.isNaN() }?.roundToInt()?.toDouble(),
            hrMax = o.optDouble("max_heartrate").takeUnless { it.isNaN() }?.roundToInt()?.toDouble(),
            rpe = null,
        )
    }

    private fun round2(d: Double) = (d * 100).roundToInt() / 100.0
}
