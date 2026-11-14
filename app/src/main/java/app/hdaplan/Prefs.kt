package app.hdaplan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hdaplan", Context.MODE_PRIVATE)

    var url: String
        get() = sp.getString("url", DEFAULT_URL) ?: DEFAULT_URL
        set(v) {
            sp.edit().putString("url", v).apply()
        }

    var calendarId: Long
        get() = sp.getLong("cal", -1L)
        set(v) {
            sp.edit().putLong("cal", v).apply()
        }

    var status: String
        get() = sp.getString("status", "") ?: ""
        set(v) {
            sp.edit().putString("status", v).apply()
        }

    var selection: List<Sel>
        get() = try {
            val a = JSONArray(sp.getString("sel", "[]") ?: "[]")
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Sel(o.getString("k"), o.getString("l"), o.getString("r"), o.getInt("o"))
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val a = JSONArray()
            for (s in v) {
                a.put(JSONObject().put("k", s.key).put("l", s.lecturers).put("r", s.rooms).put("o", s.order))
            }
            sp.edit().putString("sel", a.toString()).apply()
        }

    companion object {
        const val DEFAULT_URL = "https://splan.eit.h-da.de/s/S_EIT_BA_1EIT.htm"
    }
}
