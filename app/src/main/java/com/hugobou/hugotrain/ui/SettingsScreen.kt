package com.hugobou.hugotrain.ui

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val p = vm.prefs
    var sheetId by remember { mutableStateOf(p.spreadsheetId) }
    var clientId by remember { mutableStateOf(p.stravaClientId) }
    var secret by remember { mutableStateOf(p.stravaClientSecret) }
    var stravaOn by remember(vm.busy) { mutableStateOf(p.stravaConnected()) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Section("1. Cuenta de Google") {
            Text(if (vm.googleConnected) "Conectada ✓" else "Sin conectar")
            Button(onClick = { vm.connectGoogle() }) { Text(if (vm.googleConnected) "Volver a conectar" else "Conectar con Google") }
        }

        Section("2. Hoja de cálculo") {
            if (p.spreadsheetId.isBlank()) {
                Text("Crea la hoja desde aquí: la app solo tiene acceso a hojas creadas por ella misma.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.createSheet() }, enabled = !vm.busy) { Text("Crear hoja HugoTrain") }
            }
            OutlinedTextField(sheetId, { sheetId = it }, Modifier.fillMaxWidth(), label = { Text("ID de la hoja") }, singleLine = true)
            Button(onClick = { p.spreadsheetId = sheetId; vm.repairSheet() }, enabled = sheetId.isNotBlank() && !vm.busy) {
                Text("Guardar y revisar pestañas")
            }
            if (p.spreadsheetId.isNotBlank()) OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://docs.google.com/spreadsheets/d/${p.spreadsheetId}/edit")))
            }) { Text("Abrir hoja en Google Sheets") }
        }

        Section("3. Strava") {
            Text(if (stravaOn) "Conectado ✓ ${p.stravaAthlete}" else "Sin conectar")
            OutlinedTextField(clientId, { clientId = it }, Modifier.fillMaxWidth(), label = { Text("Client ID") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(secret, { secret = it }, Modifier.fillMaxWidth(), label = { Text("Client Secret") },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(onClick = {
                p.stravaClientId = clientId; p.stravaClientSecret = secret
                CustomTabsIntent.Builder().build().launchUrl(context, vm.repo.strava.authorizeUri())
            }, enabled = clientId.isNotBlank() && secret.isNotBlank()) { Text(if (stravaOn) "Reconectar Strava" else "Conectar Strava") }
            if (stravaOn) OutlinedButton(onClick = { p.disconnectStrava(); stravaOn = false }) { Text("Desconectar") }
        }

        Section("Sincronización") {
            Text("Series de fuerza pendientes de subir: ${vm.pending}")
            Button(onClick = { vm.refresh() }, enabled = !vm.busy) { Text("Sincronizar ahora") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
