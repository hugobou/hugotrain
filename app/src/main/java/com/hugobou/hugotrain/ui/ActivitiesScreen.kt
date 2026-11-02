package com.hugobou.hugotrain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ActivitiesScreen(vm: MainViewModel) {
    val list = vm.activities.sortedByDescending { it.date }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Actividades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Button(onClick = { vm.syncStrava() }, enabled = vm.prefs.stravaConnected() && !vm.busy) {
                    Icon(Icons.Default.Sync, null); Spacer(Modifier.width(6.dp)); Text("Strava")
                }
            }
            if (!vm.prefs.stravaConnected())
                Text("Conecta Strava en Ajustes para importar las actividades de tu Garmin.",
                    style = MaterialTheme.typography.bodySmall)
        }
        if (list.isEmpty()) item { Text("Todavía no hay actividades.", Modifier.padding(8.dp)) }
        items(list, key = { it.stravaId + it.date }) { a ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(a.sport); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name.ifBlank { a.sport }, fontWeight = FontWeight.SemiBold)
                        Text("${a.date} · ${a.sport}", style = MaterialTheme.typography.labelSmall)
                        Text(activityLine(a), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
