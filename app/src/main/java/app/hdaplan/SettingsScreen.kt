@file:OptIn(ExperimentalMaterial3Api::class)

package app.hdaplan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun fmtTime(ms: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SettingsScreen(
    pad: PaddingValues,
    onGrantCalendar: () -> Unit,
    onEnableNotif: () -> Unit,
    onAddPlan: () -> Unit,
    onReplacePlan: (String) -> Unit
) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf("") }
    var removeId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Pläne") {
            for (ref in Model.plans) {
                val p = Model.loaded[ref.id]
                Column {
                    Text(p?.label ?: ref.url, fontWeight = FontWeight.SemiBold)
                    Small(if (p != null) "Stand ${p.stand} · ${p.slots.size} Einträge" else "Noch nicht geladen")
                    Row {
                        TextButton(onClick = { onReplacePlan(ref.id) }) { Text("Ändern") }
                        if (Model.plans.size > 1) {
                            TextButton(onClick = { removeId = ref.id }) { Text("Entfernen") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onAddPlan) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Plan hinzufügen")
            }
            Small("Neues Semester? Tippe bei „Ändern“ auf den neuen Plan und kreuze danach deine Kurse neu an.")
        }

        SectionCard("Kalender") {
            if (!Model.hasCalPerm) {
                Small("Ohne Zugriff kann die App keine Termine eintragen.")
                Button(onClick = onGrantCalendar) { Text("Zugriff erlauben") }
            } else {
                Text(Model.calendarName())
                OutlinedButton(onClick = { dlg = "cal" }) { Text("Kalender wählen") }
                Small("Tipp: Lege im Google Kalender einen eigenen Kalender „Uni“ an und wähle ihn hier. So kannst du ihn ein- und ausblenden.")
            }
        }

        SectionCard("Termin-Titel im Kalender") {
            val opts = listOf(
                Pair("Name (Typ)", "z. B. Mathe (V)"),
                Pair("Nur Name", "z. B. Mathe"),
                Pair("Original", "z. B. V: Mathematik 1")
            )
            for ((i, o) in opts.withIndex()) {
                Row(
                    Modifier.fillMaxWidth().clickable { Model.setFormat(i) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = Model.titleFormat == i, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(o.first)
                        Small(o.second)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = Model.emoji, onCheckedChange = { Model.updateEmoji(it) })
                Spacer(Modifier.width(12.dp))
                Text("Emoji je Typ voranstellen")
            }
            Small("Eigene Kürzel pro Kurs stellst du unter „Auswahl“ mit dem Stift ein.")
        }

        SectionCard("Synchronisation") {
            Text("Automatisch " + (if (Model.intervalHours == 1) "jede Stunde" else "alle ${Model.intervalHours} Stunden"))
            OutlinedButton(onClick = { dlg = "interval" }) { Text("Intervall ändern") }
            Button(onClick = { Model.syncNow() }, enabled = !Model.refreshing) { Text("Jetzt synchronisieren") }
            if (Model.status.isNotEmpty()) Small(Model.status)
            if (Model.lastOk > 0) Small("Letzte erfolgreiche Synchronisation: " + fmtTime(Model.lastOk))
        }

        SectionCard("Benachrichtigungen") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bei Planänderungen melden")
                    Small("Raum, Zeit oder Dozent geändert, Termin neu oder entfallen")
                }
                Switch(
                    checked = Model.notify,
                    onCheckedChange = { on -> if (on) onEnableNotif() else Model.updateNotify(false) }
                )
            }
        }

        SectionCard("Änderungsprotokoll") {
            if (Model.log.isEmpty()) {
                Small("Noch keine Änderungen.")
            } else {
                for (e in Model.log.take(15)) {
                    Column {
                        Text(
                            fmtTime(e.time),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        for (l in e.lines) Text("• " + l, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { Model.clearLog() }) { Text("Protokoll leeren") }
            }
        }

        SectionCard("Hilfe und Aufräumen") {
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Diagnose", Model.diagnostics()))
                Model.message = "Diagnose kopiert."
            }) { Text("Diagnose kopieren") }
            Small("Kopiert Fehlerinfos in die Zwischenablage. Schick sie weiter, wenn etwas nicht klappt.")
            OutlinedButton(
                onClick = { dlg = "cleanup" },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Alle App-Termine löschen") }
            Small("h-da Plan 2.0")
        }
    }

    if (dlg == "cal") {
        AlertDialog(
            onDismissRequest = { dlg = "" },
            title = { Text("Kalender wählen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (c in Model.calendars) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Model.setCalendar(c.id)
                                    dlg = ""
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = c.id == Model.calendarId, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(c.name)
                                Small(c.account)
                            }
                        }
                    }
                    Small("Beim Wechsel werden die App-Termine im bisherigen Kalender entfernt und im neuen angelegt.")
                }
            },
            confirmButton = { TextButton(onClick = { dlg = "" }) { Text("Schließen") } }
        )
    }

    if (dlg == "interval") {
        AlertDialog(
            onDismissRequest = { dlg = "" },
            title = { Text("Wie oft prüfen?") },
            text = {
                Column {
                    for (h in listOf(1, 3, 6, 12, 24)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Model.setInterval(h)
                                    dlg = ""
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = Model.intervalHours == h, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (h == 1) "Jede Stunde" else "Alle $h Stunden")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dlg = "" }) { Text("Schließen") } }
        )
    }

    if (dlg == "cleanup") {
        AlertDialog(
            onDismissRequest = { dlg = "" },
            title = { Text("Alle App-Termine löschen?") },
            text = { Text("Entfernt alle von dieser App angelegten Termine aus dem Kalender und stoppt die automatische Synchronisation. Deine Auswahl bleibt gespeichert.") },
            confirmButton = {
                TextButton(onClick = {
                    Model.cleanup()
                    dlg = ""
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { dlg = "" }) { Text("Abbrechen") } }
        )
    }

    val rid = removeId
    if (rid != null) {
        AlertDialog(
            onDismissRequest = { removeId = null },
            title = { Text("Plan entfernen?") },
            text = { Text("Die Termine dieses Plans werden aus dem Kalender entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    Model.removePlan(rid)
                    removeId = null
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text("Abbrechen") } }
        )
    }
}
