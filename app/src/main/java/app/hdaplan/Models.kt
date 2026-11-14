package app.hdaplan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Ein Eintrag im Wochenraster, z. B. "Do 08:30-10:00 · V: Mathematik 1". */
data class Slot(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val lecturers: String,
    val rooms: String,
    val markers: List<String>,
    val code: String,
    val order: Int
) {
    /** Bleibt gleich, wenn sich nur Raum oder Dozent ändern. */
    val baseKey: String
        get() = "${day.value}|$start|$end|$title|${markers.joinToString(",")}"
}

/** Gemerkte Auswahl: damit findet die App einen Termin nach einer Plan-Änderung wieder. */
data class Sel(val key: String, val lecturers: String, val rooms: String, val order: Int)

class Plan(
    val title: String,
    val stand: String,
    val slots: List<Slot>,
    val raster: Map<String, Set<LocalDate>>
) {
    /** Alle Kalendertage, an denen dieser Eintrag stattfinden kann (aus dem x/y-Raster). */
    fun datesFor(s: Slot): List<LocalDate> {
        // Ohne x/y-Angabe stehen laut Seite beide Raster zur Verfügung = jede Vorlesungswoche.
        val labels = if (s.markers.isEmpty()) listOf("x", "y") else s.markers
        return labels
            .flatMap { raster[it] ?: emptySet<LocalDate>() }
            .filter { it.dayOfWeek == s.day }
            .distinct()
            .sorted()
    }
}

object Matcher {
    /** Ordnet die gemerkte Auswahl den aktuellen Einträgen zu. Rückgabe: (gefunden, nicht mehr vorhanden). */
    fun match(sel: List<Sel>, slots: List<Slot>): Pair<List<Slot>, List<Sel>> {
        val free = HashMap<String, MutableList<Slot>>()
        for (s in slots) {
            free.getOrPut(s.baseKey) { ArrayList() }.add(s)
        }
        val hit = ArrayList<Slot>()
        val miss = ArrayList<Sel>()
        for (s in sel) {
            val cands = free[s.key]
            if (cands == null || cands.isEmpty()) {
                miss.add(s)
                continue
            }
            // Bei mehreren Gruppen im selben Zeitfenster: ähnlichsten Eintrag nehmen.
            val best = cands.maxByOrNull {
                (if (it.lecturers == s.lecturers) 4 else 0) +
                    (if (it.rooms == s.rooms) 2 else 0) +
                    (if (it.order == s.order) 1 else 0)
            }!!
            cands.remove(best)
            hit.add(best)
        }
        return Pair(hit, miss)
    }
}
