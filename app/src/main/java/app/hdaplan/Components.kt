@file:OptIn(ExperimentalMaterial3Api::class)

package app.hdaplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")
val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Kleines farbiges Etikett: Vorlesung / Übung / Labor ... */
@Composable
fun TypeTag(type: Char, modifier: Modifier = Modifier) {
    val tc = typeColors(type)
    Surface(shape = RoundedCornerShape(6.dp), color = tc.accent, modifier = modifier) {
        Text(
            Fmt.typeName(type),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tc.onAccent
        )
    }
}

/** Karte für einen konkreten Termin (Heute-Ansicht). */
@Composable
fun OccCard(occ: Occ, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = occ.slot
    val tc = typeColors(s.type)
    Card(
        modifier = modifier.fillMaxWidth().alpha(if (occ.hidden) 0.5f else 1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tc.container, contentColor = tc.onContainer)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(54.dp)) {
                Text(s.start.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(s.end.toString(), fontSize = 12.sp, color = tc.onContainer.copy(alpha = 0.7f))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    Fmt.display(s, Model.aliases),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeTag(s.type)
                    if (s.rooms.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(s.rooms, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (s.lecturers.isNotEmpty()) {
                    Text(
                        s.lecturers,
                        fontSize = 12.sp,
                        color = tc.onContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (occ.hidden) {
                    Text("ausgeblendet", fontSize = 12.sp, fontStyle = FontStyle.Italic)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Details zu einem Termin, mit "Ausblenden" (entfernt nur diesen einen Termin aus dem Kalender). */
@Composable
fun OccSheet(occ: Occ, onClose: () -> Unit) {
    val s = occ.slot
    val plan = Model.loaded[occ.planId]
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TypeTag(s.type)
            Spacer(Modifier.height(6.dp))
            Text(Fmt.display(s, Model.aliases), style = MaterialTheme.typography.headlineSmall)
            Text(
                s.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            InfoRow("Wann", Fmt.dayShort(occ.date.dayOfWeek) + ", " + occ.date.format(DMY) + " · " + s.start + "–" + s.end)
            if (s.rooms.isNotEmpty()) InfoRow("Raum", s.rooms)
            if (s.lecturers.isNotEmpty()) InfoRow("Dozent", s.lecturers)
            if (s.code.isNotEmpty()) InfoRow("Modul", s.code)
            if (s.markers.isNotEmpty()) {
                InfoRow("Raster", s.markers.joinToString("/") + "-Wochen – konkrete Termine legt der Dozent fest")
            }
            if (plan != null) InfoRow("Plan", plan.label)
            Spacer(Modifier.height(16.dp))
            if (occ.hidden) {
                Text(
                    "Dieser Termin ist ausgeblendet und steht nicht im Kalender.",
                    style = MaterialTheme.typography.bodySmall
                )
                FilledTonalButton(onClick = {
                    Model.setHidden(occ.key, false)
                    onClose()
                }) { Text("Wieder einblenden") }
            } else {
                FilledTonalButton(onClick = {
                    Model.setHidden(occ.key, true)
                    onClose()
                }) { Text("Diesen Termin ausblenden") }
                Text(
                    "Nur dieser Tag wird aus dem Kalender entfernt und kommt beim Sync nicht wieder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
