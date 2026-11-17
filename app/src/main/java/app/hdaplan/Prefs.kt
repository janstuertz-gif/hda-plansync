package app.hdaplan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Alles, was die App dauerhaft speichert: Einstellungen, Auswahl, Plan-Cache, Protokoll. */
class Store(ctx: Context) {
    private val app: Context = ctx.applicationContext
    private val sp = app.getSharedPreferences("hdaplan", Context.MODE_PRIVATE)

    private fun defaultPlans() = listOf(PlanRef(Occurrences.FIRST_PLAN, DEFAULT_URL))

    var plans: List<PlanRef>
        get() {
            val raw = sp.getString("plans", null) ?: return defaultPlans()
            return try {
                val a = JSONArray(raw)
                val out = ArrayList<PlanRef>()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    out.add(PlanRef(o.getString("id"), o.getString("url")))
                }
                if (out.isEmpty()) defaultPlans() else out
            } catch (e: Exception) {
                defaultPlans()
            }
        }
        set(v) {
            val a = JSONArray()
            for (r in v) a.put(JSONObject().put("id", r.id).put("url", r.url))
            sp.edit().putString("plans", a.toString()).apply()
        }

    fun nextPlanId(): String {
        var max = 1
        for (r in plans) max = maxOf(max, r.id.removePrefix("p").toIntOrNull() ?: 1)
        return "p" + (max + 1)
    }

    fun selection(planId: String): List<Sel> = try {
        val a = JSONArray(sp.getString("sel_$planId", "[]") ?: "[]")
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Sel(o.getString("k"), o.getString("l"), o.getString("r"), o.getInt("o"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun setSelection(planId: String, v: List<Sel>) {
        val a = JSONArray()
        for (s in v) {
            a.put(JSONObject().put("k", s.key).put("l", s.lecturers).put("r", s.rooms).put("o", s.order))
        }
        sp.edit().putString("sel_$planId", a.toString()).apply()
    }

    /** Schlüssel ausgeblendeter Einzeltermine. */
    var hidden: Set<String>
        get() = try {
            val a = JSONArray(sp.getString("hidden", "[]") ?: "[]")
            (0 until a.length()).map { a.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        set(v) {
            sp.edit().putString("hidden", JSONArray(v.toList()).toString()).apply()
        }

    /** Eigene Kürzel: Kursname -> Kürzel. */
    var aliases: Map<String, String>
        get() = try {
            val o = JSONObject(sp.getString("alias", "{}") ?: "{}")
            val m = HashMap<String, String>()
            for (k in o.keys()) m[k] = o.getString(k)
            m
        } catch (e: Exception) {
            emptyMap()
        }
        set(v) {
            val o = JSONObject()
            for ((k, x) in v) o.put(k, x)
            sp.edit().putString("alias", o.toString()).apply()
        }

    var calendarId: Long
        get() = sp.getLong("cal", -1L)
        set(v) {
            sp.edit().putLong("cal", v).apply()
        }

    var intervalHours: Int
        get() = sp.getInt("interval", 6)
        set(v) {
            sp.edit().putInt("interval", v).apply()
        }

    var notify: Boolean
        get() = sp.getBoolean("notify", true)
        set(v) {
            sp.edit().putBoolean("notify", v).apply()
        }

    /** 0 = "Name (Typ)", 1 = nur Name, 2 = Original */
    var titleFormat: Int
        get() = sp.getInt("fmt", 0)
        set(v) {
            sp.edit().putInt("fmt", v).apply()
        }

    var emoji: Boolean
        get() = sp.getBoolean("emoji", false)
        set(v) {
            sp.edit().putBoolean("emoji", v).apply()
        }

    var status: String
        get() = sp.getString("status", "") ?: ""
        set(v) {
            sp.edit().putString("status", v).apply()
        }

    var lastOk: Long
        get() = sp.getLong("lastOk", 0L)
        set(v) {
            sp.edit().putLong("lastOk", v).apply()
        }

    var lastError: String
        get() = sp.getString("lastError", "") ?: ""
        set(v) {
            sp.edit().putString("lastError", v).apply()
        }

    // ---------- Änderungsprotokoll ----------

    val log: List<LogEntry>
        get() = try {
            val a = JSONArray(sp.getString("log", "[]") ?: "[]")
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                val l = o.getJSONArray("l")
                LogEntry(o.getLong("t"), (0 until l.length()).map { i -> l.getString(i) })
            }
        } catch (e: Exception) {
            emptyList()
        }

    fun addLog(lines: List<String>) {
        if (lines.isEmpty()) return
        val cur = ArrayList<LogEntry>(log)
        cur.add(0, LogEntry(System.currentTimeMillis(), lines))
        val a = JSONArray()
        for (e in cur.take(40)) {
            a.put(JSONObject().put("t", e.time).put("l", JSONArray(e.lines)))
        }
        sp.edit().putString("log", a.toString()).apply()
    }

    fun clearLog() {
        sp.edit().putString("log", "[]").apply()
    }

    // ---------- Plan-Cache (damit Ansichten offline funktionieren) ----------

    private fun planFile(id: String) = File(app.filesDir, "plan_$id.json")

    fun deletePlan(id: String) {
        planFile(id).delete()
    }

    fun savePlan(p: Plan) {
        val o = JSONObject()
        o.put("id", p.id)
        o.put("url", p.url)
        o.put("title", p.title)
        o.put("term", p.term)
        o.put("stand", p.stand)
        o.put("at", p.fetchedAt)
        val sl = JSONArray()
        for (s in p.slots) {
            sl.put(
                JSONObject()
                    .put("d", s.day.value)
                    .put("s", s.start.toString())
                    .put("e", s.end.toString())
                    .put("t", s.title)
                    .put("l", s.lecturers)
                    .put("r", s.rooms)
                    .put("m", JSONArray(s.markers))
                    .put("c", s.code)
                    .put("o", s.order)
                    .put("kb", s.keyBase)
                    .put("ko", s.keyOrder)
            )
        }
        o.put("slots", sl)
        val ra = JSONObject()
        for ((k, v) in p.raster) ra.put(k, JSONArray(v.map { it.toString() }))
        o.put("raster", ra)
        planFile(p.id).writeText(o.toString())
    }

    fun loadPlan(id: String): Plan? {
        return try {
            val f = planFile(id)
            if (!f.exists()) return null
            val o = JSONObject(f.readText())
            val sl = o.getJSONArray("slots")
            val slots = ArrayList<Slot>()
            for (i in 0 until sl.length()) {
                val x = sl.getJSONObject(i)
                val ma = x.getJSONArray("m")
                val markers = ArrayList<String>()
                for (j in 0 until ma.length()) markers.add(ma.getString(j))
                slots.add(
                    Slot(
                        DayOfWeek.of(x.getInt("d")),
                        LocalTime.parse(x.getString("s")),
                        LocalTime.parse(x.getString("e")),
                        x.getString("t"),
                        x.getString("l"),
                        x.getString("r"),
                        markers,
                        x.getString("c"),
                        x.getInt("o"),
                        x.optString("kb", ""),
                        x.optInt("ko", 0)
                    )
                )
            }
            val ro = o.getJSONObject("raster")
            val raster = HashMap<String, Set<LocalDate>>()
            for (k in ro.keys()) {
                val a = ro.getJSONArray(k)
                val set = HashSet<LocalDate>()
                for (j in 0 until a.length()) set.add(LocalDate.parse(a.getString(j)))
                raster[k] = set
            }
            Plan(
                o.getString("id"), o.getString("url"), o.getString("title"), o.optString("term", ""),
                o.optString("stand", "?"), slots, raster, o.optLong("at", 0L)
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_URL = "https://splan.eit.h-da.de/s/S_EIT_BA_1EIT.htm"
    }
}
