package app.hdaplan

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Ein Plan aus der Übersicht, z. B. "EIT (BA)", 1. Semester, Variante "EIT". */
data class PlanLink(val url: String, val group: String, val semester: Int, val variant: String)

/**
 * Liest die Wochenraster-Tabelle des Stundenplans und die verlinkte x/y-Raster-Seite,
 * die für jeden Wochentag die echten Kalendertage (x, y, x1, x2, y1, y2) auflistet.
 */
object Parser {
    private val DAYS = mapOf(
        "montag" to DayOfWeek.MONDAY, "dienstag" to DayOfWeek.TUESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY, "freitag" to DayOfWeek.FRIDAY, "samstag" to DayOfWeek.SATURDAY
    )
    private val TIME = Regex("(\\d{1,2}):(\\d{2})\\D+(\\d{1,2}):(\\d{2})")
    private val ROOM = Regex("\\b[A-Z]\\d{1,2}\\s*/\\s*\\d{1,2}\\.\\d{1,3}\\b")
    private val MARK = Regex("^([xy][12]?(?:\\s*,\\s*[xy][12]?)*)(?=\\s|$)")
    private val LABEL = Regex("[xy][12]?")
    private val WS = Regex("\\s+")
    private val PLAN_FILE = Regex("^S_([A-Za-z0-9]+)_(BA|MA)_(\\d+)(.*)\\.htm$", RegexOption.IGNORE_CASE)

    private class Cell(val el: Element, val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int)
    private class Ln(val text: String, val bold: String)
    private class E(
        val title: String,
        val lecturers: String,
        val rooms: String,
        val markers: List<String>,
        val code: String
    )

    private fun fetch(url: String): Document =
        Jsoup.connect(url).userAgent("Mozilla/5.0 (Android) h-da-plansync").timeout(20000).get()

    /** Lädt Plan und zugehörige Raster-Seite. Wirft IOException bei Netzwerkproblemen. */
    fun load(id: String, url: String): Plan {
        val doc = fetch(url)
        val slots = parseSlots(doc)
        val rasterUrl = doc.select("a[href*=Raster]").firstOrNull()?.absUrl("href")?.substringBefore('#') ?: ""
        val raster: Map<String, Set<LocalDate>> =
            if (rasterUrl.isEmpty()) emptyMap() else parseRaster(fetch(rasterUrl))
        val text = doc.text()
        val stand = Regex("Aktualisierungsdatum\\D{0,6}(\\d{2}\\.\\d{2}\\.\\d{4})")
            .find(text)?.groupValues?.get(1) ?: "?"
        val term = Regex("Stundenplan\\s+((?:WS|SS|WiSe|SoSe)\\s*\\d{2}(?:/\\d{2})?)")
            .find(text)?.groupValues?.get(1) ?: ""
        val title = doc.title().ifBlank { url.substringAfterLast('/') }
        return Plan(id, url, title, term, stand, slots, raster, System.currentTimeMillis())
    }

    // ---------- Plan-Übersicht (nur eindeutig erkennbare Pläne, sortiert) ----------

    /** Sucht auf der Startseite (und ein paar Ausweichseiten) nach Plan-Links wie S_EIT_BA_1EIT.htm. */
    fun loadIndex(anyPlanUrl: String): List<PlanLink> {
        val root = rootOf(anyPlanUrl)
        val dir = anyPlanUrl.substringBeforeLast('/') + "/"
        for (c in listOf(root, root + "index.htm", root + "index.html", dir)) {
            val links = try {
                parseIndex(fetch(c))
            } catch (e: Exception) {
                emptyList()
            }
            if (links.isNotEmpty()) return links
        }
        return emptyList()
    }

    private fun rootOf(url: String): String = try {
        val u = URI(url)
        val port = if (u.port > 0) ":" + u.port else ""
        u.scheme + "://" + u.host + port + "/"
    } catch (e: Exception) {
        "https://splan.eit.h-da.de/"
    }

    private fun parseIndex(doc: Document): List<PlanLink> {
        val seen = HashSet<String>()
        val out = ArrayList<PlanLink>()
        for (a in doc.select("a[href]")) {
            val url = a.absUrl("href").substringBefore('#')
            val file = url.substringAfterLast('/')
            val m = PLAN_FILE.find(file) ?: continue
            val sem = m.groupValues[3].toIntOrNull() ?: continue
            if (!seen.add(url)) continue
            val variant = m.groupValues[4].trim('_', ' ').uppercase()
            out.add(PlanLink(url, m.groupValues[1].uppercase() + " (" + m.groupValues[2].uppercase() + ")", sem, variant))
        }
        return out.sortedWith(compareBy<PlanLink>({ it.group }, { it.semester }, { it.variant }))
    }

    // ---------- x/y-Raster ----------

    /**
     * Die Seite besteht aus Überschrift "x" / "y" / "x1" ... gefolgt von einer Liste Datumsangaben.
     * Wir laufen den Text durch und ordnen jedes Datum dem zuletzt gesehenen Label zu.
     */
    private fun parseRaster(doc: Document): Map<String, Set<LocalDate>> {
        val res = HashMap<String, MutableSet<LocalDate>>()
        val tok = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})|\\p{L}[\\p{L}\\p{N}]*")
        var cur: String? = null
        for (m in tok.findAll(doc.body().text())) {
            if (m.groups[1] != null) {
                val d = try {
                    LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
                } catch (e: Exception) {
                    null
                }
                val c = cur
                if (c != null && d != null) {
                    res.getOrPut(c) { HashSet() }.add(d)
                }
            } else {
                val w = m.value.lowercase()
                cur = if (LABEL.matches(w)) w else if (DAYS.containsKey(w)) cur else null
            }
        }
        return res
    }

    // ---------- Wochenraster-Tabelle ----------

    private fun rowsOf(table: Element): List<Element> {
        val rows = ArrayList<Element>()
        for (c in table.children()) {
            val n = c.normalName()
            if (n == "tr") {
                rows.add(c)
            } else if (n == "thead" || n == "tbody" || n == "tfoot") {
                for (r in c.children()) {
                    if (r.normalName() == "tr") rows.add(r)
                }
            }
        }
        return rows
    }

    /** Tabellen-Gitter mit rowspan/colspan, damit Spaltenindex = Wochentag stimmt. */
    private fun grid(rows: List<Element>): List<Cell> {
        val used = HashSet<Long>()
        val cells = ArrayList<Cell>()
        for ((r, row) in rows.withIndex()) {
            var c = 0
            for (el in row.children()) {
                if (el.normalName() != "td" && el.normalName() != "th") continue
                while (used.contains(r * 1000L + c)) c++
                val cs = maxOf(1, el.attr("colspan").toIntOrNull() ?: 1)
                val rs = maxOf(1, el.attr("rowspan").toIntOrNull() ?: 1)
                for (dr in 0 until rs) {
                    for (dc in 0 until cs) used.add((r + dr) * 1000L + (c + dc))
                }
                cells.add(Cell(el, r, c, rs, cs))
                c += cs
            }
        }
        return cells
    }

    private fun parseSlots(doc: Document): List<Slot> {
        for (table in doc.select("table")) {
            val rows = rowsOf(table)
            val cells = grid(rows)

            // Kopfzeile mit den Wochentagen finden
            var head = -1
            val dayCols = HashMap<Int, DayOfWeek>()
            for (r in rows.indices) {
                val found = HashMap<Int, DayOfWeek>()
                for (c in cells) {
                    if (c.row != r) continue
                    val d = DAYS[c.el.text().trim().lowercase()] ?: continue
                    for (i in c.col until c.col + c.colSpan) found[i] = d
                }
                if (found.size >= 3) {
                    head = r
                    dayCols.putAll(found)
                    break
                }
            }
            if (head < 0) continue

            // Zeitfenster je Zeile (Spalte ohne Wochentag, z. B. "08:30 - 10:00")
            val times = HashMap<Int, Pair<LocalTime, LocalTime>>()
            for (c in cells.sortedBy { it.col }) {
                if (c.row <= head || dayCols.containsKey(c.col) || times.containsKey(c.row)) continue
                val m = TIME.find(c.el.text()) ?: continue
                times[c.row] = Pair(
                    LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()),
                    LocalTime.of(m.groupValues[3].toInt(), m.groupValues[4].toInt())
                )
            }

            val raw = ArrayList<Slot>()
            for (c in cells) {
                val day = dayCols[c.col] ?: continue
                if (c.row <= head) continue
                val t0 = times[c.row] ?: continue
                val lastRow = (c.row + c.rowSpan - 1 downTo c.row).firstOrNull { times.containsKey(it) } ?: c.row
                val t1 = times[lastRow] ?: t0
                for (e in entries(c.el)) {
                    raw.add(Slot(day, t0.first, t1.second, e.title, e.lecturers, e.rooms, e.markers, e.code, 0))
                }
            }

            // Gleichartige Einträge (z. B. zwei parallele Übungsgruppen) durchnummerieren
            val count = HashMap<String, Int>()
            return raw.map { s ->
                val n = count[s.baseKey] ?: 0
                count[s.baseKey] = n + 1
                s.copy(order = n)
            }
        }
        return emptyList()
    }

    // ---------- Zelleninhalt ----------

    /** Zerlegt eine Zelle in Zeilen (<br> und Blockelemente trennen) und merkt sich den fetten Text. */
    private fun lines(cell: Element): List<Ln> {
        val out = ArrayList<Ln>()
        val text = StringBuilder()
        val bold = StringBuilder()

        fun clean(s: CharSequence): String = s.toString().replace('\u00a0', ' ').replace(WS, " ").trim()

        fun flush() {
            val t = clean(text)
            if (t.isNotEmpty()) out.add(Ln(t, clean(bold)))
            text.setLength(0)
            bold.setLength(0)
        }

        fun walk(n: Node, inBold: Boolean) {
            if (n is TextNode) {
                text.append(n.wholeText)
                if (inBold) bold.append(n.wholeText)
            } else if (n is Element) {
                val tag = n.normalName()
                if (tag == "br") {
                    flush()
                    return
                }
                val block = n.isBlock
                if (block) flush()
                val b = inBold || tag == "b" || tag == "strong"
                for (c in n.childNodes()) walk(c, b)
                if (block) flush()
            }
        }

        for (c in cell.childNodes()) walk(c, false)
        flush()
        return out
    }

    private fun isInfo(t: String): Boolean = t.any { it.isDigit() } || MARK.containsMatchIn(t)

    /**
     * Eintrag = [Dozenten] / **Titel** / [x|y] Raum(e) Modulnummer, mehrere Einträge pro Zelle möglich.
     * Der fette Titel dient als Anker.
     */
    private fun entries(cell: Element): List<E> {
        val ls = lines(cell)
        val idx = ls.indices.filter { ls[it].bold.isNotEmpty() && ls[it].bold == ls[it].text }
        val out = ArrayList<E>()
        for ((k, p) in idx.withIndex()) {
            val prev = if (k == 0) -1 else idx[k - 1]
            val next = if (k == idx.size - 1) ls.size else idx[k + 1]
            // Die letzte Zeile vor dem nächsten Titel gehört dem nächsten Eintrag (Dozenten),
            // außer sie sieht nach Raum/Modul aus.
            var end = next
            if (next < ls.size && next - 1 > p && !isInfo(ls[next - 1].text)) end = next - 1
            var info = (p + 1 until end).joinToString(" ") { ls[it].text }
            val lec = if (p - 1 > prev && !isInfo(ls[p - 1].text)) ls[p - 1].text else ""

            var markers = emptyList<String>()
            val m = MARK.find(info)
            if (m != null) {
                markers = LABEL.findAll(m.groupValues[1]).map { it.value }.distinct().toList()
                info = info.substring(m.range.last + 1).trim()
            }
            val rooms = ROOM.findAll(info).map { it.value }.toList()
            val code = ROOM.replace(info, " ").replace(",", " ").replace(WS, " ").trim()
            out.add(E(ls[p].bold, lec, rooms.joinToString(", "), markers, code))
        }
        return out
    }
}
