package app.hdaplan

import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
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

    /** Eindeutig innerhalb eines Plans. */
    val id: String
        get() = "$baseKey#$order"

    /** V, Ü, L, S, Z, P oder ? */
    val type: Char
        get() = Fmt.typeOf(title)
}

/** Gemerkte Auswahl: damit findet die App einen Termin nach einer Plan-Änderung wieder. */
data class Sel(val key: String, val lecturers: String, val rooms: String, val order: Int)

data class PlanRef(val id: String, val url: String)

data class LogEntry(val time: Long, val lines: List<String>)

class Plan(
    val id: String,
    val url: String,
    val title: String,
    val term: String,
    val stand: String,
    val slots: List<Slot>,
    val raster: Map<String, Set<LocalDate>>,
    val fetchedAt: Long
) {
    val label: String
        get() = if (term.isEmpty()) title else "$title · $term"

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

/** Ein konkreter Termin an einem Kalendertag. */
data class Occ(
    val key: String,
    val planId: String,
    val slot: Slot,
    val date: LocalDate,
    val hidden: Boolean
) {
    val start: LocalDateTime
        get() = date.atTime(slot.start)
    val end: LocalDateTime
        get() = date.atTime(slot.end)
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

object Occurrences {
    const val FIRST_PLAN = "p1"

    fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)

    /** Schlüssel für die Markierung im Kalender. Für den ersten Plan identisch zur Version 1. */
    fun keyOf(planId: String, s: Slot, d: LocalDate): String =
        hash((if (planId == FIRST_PLAN) "" else "$planId:") + "${s.baseKey}#${s.order}@$d")

    /** Alle Termine der gewählten Einträge (auch vergangene; ausgeblendete sind markiert). */
    fun build(
        refs: List<PlanRef>,
        plans: Map<String, Plan>,
        sel: Map<String, List<Sel>>,
        hidden: Set<String>
    ): List<Occ> {
        val out = ArrayList<Occ>()
        for (r in refs) {
            val p = plans[r.id] ?: continue
            val hit = Matcher.match(sel[r.id] ?: emptyList(), p.slots).first
            for (s in hit) {
                for (d in p.datesFor(s)) {
                    val k = keyOf(p.id, s, d)
                    out.add(Occ(k, p.id, s, d, hidden.contains(k)))
                }
            }
        }
        out.sortWith(compareBy<Occ>({ it.date }, { it.slot.start }, { it.slot.title }))
        return out
    }
}

object Fmt {
    private val SUFFIX = Regex("\\s*[-–]\\s*(Übung|Labor|Praktikum|Seminar|Tutorium)\\b.*$", RegexOption.IGNORE_CASE)
    private val DAYS = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    fun dayShort(d: DayOfWeek): String = DAYS[d.value - 1]

    fun typeOf(title: String): Char {
        val t = title.substringBefore(':', "").trim().uppercase()
        return if (t.length == 1 && "VÜLSZP".indexOf(t[0]) >= 0) t[0] else '?'
    }

    fun typeName(c: Char): String = when (c) {
        'V' -> "Vorlesung"
        'Ü' -> "Übung"
        'L' -> "Labor"
        'S' -> "Seminar"
        'Z' -> "Zusatz"
        'P' -> "Projekt"
        else -> "Sonstiges"
    }

    fun emoji(c: Char): String = when (c) {
        'V' -> "🎓"
        'Ü' -> "✏️"
        'L' -> "🔬"
        'S' -> "💬"
        'Z' -> "➕"
        'P' -> "🛠️"
        else -> "📌"
    }

    /** Kursname ohne Typ-Präfix und ohne " - Übung" / " - Labor" am Ende. */
    fun baseName(title: String): String {
        val n = title.substringAfter(':', title).trim()
        val b = n.replace(SUFFIX, "").trim()
        return if (b.isEmpty()) n else b
    }

    /** Name in der App: eigenes Kürzel, sonst der bereinigte Kursname. */
    fun display(s: Slot, aliases: Map<String, String>): String {
        val b = baseName(s.title)
        val a = aliases[b]
        return if (a != null && a.isNotBlank()) a else b
    }

    /** Titel im Kalender. fmt: 0 = "Name (Typ)", 1 = nur Name, 2 = Original. */
    fun calTitle(s: Slot, aliases: Map<String, String>, fmt: Int, withEmoji: Boolean): String {
        val core = when (fmt) {
            2 -> s.title
            1 -> display(s, aliases)
            else -> {
                val n = display(s, aliases)
                if (s.type == '?') n else "$n (${s.type})"
            }
        }
        return if (withEmoji) emoji(s.type) + " " + core else core
    }
}
