package app.hdaplan

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events

data class CalInfo(val id: Long, val name: String, val account: String, val primaryGoogle: Boolean)

/** Ein Kalendertermin, so wie die App ihn haben möchte (Zeiten in ms seit 1970). */
data class Ev(val start: Long, val end: Long, val title: String, val loc: String, val desc: String)

/** kind: 0 = neu, 1 = geändert, 2 = entfernt */
class Change(val kind: Int, val old: Ev?, val new: Ev?)

class Outcome(
    val ins: Int,
    val upd: Int,
    val del: Int,
    val hadExisting: Boolean,
    val changes: List<Change>
)

/**
 * Schreibt, aktualisiert und löscht Termine über den Kalender-Provider des Handys.
 * Termine in einem Google-Konto werden von Android automatisch zu Google synchronisiert.
 * Eigene Termine erkennt die App an der Markierung [hdaplan:...] in der Beschreibung.
 */
object CalendarSync {
    private val KEY = Regex("\\[hdaplan:([0-9a-f]+)]")

    private class Existing(val id: Long, val ev: Ev)

    /** Alle Kalender, in die geschrieben werden darf. */
    fun listCalendars(ctx: Context): List<CalInfo> {
        val out = ArrayList<CalInfo>()
        val proj = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.OWNER_ACCOUNT
        )
        val sel = "${Calendars.CALENDAR_ACCESS_LEVEL} >= 500"
        ctx.contentResolver.query(Calendars.CONTENT_URI, proj, sel, null, null)?.use { c ->
            while (c.moveToNext()) {
                val account = c.getString(2) ?: ""
                out.add(
                    CalInfo(
                        c.getLong(0),
                        c.getString(1) ?: "?",
                        account,
                        c.getString(3) == "com.google" && account == c.getString(4)
                    )
                )
            }
        }
        return out
    }

    /** Alle von der App angelegten Termine ab [fromMs] (Schlüssel aus der Markierung, Termin). */
    private fun managed(ctx: Context, calId: Long, fromMs: Long): List<Pair<String, Existing>> {
        val out = ArrayList<Pair<String, Existing>>()
        val proj = arrayOf(
            Events._ID, Events.DTSTART, Events.DTEND, Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION
        )
        val sel = "${Events.CALENDAR_ID}=? AND deleted=0 AND ${Events.DTSTART}>=? AND ${Events.DESCRIPTION} LIKE ?"
        val args = arrayOf(calId.toString(), fromMs.toString(), "%hdaplan:%")
        ctx.contentResolver.query(Events.CONTENT_URI, proj, sel, args, null)?.use { c ->
            while (c.moveToNext()) {
                val desc = c.getString(5) ?: ""
                val key = KEY.find(desc)?.groupValues?.get(1) ?: continue
                val ev = Ev(c.getLong(1), c.getLong(2), c.getString(3) ?: "", c.getString(4) ?: "", desc)
                out.add(Pair(key, Existing(c.getLong(0), ev)))
            }
        }
        return out
    }

    private fun values(calId: Long?, e: Ev): ContentValues {
        val v = ContentValues()
        if (calId != null) v.put(Events.CALENDAR_ID, calId)
        v.put(Events.TITLE, e.title)
        v.put(Events.DTSTART, e.start)
        v.put(Events.DTEND, e.end)
        v.put(Events.EVENT_TIMEZONE, Syncer.ZONE)
        v.put(Events.EVENT_LOCATION, e.loc)
        v.put(Events.DESCRIPTION, e.desc)
        return v
    }

    /** Vergleichsform: Google darf Zeilenenden/Leerraum leicht ändern, ohne dass wir "Änderung" melden. */
    private fun norm(e: Ev): Ev =
        e.copy(title = e.title.trim(), loc = e.loc.trim(), desc = e.desc.replace("\r\n", "\n").trim())

    private fun uri(id: Long) = ContentUris.withAppendedId(Events.CONTENT_URI, id)

    /** Gleicht die App-Termine ab [fromMs] mit [desired] (Schlüssel -> Termin) ab. */
    fun sync(ctx: Context, calId: Long, desired: Map<String, Ev>, fromMs: Long): Outcome {
        val cr = ctx.contentResolver
        val have = HashMap<String, Existing>()
        val changes = ArrayList<Change>()
        var ins = 0
        var upd = 0
        var del = 0

        val found = managed(ctx, calId, fromMs)
        val hadExisting = found.isNotEmpty()

        for ((key, ex) in found) {
            if (have.containsKey(key)) {
                cr.delete(uri(ex.id), null, null) // doppelt vorhanden
            } else {
                have[key] = ex
            }
        }
        for ((key, ex) in have) {
            val want = desired[key]
            if (want == null) {
                cr.delete(uri(ex.id), null, null)
                del++
                changes.add(Change(2, ex.ev, null))
            } else if (norm(want) != norm(ex.ev)) {
                cr.update(uri(ex.id), values(null, want), null, null)
                upd++
                changes.add(Change(1, ex.ev, want))
            }
        }
        for ((key, want) in desired) {
            if (!have.containsKey(key)) {
                cr.insert(Events.CONTENT_URI, values(calId, want))
                ins++
                changes.add(Change(0, null, want))
            }
        }
        return Outcome(ins, upd, del, hadExisting, changes)
    }

    /** Löscht alle von der App angelegten Termine (auch vergangene). */
    fun deleteAll(ctx: Context, calId: Long): Int {
        var n = 0
        for ((_, ex) in managed(ctx, calId, 0L)) {
            ctx.contentResolver.delete(uri(ex.id), null, null)
            n++
        }
        return n
    }
}
