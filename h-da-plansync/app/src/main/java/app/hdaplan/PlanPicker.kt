@file:OptIn(ExperimentalMaterial3Api::class)

package app.hdaplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PickerReq(val replaceId: String?)

/** Plan wählen: Liste der erkennbaren Pläne (sortiert nach Studiengang und Semester) oder Link einfügen. */
@Composable
fun PlanPickerSheet(replaceId: String?, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(0) }
    var link by remember { mutableStateOf("") }
    var links by remember { mutableStateOf<List<PlanLink>?>(null) }
    val base = Model.plans.firstOrNull()?.url ?: Store.DEFAULT_URL

    LaunchedEffect(Unit) {
        links = withContext(Dispatchers.IO) {
            try {
                Parser.loadIndex(base)
            } catch (e: Exception) {
                emptyList<PlanLink>()
            }
        }
    }

    fun pick(url: String) {
        if (replaceId == null) Model.addPlan(url) else Model.replacePlan(replaceId, url)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                if (replaceId == null) "Plan hinzufügen" else "Plan ändern (z. B. neues Semester)",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Aus Liste") })
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Link einfügen") })
            }
            Spacer(Modifier.height(8.dp))
            if (mode == 0) {
                val l = links
                if (l == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Lade Übersicht …")
                    }
                } else if (l.isEmpty()) {
                    Text(
                        "Die Übersicht konnte nicht gelesen werden. Nutze „Link einfügen“.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val groups = l.groupBy { it.group }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        for ((name, entries) in groups) {
                            item {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(entries) { pl ->
                                Text(
                                    pl.semester.toString() + ". Semester · " + pl.variant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { pick(pl.url) }
                                        .padding(vertical = 12.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Link zum Stundenplan") },
                    placeholder = { Text("https://splan.eit.h-da.de/s/…") }
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { pick(link.trim()) }, enabled = link.trim().startsWith("http")) {
                    Text("Übernehmen")
                }
            }
        }
    }
}
