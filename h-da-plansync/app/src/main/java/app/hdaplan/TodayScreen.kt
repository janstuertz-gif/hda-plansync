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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private sealed interface TRow
private class THeader(val text: String) : TRow
private class TEvent(val occ: Occ) : TRow
private class TGap(val text: String) : TRow

private fun dur(min: Long): String = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

private fun dayLabel(d: LocalDate, today: LocalDate): String {
    val base = Fmt.dayShort(d.dayOfWeek) + ", " + d.format(DM)
    return when {
        d == today -> "Heute · $base"
        d == today.plusDays(1) -> "Morgen · $base"
        else -> base
    }
}

private fun countdown(o: Occ, now: LocalDateTime): String {
    val toStart = Duration.between(now, o.start).toMinutes()
    val toEnd = Duration.between(now, o.end).toMinutes()
    return when {
        toStart <= 0 && toEnd > 0 -> "läuft · noch " + dur(toEnd)
        o.date == now.toLocalDate() && toStart < 1 -> "gleich"
        o.date == now.toLocalDate() -> "in " + dur(toStart)
        o.date == now.toLocalDate().plusDays(1) -> "morgen um " + o.slot.start
        else -> Fmt.dayShort(o.date.dayOfWeek) + ", " + o.date.format(DM) + " um " + o.slot.start
    }
}

/** Startseite: nächste Veranstaltung mit Countdown, dann die kommenden Tage mit Pausen. */
@Composable
fun TodayScreen(pad: PaddingValues, onOpen: (Occ) -> Unit, onGoSelection: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    val today = now.toLocalDate()
    val upcoming = Model.occs.filter { !it.hidden && it.end.isAfter(now) && it.date.isBefore(today.plusDays(15)) }

    val rows = ArrayList<TRow>()
    var lastDate: LocalDate? = null
    var lastEnd: LocalDateTime? = null
    for (o in upcoming) {
        if (o.date != lastDate) {
            rows.add(THeader(dayLabel(o.date, today)))
            lastDate = o.date
            lastEnd = null
        }
        val prevEnd = lastEnd
        if (prevEnd != null) {
            val gap = Duration.between(prevEnd, o.start).toMinutes()
            if (gap >= 20) rows.add(TGap("Pause · " + dur(gap)))
        }
        rows.add(TEvent(o))
        if (prevEnd == null || o.end.isAfter(prevEnd)) lastEnd = o.end
    }
    val next = upcoming.firstOrNull()

    PullToRefreshBox(
        isRefreshing = Model.refreshing,
        onRefresh = { Model.refreshAll() },
        modifier = Modifier.padding(pad).fillMaxSize()
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (rows.isEmpty()) {
                item { EmptyToday(onGoSelection) }
            }
            items(rows) { row ->
                when (row) {
                    is THeader -> Text(
                        row.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    is TGap -> Text(
                        row.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    is TEvent -> if (row.occ == next) {
                        NextCard(row.occ, now) { onOpen(row.occ) }
                    } else {
                        OccCard(row.occ, onClick = { onOpen(row.occ) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NextCard(occ: Occ, now: LocalDateTime, onClick: () -> Unit) {
    val s = occ.slot
    val tc = typeColors(s.type)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tc.container, contentColor = tc.onContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Als Nächstes",
                style = MaterialTheme.typography.labelLarge,
                color = tc.onContainer.copy(alpha = 0.7f)
            )
            Text(
                countdown(occ, now),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                Fmt.display(s, Model.aliases),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeTag(s.type)
                Spacer(Modifier.width(8.dp))
                Text(s.start.toString() + "–" + s.end)
            }
            if (s.rooms.isNotEmpty()) Text("Raum: " + s.rooms)
            if (s.lecturers.isNotEmpty()) Text(s.lecturers)
        }
    }
}

@Composable
private fun EmptyToday(onGoSelection: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Model.occs.isEmpty()) {
                Text("Noch nichts eingerichtet", style = MaterialTheme.typography.titleMedium)
                Text("Wähle unter „Auswahl“ deine Kurse aus. Dann erscheinen sie hier und im Kalender.")
                Button(onClick = onGoSelection) { Text("Zur Auswahl") }
            } else {
                Text("Keine Termine in den nächsten zwei Wochen", style = MaterialTheme.typography.titleMedium)
                Text("Unter „Woche“ siehst du auch vergangene und spätere Wochen.")
            }
        }
    }
}
