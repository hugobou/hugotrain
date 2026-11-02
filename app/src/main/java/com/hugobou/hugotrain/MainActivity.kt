package com.hugobou.hugotrain

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.hugobou.hugotrain.data.GoogleAuth
import com.hugobou.hugotrain.ui.ActivitiesScreen
import com.hugobou.hugotrain.ui.HugoTheme
import com.hugobou.hugotrain.ui.MainViewModel
import com.hugobou.hugotrain.ui.SettingsScreen
import com.hugobou.hugotrain.ui.StrengthScreen
import com.hugobou.hugotrain.ui.WeekScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val r = runCatching { GoogleAuth.tokenFromIntent(this, res.data) }
        vm.onGoogleConsent(r.getOrNull(), r.exceptionOrNull()?.let { GoogleAuth.explain(it) }
            ?: if (res.resultCode != RESULT_OK) "Google: autorización cancelada o rechazada (código ${res.resultCode})" else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            HugoTheme {
                val snackbar = remember { SnackbarHostState() }
                var tab by rememberSaveable { mutableIntStateOf(if (vm.session.isNotEmpty()) 1 else 0) }

                // Mensajes cortos como aviso abajo; los largos (errores) en un diálogo para poder leerlos enteros.
                val msg = vm.message
                LaunchedEffect(msg) {
                    if (msg != null && msg.length <= 60) {
                        vm.message = null
                        snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
                    }
                }
                if (msg != null && msg.length > 60) {
                    AlertDialog(
                        onDismissRequest = { vm.message = null },
                        title = { Text("Aviso") },
                        text = { Text(msg) },
                        confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK") } },
                    )
                }
                LaunchedEffect(vm.googleConsent) {
                    vm.googleConsent?.let {
                        consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                    }
                }

                val tabs = listOf(
                    Triple("Semana", Icons.Default.CalendarMonth, 0),
                    Triple("Fuerza", Icons.Default.FitnessCenter, 1),
                    Triple("Actividades", Icons.Default.DirectionsRun, 2),
                    Triple("Ajustes", Icons.Default.Settings, 3),
                )
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEach { (label, icon: ImageVector, i) ->
                                NavigationBarItem(
                                    selected = tab == i, onClick = { tab = i },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) })
                            }
                        }
                    },
                ) { pad ->
                    Column(Modifier.padding(pad).fillMaxSize()) {
                        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Box(Modifier.fillMaxSize()) {
                            when (tab) {
                                0 -> WeekScreen(vm)
                                1 -> StrengthScreen(vm)
                                2 -> ActivitiesScreen(vm)
                                else -> SettingsScreen(vm)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Vuelta del navegador tras autorizar Strava. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri = intent.data ?: return
        if (uri.scheme != "hugotrain") return
        val code = uri.getQueryParameter("code")
        if (code != null) vm.stravaCode(code)
        else vm.message = "Strava: autorización cancelada (${uri.getQueryParameter("error") ?: "sin código"})"
        intent.data = null
    }
}
