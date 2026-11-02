package com.hugobou.hugotrain.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Autorización de Google para Sheets.
 * Usamos el permiso "drive.file": la app solo ve las hojas que ella misma ha creado,
 * que es justo lo que necesitamos y no requiere verificación de Google.
 */
object GoogleAuth {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    sealed interface Result {
        data class Token(val token: String) : Result
        data class NeedsUser(val intent: PendingIntent) : Result
    }

    suspend fun authorize(context: Context): Result {
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        val res = Identity.getAuthorizationClient(context).authorize(req).await()
        val pending = res.pendingIntent
        return if (res.hasResolution() && pending != null) Result.NeedsUser(pending)
        else Result.Token(res.accessToken ?: error("Google no devolvió token"))
    }

    fun tokenFromIntent(context: Context, data: Intent?): String? =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken

    /** Traduce los errores de Google a un mensaje que explique qué revisar. */
    fun explain(e: Throwable): String {
        val api = e as? ApiException ?: (e.cause as? ApiException)
        val code = api?.statusCode
        val hint = when (code) {
            10 -> "configuración de Google Cloud incorrecta. Revisa que el ID de cliente OAuth sea de tipo Android, " +
                "con paquete com.hugobou.hugotrain y SHA-1 33:C2:65:BD:B3:C4:83:C6:6A:39:4A:FB:6A:05:A4:29:5D:93:3D:4F"
            7 -> "sin conexión a internet"
            8 -> "error interno de Google Play Services; vuelve a intentarlo"
            16, 12501 -> "has cancelado la autorización"
            17 -> "Google Play Services no está disponible o está desactualizado"
            else -> null
        }
        return if (hint != null) "Google ($code): $hint" else "Google: ${e.message ?: e.javaClass.simpleName}"
    }
}
