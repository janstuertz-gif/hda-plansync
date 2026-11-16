@file:OptIn(ExperimentalMaterial3Api::class)

package app.hdaplan

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.IsoFields

private fun initialWeekStart(): LocalDate {
    val t = LocalDate.now()
    val v = t.dayOfWeek.value
    // Am Wochenende gleich die kommende Woche zeigen
    return if (v >= 6) t.plusDays((8 - v).toLong()) else t.minusDays((v - 1).toLong())
}

private class Placed(val occ: Occ, val col: Int, val cols: Int)

/** Überlappende Termine nebeneinander anordnen. */
private fun layoutDay(list: List<Occ>): List<Placed> {
    val sorted = list.sortedBy { it.slot.start }
    val out = ArrayList<Placed>()
    var cluster = ArrayList<Occ>()
    var clusterEnd = LocalTime.MIN

    fun flush() {
        if (cluster.isEmpty()) return
        val colEnds = ArrayList<LocalTime>()
        val assigned = ArrayList<Pair<Occ, Int>>()
        for (o in cluster) {
            var c = colEnds.indexOfFirst { !it.isAfter(o.slot.start) }
            if (c < 0) {
                colEnds.add(o.slot.end)
                c = colEnds.size - 1
            } else {
                colEnds[c] = o.slot.end
            }
            assigned.add(Pair(o, c))
        }
        for ((o, c) in assigned) out.add(Placed(o, c, colEnds.size))
        cluster = ArrayList()
    }

    for (o in sorted) {
        if (cluster.isNotEmpty() && !o.slot.start.isBefore(clusterEnd)) flush()
        if (cluster.isEmpty()) {
            clusterEnd = o.slot.end
        } else if (o.slot.end.isAfter(clusterEnd)) {
            clusterEnd = o.slot.end
        }
        cluster.add(o)
    }
    flush()
    return out
}

/** Wochenansicht: eigener, immer aktueller Stundenplan nur mit deinen Terminen – oder die Original-Seite. */
@Composable
fun WeekScreen(pad: PaddingValues, onOpen: (Occ) -> Unit) {
    var web by remember { mutableStateOf(false) }
    var webIdx by remember { mutableStateOf(0) }
    var start by remember { mutableStateOf(initialWeekStart()) }
    val uri = LocalUriHandler.current

    Column(Modifier.padding(pad).fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = !web, onClick = { web = false }, label = { Text("Meine Woche") })
            FilterChip(selected = web, onClick = { web = true }, label = { Text("Original-Plan") })
        }
        if (!web) {
            WeekHeader(
                start,
                onPrev = { start = start.minusWeeks(1) },
                onNext = { start = start.plusWeeks(1) },
                onToday = { start = initialWeekStart() }
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(start) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total > 120f) {
                                    start = start.minusWeeks(1)
                                } else if (total < -120f) {
                                    start = start.plusWeeks(1)
                                }
                            },
                            onDragCancel = { total = 0f },
                            onHorizontalDrag = { _, dx -> total += dx }
                        )
                    }
            ) {
                WeekGrid(start, Model.occs, onOpen)
                if (Model.occs.isEmpty()) {
                    Text(
                        "Noch keine Kurse ausgewählt. Das erledigst du unter „Auswahl“.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            val refs = Model.plans
            if (refs.size > 1) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((i, r) in refs.withIndex()) {
                        val label = Model.loaded[r.id]?.title ?: r.id
                        FilterChip(selected = webIdx == i, onClick = { webIdx = i }, label = { Text(label) })
                    }
                }
            }
            val ref = refs.getOrNull(webIdx) ?: refs.firstOrNull()
            if (ref != null) {
                TextButton(
                    onClick = { uri.openUri(ref.url) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text("Im Browser öffnen") }
                WebPlan(ref.url, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun WeekHeader(start: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val end = start.plusDays(4)
    val kw = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vorherige Woche")
        }
        Column(
            Modifier.weight(1f).clickable(onClick = onToday),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("KW $kw", fontWeight = FontWeight.Bold)
            Text(
                start.format(DM) + " – " + end.format(DM) + end.year,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Nächste Woche")
        }
    }
}

@Composable
private fun WeekGrid(start: LocalDate, all: List<Occ>, onOpen: (Occ) -> Unit) {
    val end = start.plusDays(6)
    val week = all.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    val days = (0..5).map { start.plusDays(it.toLong()) }
        .filter { d -> d.dayOfWeek != DayOfWeek.SATURDAY || week.any { it.date == d } }
    val minMin = minOf(8 * 60, week.minOfOrNull { it.slot.start.hour * 60 + it.slot.start.minute } ?: (8 * 60))
    val maxMin = maxOf(18 * 60, week.maxOfOrNull { it.slot.end.hour * 60 + it.slot.end.minute } ?: (18 * 60))
    val startH = minMin / 60
    val endH = (maxMin + 59) / 60
    val hourH = 56.dp
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val today = LocalDate.now()

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // Kopfzeile mit den Wochentagen
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(32.dp))
            for (d in days) {
                val isToday = d == today
                Column(
                    Modifier.weight(1f).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        Fmt.dayShort(d.dayOfWeek),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        d.dayOfMonth.toString(),
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        // Raster mit Terminblöcken
        Row(Modifier.fillMaxWidth().height(hourH * (endH - startH))) {
            Column(Modifier.width(32.dp)) {
                for (h in startH until endH) {
                    Box(Modifier.height(hourH)) {
                        Text(
                            h.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            for (d in days) {
                val list = week.filter { it.date == d }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            val hp = hourH.toPx()
                            for (i in 0..(endH - startH)) {
                                drawLine(lineColor, Offset(0f, hp * i), Offset(size.width, hp * i), 1f)
                            }
                        }
                ) {
                    val w = maxWidth
                    for (p in layoutDay(list)) {
                        val s = p.occ.slot
                        val topMin = s.start.hour * 60 + s.start.minute - startH * 60
                        val durMin = maxOf(30, (s.end.hour * 60 + s.end.minute) - (s.start.hour * 60 + s.start.minute))
                        EventBlock(
                            p.occ,
                            Modifier
                                .offset(x = w * (p.col.toFloat() / p.cols), y = hourH * (topMin / 60f))
                                .width(w / p.cols)
                                .height(hourH * (durMin / 60f)),
                            onOpen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventBlock(occ: Occ, modifier: Modifier, onOpen: (Occ) -> Unit) {
    val s = occ.slot
    val tc = typeColors(s.type)
    Box(
        modifier
            .alpha(if (occ.hidden) 0.4f else 1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tc.container)
            .clickable { onOpen(occ) }
            .padding(3.dp)
    ) {
        Column {
            Text(
                Fmt.display(s, Model.aliases),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tc.onContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (s.rooms.isNotEmpty()) {
                Text(
                    s.rooms,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = tc.onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WebPlan(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = WebViewClient()
                tag = url
                loadUrl(url)
            }
        },
        update = { w ->
            if (w.tag != url) {
                w.tag = url
                w.loadUrl(url)
            }
        }
    )
}
