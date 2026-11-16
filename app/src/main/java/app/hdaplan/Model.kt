package app.hdaplan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/**
 * Zustand für die Oberfläche. Die Wahrheit liegt in [Store] (Festplatte);
 * [reload] liest sie in Compose-Zustände ein, alle Änderungen laufen über die Funktionen hier.
 */
object Model {
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    // --- Daten ---
    var plans by mutableStateOf(listOf<PlanRef>())
    var loaded by mutableStateOf(mapOf<String, Plan>())
    var selection by mutableStateOf(mapOf<String, List<Sel>>())
    var hidden by mutableStateOf(setOf<String>())
    var aliases by mutableStateOf(mapOf<String, String>())
    var occs by mutableStateOf(listOf<Occ>())

    // --- Einstellungen ---
    var calendars by mutableStateOf(listOf<CalInfo>())
    var calendarId by mutableStateOf(-1L)
    var hasCalPerm by mutableStateOf(false)
    var intervalHours by mutableStateOf(6)
    var notify by mutableStateOf(true)
    var titleFormat by mutableStateOf(0)
    var emoji by mutableStateOf(false)

    // --- Status ---
    var status by mutableStateOf("")
    var lastOk by mutableStateOf(0L)
    var log by mutableStateOf(listOf<LogEntry>())
    var refreshing by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    // --- Ungespeicherte Auswahl (Überschreibungen pro Plan) ---
    var draft by mutableStateOf(mapOf<String, Set<String>>())
    var dirty by mutableStateOf(false)

    private var busy = 0

    private fun begin() {
        busy++
        refreshing = true
    }

    private fun end() {
        busy = maxOf(0, busy - 1)
        refreshing = busy > 0
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        reload()
    }

    private fun hasCalPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            app.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Liest alles aus dem Speicher neu ein (immer im Hauptthread aufrufen). */
    fun reload() {
        val st = Store(app)
        val refs = st.plans
        val ld = HashMap<String, Plan>()
        val sel = HashMap<String, List<Sel>>()
        for (r in refs) {
            val p = st.loadPlan(r.id)
            if (p != null) ld[r.id] = p
            sel[r.id] = st.selection(r.id)
        }
        plans = refs
        loaded = ld
        selection = sel
        hidden = st.hidden
        aliases = st.aliases
        intervalHours = st.intervalHours
        notify = st.notify
        titleFormat = st.titleFormat
        emoji = st.emoji
        status = st.status
        lastOk = st.lastOk
        log = st.log

        val perm = hasCalPermission()
        hasCalPerm = perm
        val cals: List<CalInfo> = if (perm) {
            try {
                CalendarSync.listCalendars(app)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        calendars = cals
        var cal = st.calendarId
        if (cals.isNotEmpty() && cals.none { it.id == cal }) {
            cal = (cals.firstOrNull { it.primaryGoogle } ?: cals.first()).id
            st.calendarId = cal
        }
        calendarId = cal
        occs = Occurrences.build(refs, ld, sel, hidden)
    }

    fun needsSetup(): Boolean = !hasCalPerm || calendarId < 0

    fun calendarName(): String {
        val c = calendars.firstOrNull { it.id == calendarId } ?: return "–"
        return c.name + " (" + c.account + ")"
    }

    // ---------- Auswahl ----------

    private fun savedIds(planId: String): Set<String> {
        val p = loaded[planId] ?: return emptySet()
        return Matcher.match(selection[planId] ?: emptyList(), p.slots).first.map { it.id }.toSet()
    }

    /** Angekreuzte Einträge eines Plans (ungespeicherte Änderungen haben Vorrang). */
    fun draftOf(planId: String): Set<String> = draft[planId] ?: savedIds(planId)

    fun toggleSlot(planId: String, slotId: String, on: Boolean) {
        val cur = draftOf(planId)
        val next: Set<String> = if (on) cur + slotId else cur - slotId
        draft = draft + (planId to next)
        dirty = true
    }

    fun toggleGroup(planId: String, ids: List<String>, on: Boolean) {
        val cur = draftOf(planId)
        val next: Set<String> = if (on) cur + ids else cur - ids.toSet()
        draft = draft + (planId to next)
        dirty = true
    }

    fun saveDraft() {
        if (needsSetup()) {
            message = "Erst Kalender-Zugriff erlauben und einen Kalender wählen."
            return
        }
        val st = Store(app)
        for (r in plans) {
            val ids = draft[r.id] ?: continue
            val p = loaded[r.id] ?: continue
            st.setSelection(
                r.id,
                p.slots.filter { ids.contains(it.id) }.map { Sel(it.baseKey, it.lecturers, it.rooms, it.order) }
            )
        }
        draft = emptyMap()
        dirty = false
        reload()
        Syncer.schedule(app)
        syncNow()
    }

    // ---------- Laden & Synchronisieren ----------

    fun refreshPlans(report: Boolean) {
        if (refreshing) return
        begin()
        io.execute {
            val st = Store(app)
            var err: String? = null
            for (r in st.plans) {
                try {
                    val old = st.loadPlan(r.id)
                    val p = Parser.load(r.id, r.url)
                    if (p.slots.isEmpty()) {
                        err = "Keine Termine erkannt: " + r.url
                    } else {
                        if (old != null && old.stand != p.stand) {
                            st.addLog(listOf("Plan " + p.title + ": Stand " + old.stand + " → " + p.stand))
                        }
                        st.savePlan(p)
                    }
                } catch (e: Exception) {
                    err = "Laden fehlgeschlagen: " + e.message
                }
            }
            NextWidget.refresh(app)
            ui.post {
                end()
                reload()
                if (err != null) {
                    message = err
                } else if (report) {
                    message = "Plan aktualisiert."
                }
            }
        }
    }

    fun syncNow(quiet: Boolean = false) {
        if (needsSetup()) {
            if (!quiet) message = "Erst Kalender-Zugriff erlauben und einen Kalender wählen."
            return
        }
        begin()
        io.execute {
            val msg = try {
                Syncer.execute(app, true).status
            } catch (e: Exception) {
                val st = Store(app)
                st.lastError = e.stackTraceToString().take(1500)
                st.status = "Fehler: " + e.message
                "Fehler: " + e.message
            }
            ui.post {
                end()
                reload()
                if (!quiet || msg.startsWith("Fehler")) message = msg.lineSequence().first()
            }
        }
    }

    /** Pull-to-Refresh / Aktualisieren-Knopf: Plan neu lesen und – wenn eingerichtet – den Kalender abgleichen. */
    fun refreshAll() {
        if (!needsSetup() && selection.values.any { it.isNotEmpty() }) syncNow() else refreshPlans(true)
    }

    /** Beim App-Start: Plan-Cache auffrischen; ist der letzte Abgleich älter als 3 Stunden, auch den Kalender. */
    fun autoRefresh() {
        val stale = System.currentTimeMillis() - lastOk > 3 * 3600_000L
        if (stale && !needsSetup() && selection.values.any { it.isNotEmpty() }) syncNow(true) else refreshPlans(false)
    }

    private fun syncIfConfigured() {
        if (!needsSetup() && selection.values.any { it.isNotEmpty() }) syncNow()
    }

    // ---------- Ausblenden, Kürzel, Titel ----------

    fun setHidden(key: String, hide: Boolean) {
        val st = Store(app)
        st.hidden = if (hide) st.hidden + key else st.hidden - key
        reload()
        syncIfConfigured()
    }

    fun setAlias(base: String, alias: String) {
        val st = Store(app)
        val m = HashMap(st.aliases)
        if (alias.isBlank()) {
            m.remove(base)
        } else {
            m[base] = alias.trim()
        }
        st.aliases = m
        reload()
        syncIfConfigured()
    }

    fun setFormat(f: Int) {
        Store(app).titleFormat = f
        titleFormat = f
        syncIfConfigured()
    }

    fun updateEmoji(on: Boolean) {
        Store(app).emoji = on
        emoji = on
        syncIfConfigured()
    }

    // ---------- Einstellungen ----------

    fun setInterval(h: Int) {
        Store(app).intervalHours = h
        intervalHours = h
        if (selection.values.any { it.isNotEmpty() }) Syncer.schedule(app)
    }

    fun updateNotify(on: Boolean) {
        Store(app).notify = on
        notify = on
    }

    /** Wechselt den Zielkalender: Termine im alten Kalender werden entfernt, im neuen angelegt. */
    fun setCalendar(id: Long) {
        val old = calendarId
        if (old == id) return
        begin()
        io.execute {
            try {
                if (old >= 0) CalendarSync.deleteAll(app, old)
            } catch (e: Exception) {
                // alter Kalender evtl. nicht mehr vorhanden
            }
            Store(app).calendarId = id
            ui.post {
                reload()
                end()
                syncIfConfigured()
            }
        }
    }

    // ---------- Pläne verwalten ----------

    fun addPlan(url: String) {
        val st = Store(app)
        st.plans = st.plans + PlanRef(st.nextPlanId(), url.trim())
        reload()
        refreshPlans(true)
    }

    fun replacePlan(id: String, url: String) {
        val st = Store(app)
        st.plans = st.plans.map { if (it.id == id) PlanRef(id, url.trim()) else it }
        st.setSelection(id, emptyList())
        st.deletePlan(id)
        draft = emptyMap()
        dirty = false
        reload()
        refreshPlans(true)
    }

    fun removePlan(id: String) {
        val st = Store(app)
        st.plans = st.plans.filter { it.id != id }
        st.setSelection(id, emptyList())
        st.deletePlan(id)
        draft = emptyMap()
        dirty = false
        reload()
        if (!needsSetup()) syncNow()
    }

    // ---------- Aufräumen & Diagnose ----------

    fun cleanup() {
        val cal = calendarId
        if (needsSetup()) {
            message = "Kein Kalender gewählt."
            return
        }
        Syncer.stop(app)
        begin()
        io.execute {
            val n = try {
                CalendarSync.deleteAll(app, cal)
            } catch (e: Exception) {
                -1
            }
            val st = Store(app)
            val msg = if (n >= 0) "$n App-Termine gelöscht, Auto-Sync gestoppt." else "Löschen fehlgeschlagen."
            st.status = msg
            st.addLog(listOf(msg))
            ui.post {
                end()
                reload()
                message = msg
            }
        }
    }

    fun clearLog() {
        Store(app).clearLog()
        log = emptyList()
    }

    fun diagnostics(): String {
        val st = Store(app)
        val sb = StringBuilder()
        sb.append("h-da Plan 2.0 · Android ").append(Build.VERSION.SDK_INT)
        sb.append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("Kalender-Zugriff: ").append(hasCalPerm)
        sb.append(" · Kalender-ID: ").append(st.calendarId)
        sb.append(" · Intervall: ").append(st.intervalHours).append(" h\n")
        for (r in st.plans) {
            val p = st.loadPlan(r.id)
            sb.append("Plan ").append(r.id).append(": ").append(r.url).append('\n')
            if (p == null) {
                sb.append("  (nicht geladen)\n")
            } else {
                sb.append("  ").append(p.slots.size).append(" Einträge, Raster: ")
                sb.append(p.raster.keys.sorted().joinToString(","))
                sb.append(", Stand ").append(p.stand).append(", ").append(p.term).append('\n')
            }
            sb.append("  Auswahl: ").append(st.selection(r.id).size).append('\n')
        }
        sb.append("Ausgeblendet: ").append(st.hidden.size).append('\n')
        sb.append("Status: ").append(st.status).append('\n')
        if (st.lastError.isNotEmpty()) sb.append("Letzter Fehler:\n").append(st.lastError).append('\n')
        return sb.toString()
    }
}
