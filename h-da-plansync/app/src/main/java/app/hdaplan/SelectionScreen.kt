@file:OptIn(ExperimentalMaterial3Api::class)

package app.hdaplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun matches(q: String, s: Slot): Boolean {
    if (q.isBlank()) return true
    val needle = q.trim()
    val hay = listOf(s.title, Fmt.display(s, Model.aliases), s.lecturers, s.rooms, s.code)
    return hay.any { it.contains(needle, ignoreCase = true) }
}

/** Kurs-/Terminauswahl: pro Kurs ein Block, darunter die einzelnen Zeitfenster (wichtig bei Laborgruppen). */
@Composable
fun SelectionScreen(pad: PaddingValues, onGrantCalendar: () -> Unit, onGoSettings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var aliasFor by remember { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = Model.refreshing,
        onRefresh = { Model.refreshAll() },
        modifier = Modifier.padding(pad).fillMaxSize()
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (Model.needsSetup()) {
                item(key = "setup") { SetupCard(onGrantCalendar, onGoSettings) }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Suchen (Kurs, Dozent, Raum)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Leeren")
                            }
                        }
                    }
                )
            }
            for (ref in Model.plans) {
                val plan = Model.loaded[ref.id]
                item(key = "h-" + ref.id) { PlanHeader(plan, ref) }
                if (plan != null) {
                    val checked = Model.draftOf(ref.id)
                    val groups = plan.slots
                        .filter { matches(query, it) }
                        .groupBy { it.title }
                        .entries
                        .sortedWith(
                            compareBy<Map.Entry<String, List<Slot>>>(
                                { Fmt.baseName(it.key).lowercase() },
                                { it.key }
                            )
                        )
                    items(groups, key = { ref.id + "|" + it.key }) { g ->
                        GroupCard(ref.id, g.key, g.value, checked) { aliasFor = Fmt.baseName(g.key) }
                    }
                    if (groups.isEmpty()) {
                        item(key = "none-" + ref.id) {
                            Text(
                                "Nichts gefunden.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    val base = aliasFor
    if (base != null) AliasDialog(base) { aliasFor = null }
}

@Composable
private fun SetupCard(onGrant: () -> Unit, onGoSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Einrichtung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!Model.hasCalPerm) {
                Text("Erlaube den Kalender-Zugriff, damit die App Termine eintragen kann.")
                Button(onClick = onGrant) { Text("Zugriff erlauben") }
            } else {
                Text("Es wurde kein beschreibbarer Kalender gefunden. Lege in deiner Kalender-App einen an und öffne dann die Einstellungen.")
                Button(onClick = onGoSettings) { Text("Zu den Einstellungen") }
            }
        }
    }
}

@Composable
private fun PlanHeader(plan: Plan?, ref: PlanRef) {
    Column(Modifier.padding(top = 6.dp, start = 4.dp)) {
        Text(
            plan?.label ?: "Plan nicht geladen",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (plan != null) "Stand ${plan.stand} · ${plan.slots.size} Einträge" else ref.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GroupCard(
    planId: String,
    title: String,
    slots: List<Slot>,
    checked: Set<String>,
    onAlias: () -> Unit
) {
    val sorted = slots.sortedWith(compareBy<Slot>({ it.day }, { it.start }))
    val ids = sorted.map { it.id }
    val all = ids.all { checked.contains(it) }
    val type = Fmt.typeOf(title)
    val plan = Model.loaded[planId]
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = all, onCheckedChange = { Model.toggleGroup(planId, ids, it) })
                Column(Modifier.weight(1f)) {
                    Text(
                        Fmt.display(sorted.first(), Model.aliases),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeTag(type)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${sorted.size} Zeitfenster",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onAlias) {
                    Icon(Icons.Default.Edit, contentDescription = "Kürzel ändern")
                }
            }
            for (s in sorted) {
                val on = checked.contains(s.id)
                val mark = if (s.markers.isEmpty()) "" else " · Raster " + s.markers.joinToString(",")
                val count = plan?.datesFor(s)?.size ?: 0
                val who = listOf(s.lecturers, s.rooms).filter { it.isNotEmpty() }.joinToString(" · ")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Model.toggleSlot(planId, s.id, !on) }
                        .padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            Fmt.dayShort(s.day) + " " + s.start + "–" + s.end + mark + " · " + count + "×",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        if (who.isNotEmpty()) {
                            Text(who, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AliasDialog(base: String, onClose: () -> Unit) {
    var text by remember { mutableStateOf(Model.aliases[base] ?: "") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Kürzel für „$base“") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("z. B. Mathe") }
                )
                Text(
                    "Das Kürzel erscheint in der App, im Widget und im Kalender. Leer lassen für den Originalnamen.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Model.setAlias(base, text)
                onClose()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } }
    )
}
