package app.hdaplan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class SyncResult(val status: String, val ins: Int = 0, val upd: Int = 0, val del: Int = 0)

object Syncer {
    const val ZONE = "Europe/Berlin"
    private const val WORK = "hda-sync"
    private val DM = DateTimeFormatter.ofPattern("dd.MM.")

    /** Plant den automatischen Abgleich im eingestellten Intervall (nur mit Netz). */
    fun schedule(ctx: Context) {
        val h = Store(ctx).intervalHours.coerceAtLeast(1).toLong()
        val req = PeriodicWorkRequest.Builder(SyncWorker::class.java, h, TimeUnit.HOURS)
            .setInitialDelay(h, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun stop(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
    }

    /**
     * Liest alle Pläne, berechnet die Termine ab heute für die gewählten Einträge und gleicht den Kalender ab.
     * manual = true: vom Nutzer ausgelöst (keine Benachrichtigung, leere Auswahl räumt den Kalender auf).
     * Wirft bei Netzwerk-/Lesefehlern (IOException u. a.).
     */
    @Synchronized
    fun execute(ctx: Context, manual: Boolean): SyncResult {
        val st = Store(ctx)
        val perms = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!perms) return done(st, "Kein Kalender-Zugriff erlaubt.")
        if (st.calendarId < 0) return done(st, "Kein Kalender gewählt.")

        val refs = st.plans
        val plans = ArrayList<Plan>()
        val logLines = ArrayList<String>()
        for (r in refs) {
            val old = st.loadPlan(r.id)
            val p = Parser.load(r.id, r.url)
            if (p.slots.isEmpty()) {
                return done(st, "Keine Termine im Plan ${r.url} gefunden – hat sich der Seitenaufbau geändert?")
            }
            if (p.raster.isEmpty()) {
                return done(st, "x/y-Raster zu ${p.title} nicht gefunden.")
            }
            if (old != null && old.stand != p.stand) {
                logLines.add("Plan ${p.title}: Stand ${old.stand} → ${p.stand}")
            }
            st.savePlan(p)
            plans.add(p)
        }

        var total = 0
        for (r in refs) total += st.selection(r.id).size
        if (total == 0 && !manual) {
            st.addLog(logLines)
            return done(st, "Nichts ausgewählt – Kalender unverändert.")
        }

        val zone = ZoneId.of(ZONE)
        val today = LocalDate.now(zone)
        val fromMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val aliases = st.aliases
        val fmt = st.titleFormat
        val emoji = st.emoji
        val hidden = st.hidden

        val desired = LinkedHashMap<String, Ev>()
        val missing = ArrayList<String>()
        for (p in plans) {
            val pair = Matcher.match(st.selection(p.id), p.slots)
            val hit = pair.first
            val miss = pair.second
            for (s in hit) {
                for (d in p.datesFor(s)) {
                    if (d.isBefore(today)) continue
                    val key = Occurrences.keyOf(p.id, s, d)
                    if (hidden.contains(key)) continue
                    desired[key] = Ev(
                        d.atTime(s.start).atZone(zone).toInstant().toEpochMilli(),
                        d.atTime(s.end).atZone(zone).toInstant().toEpochMilli(),
                        Fmt.calTitle(s, aliases, fmt, emoji),
                        s.rooms,
                        describe(s, key)
                    )
                }
            }
            // Aktuelle Raum-/Dozenten-Angaben merken, damit der Eintrag beim nächsten Mal wiedergefunden wird.
            st.setSelection(p.id, hit.map { Sel(it.baseKey, it.lecturers, it.rooms, it.order) } + miss)
            for (m in miss) missing.add(missLabel(m))
        }

        val out = CalendarSync.sync(ctx, st.calendarId, desired, fromMs)

        val details = describeChanges(out.changes, zone)
        val lines = ArrayList<String>(logLines)
        if (!out.hadExisting) {
            if (out.ins > 0) lines.add("Eingetragen: ${out.ins} Termine")
        } else if (manual) {
            if (out.ins + out.upd + out.del > 0) {
                lines.add("Abgeglichen: ${out.ins} neu, ${out.upd} geändert, ${out.del} gelöscht")
            }
        } else {
            lines.addAll(details)
        }
        st.addLog(lines)
        if (!manual && out.hadExisting && details.isNotEmpty() && st.notify) {
            Notifier.post(ctx, details)
        }

        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
        val sb = StringBuilder("Zuletzt $time: ${out.ins} neu, ${out.upd} geändert, ${out.del} gelöscht.")
        if (missing.isNotEmpty()) {
            sb.append("\nNicht mehr im Plan gefunden: ").append(missing.joinToString("; "))
        }
        st.lastOk = System.currentTimeMillis()
        st.status = sb.toString()
        NextWidget.refresh(ctx)
        return SyncResult(sb.toString(), out.ins, out.upd, out.del)
    }

    private fun done(st: Store, msg: String): SyncResult {
        st.status = msg
        return SyncResult(msg)
    }

    private fun describe(s: Slot, key: String): String {
        val sb = StringBuilder()
        if (s.lecturers.isNotEmpty()) sb.append("Dozent: ").append(s.lecturers).append('\n')
        if (s.rooms.isNotEmpty()) sb.append("Raum: ").append(s.rooms).append('\n')
        if (s.code.isNotEmpty()) sb.append("Modul: ").append(s.code).append('\n')
        if (s.markers.isNotEmpty()) {
            sb.append("Raster ").append(s.markers.joinToString(", "))
            sb.append(" – konkrete Termine legt der Dozent fest\n")
        }
        sb.append("\n[hdaplan:").append(key).append(']')
        return sb.toString()
    }

    private fun missLabel(s: Sel): String {
        val f = s.key.split("|")
        if (f.size < 4) return s.key
        val day = try {
            Fmt.dayShort(DayOfWeek.of(f[0].toInt()))
        } catch (e: Exception) {
            f[0]
        }
        return f[3] + " (" + day + " " + f[1] + ")"
    }

    // ---------- Änderungen in lesbare Zeilen verwandeln ----------

    private fun dateOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun slotTime(e: Ev, zone: ZoneId): String {
        val s = Instant.ofEpochMilli(e.start).atZone(zone)
        val en = Instant.ofEpochMilli(e.end).atZone(zone)
        return Fmt.dayShort(s.dayOfWeek) + " " + s.toLocalTime() + "–" + en.toLocalTime()
    }

    private fun lecturerOf(desc: String): String {
        for (line in desc.lines()) {
            if (line.startsWith("Dozent: ")) return line.removePrefix("Dozent: ")
        }
        return ""
    }

    /** Fasst gleichartige Änderungen mehrerer Wochen zu einer Zeile zusammen. */
    private fun describeChanges(changes: List<Change>, zone: ZoneId): List<String> {
        val groups = LinkedHashMap<String, Pair<String, ArrayList<LocalDate>>>()
        fun add(key: String, text: String, d: LocalDate) {
            groups.getOrPut(key) { Pair(text, ArrayList<LocalDate>()) }.second.add(d)
        }
        for (c in changes) {
            val o = c.old
            val n = c.new
            if (c.kind == 0 && n != null) {
                val t = slotTime(n, zone)
                add("N|" + n.title + "|" + t, "Neu: " + n.title + " · " + t, dateOf(n.start, zone))
            } else if (c.kind == 2 && o != null) {
                val t = slotTime(o, zone)
                add("D|" + o.title + "|" + t, "Entfällt: " + o.title + " · " + t, dateOf(o.start, zone))
            } else if (c.kind == 1 && o != null && n != null) {
                val d = dateOf(n.start, zone)
                val to = slotTime(o, zone)
                val tn = slotTime(n, zone)
                if (to != tn) {
                    add("T|" + n.title + "|" + to + "|" + tn, n.title + ": Zeit " + to + " → " + tn, d)
                }
                if (o.loc != n.loc) {
                    val a = o.loc.ifEmpty { "–" }
                    val b = n.loc.ifEmpty { "–" }
                    add("R|" + n.title + "|" + tn + "|" + a + "|" + b, n.title + " · " + tn + ": Raum " + a + " → " + b, d)
                }
                val lo = lecturerOf(o.desc)
                val ln = lecturerOf(n.desc)
                if (lo != ln) {
                    val a = lo.ifEmpty { "–" }
                    val b = ln.ifEmpty { "–" }
                    add("L|" + n.title + "|" + tn + "|" + a + "|" + b, n.title + " · " + tn + ": Dozent " + a + " → " + b, d)
                }
            }
        }
        val out = ArrayList<String>()
        for ((text, dates) in groups.values) {
            val sorted = dates.sorted()
            val first = sorted.first().format(DM)
            out.add(if (sorted.size == 1) "$text (am $first)" else "$text (${sorted.size}× ab $first)")
        }
        return out
    }
}

/** Läuft im eingestellten Intervall im Hintergrund. */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val st = Store(applicationContext)
        return try {
            Syncer.execute(applicationContext, false)
            Result.success()
        } catch (e: IOException) {
            st.status = "Netzwerkfehler: ${e.message}"
            Result.retry()
        } catch (e: Exception) {
            st.status = "Fehler: ${e.message}"
            st.lastError = e.stackTraceToString().take(1500)
            Result.success()
        }
    }
}
