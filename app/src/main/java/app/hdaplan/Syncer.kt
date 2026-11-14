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
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

object Syncer {
    const val ZONE = "Europe/Berlin"
    private const val WORK = "hda-sync"

    /** Plant den automatischen Abgleich alle 6 Stunden (nur mit Netz). */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequest.Builder(SyncWorker::class.java, 6, TimeUnit.HOURS)
            .setInitialDelay(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun stop(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
    }

    /**
     * Liest den Plan, berechnet alle Termine ab heute für die gewählten Einträge und gleicht den Kalender ab.
     * Wirft bei Netzwerk-/Lesefehlern. Gibt eine Statuszeile zurück und speichert sie.
     */
    @Synchronized
    fun execute(ctx: Context, preloaded: Plan? = null): String {
        val pr = Prefs(ctx)
        val granted = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!granted) return finish(pr, "Kein Kalender-Zugriff erlaubt.")
        if (pr.calendarId < 0) return finish(pr, "Kein Kalender gewählt.")

        val plan = preloaded ?: Parser.load(pr.url)
        if (plan.slots.isEmpty()) {
            return finish(pr, "Keine Termine im Plan gefunden – hat sich der Seitenaufbau geändert?")
        }
        if (plan.raster.isEmpty()) {
            return finish(pr, "x/y-Raster nicht gefunden – die Kalendertage lassen sich nicht berechnen.")
        }

        val (hit, miss) = Matcher.match(pr.selection, plan.slots)
        val zone = ZoneId.of(ZONE)
        val today = LocalDate.now(zone)
        val fromMs = today.atStartOfDay(zone).toInstant().toEpochMilli()

        val desired = LinkedHashMap<String, Ev>()
        for (s in hit) {
            for (day in plan.datesFor(s)) {
                if (day.isBefore(today)) continue
                val key = hash("${s.baseKey}#${s.order}@$day")
                desired[key] = Ev(
                    day.atTime(s.start).atZone(zone).toInstant().toEpochMilli(),
                    day.atTime(s.end).atZone(zone).toInstant().toEpochMilli(),
                    s.title,
                    s.rooms,
                    describe(s, key)
                )
            }
        }

        val (ins, upd, del) = CalendarSync.sync(ctx, pr.calendarId, desired, fromMs)

        // Aktuelle Raum-/Dozenten-Angaben merken, damit der Eintrag beim nächsten Mal wiedergefunden wird.
        pr.selection = hit.map { Sel(it.baseKey, it.lecturers, it.rooms, it.order) } + miss

        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
        val sb = StringBuilder("Zuletzt $time: $ins neu, $upd geändert, $del gelöscht (Plan-Stand ${plan.stand}).")
        if (miss.isNotEmpty()) {
            sb.append("\nNicht mehr im Plan gefunden: ").append(miss.joinToString("; ") { missLabel(it) })
        }
        return finish(pr, sb.toString())
    }

    private fun finish(pr: Prefs, msg: String): String {
        pr.status = msg
        return msg
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

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)

    private fun missLabel(s: Sel): String {
        val f = s.key.split("|")
        if (f.size < 4) return s.key
        val day = try {
            DayOfWeek.of(f[0].toInt()).getDisplayName(TextStyle.SHORT, Locale.GERMAN)
        } catch (e: Exception) {
            f[0]
        }
        return "${f[3]} ($day ${f[1]})"
    }
}

/** Läuft alle 6 Stunden im Hintergrund. */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val pr = Prefs(applicationContext)
        return try {
            Syncer.execute(applicationContext)
            Result.success()
        } catch (e: IOException) {
            pr.status = "Netzwerkfehler: ${e.message}"
            Result.retry()
        } catch (e: Exception) {
            pr.status = "Fehler: ${e.message}"
            Result.success()
        }
    }
}
