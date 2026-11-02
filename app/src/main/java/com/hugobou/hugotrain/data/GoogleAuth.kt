package com.hugobou.hugotrain.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
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
}
