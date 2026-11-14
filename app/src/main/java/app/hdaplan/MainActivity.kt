package app.hdaplan

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.time.format.TextStyle
import java.util.Locale

/** Eine einzige Seite: Link -> laden -> ankreuzen -> Kalender wählen -> speichern. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var urlEdit: EditText
    private lateinit var info: TextView
    private lateinit var list: LinearLayout
    private lateinit var spinner: Spinner
    private lateinit var status: TextView

    private var plan: Plan? = null
    private var loadedUrl = ""
    private var cals: List<CalInfo> = emptyList()
    private val boxes = ArrayList<Pair<CheckBox, Slot>>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, big: Boolean = false) = TextView(this).apply {
        this.text = text
        if (big) {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        root.addView(label("1. Stundenplan-Link", true))
        urlEdit = EditText(this).apply {
            setText(prefs.url)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(urlEdit)
        root.addView(button("Stundenplan laden") { loadPlan() })
        info = label("")
        root.addView(info)

        root.addView(label("2. Was brauchst du?", true))
        root.addView(label("Bei Laboren und Übungen gibt es oft mehrere Gruppen – such nur deine aus."))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        root.addView(label("3. In welchen Kalender?", true))
        spinner = Spinner(this)
        root.addView(spinner)
        root.addView(button("Speichern & in Kalender eintragen") { saveAndSync() })
        status = label(prefs.status)
        root.addView(status)
        root.addView(button("Alle App-Termine löschen & Auto-Sync stoppen") { cleanup() })

        setContentView(ScrollView(this).apply { addView(root) })
        loadCalendars()
        loadPlan()
    }

    // ---------- Berechtigung & Kalender ----------

    private fun hasPerms() =
        checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun loadCalendars() {
        if (!hasPerms()) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 1
            )
            return
        }
        cals = CalendarSync.listCalendars(this)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, cals.map { "${it.name} (${it.account})" }
        )
        var i = cals.indexOfFirst { it.id == prefs.calendarId }
        if (i < 0) i = cals.indexOfFirst { it.primaryGoogle }
        if (i >= 0) spinner.setSelection(i)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPerms()) {
            loadCalendars()
        } else {
            status.text = "Ohne Kalender-Zugriff kann die App nichts eintragen."
        }
    }

    // ---------- Plan laden & anzeigen ----------

    private fun loadPlan() {
        val url = urlEdit.text.toString().trim()
        info.text = "Lade …"
        Thread {
            try {
                val p = Parser.load(url)
                runOnUiThread { showPlan(url, p) }
            } catch (e: Exception) {
                runOnUiThread { info.text = "Fehler beim Laden: ${e.message}" }
            }
        }.start()
    }

    private fun slotLabel(p: Plan, s: Slot): String {
        val day = s.day.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
        val mark = if (s.markers.isEmpty()) "" else " · Raster " + s.markers.joinToString(",")
        val who = listOf(s.lecturers, s.rooms).filter { it.isNotEmpty() }.joinToString(" · ")
        return "$day ${s.start}–${s.end}$mark · ${p.datesFor(s).size}×\n$who"
    }

    private fun showPlan(url: String, p: Plan) {
        plan = p
        loadedUrl = url
        list.removeAllViews()
        boxes.clear()
        if (p.slots.isEmpty()) {
            info.text = "Keine Termine erkannt – der Seitenaufbau passt nicht zum Parser."
            return
        }
        info.text = "${p.title} · Stand ${p.stand} · ${p.slots.size} Einträge im Wochenraster"

        val saved = if (url == prefs.url) prefs.selection else emptyList<Sel>()
        val chosen = Matcher.match(saved, p.slots).first.toHashSet()

        val groups = p.slots.groupBy { it.title }.entries
            .sortedWith(
                compareBy<Map.Entry<String, List<Slot>>>(
                    { it.key.substringAfter(':').trim().lowercase() },
                    { it.key }
                )
            )
        for ((title, slots) in groups) {
            val head = CheckBox(this).apply {
                text = title
                setTypeface(typeface, Typeface.BOLD)
            }
            list.addView(head)
            val kids = ArrayList<CheckBox>()
            for (s in slots.sortedWith(compareBy<Slot>({ it.day }, { it.start }))) {
                val cb = CheckBox(this).apply {
                    text = slotLabel(p, s)
                    isChecked = chosen.contains(s)
                }
                cb.setOnClickListener { head.isChecked = kids.all { k -> k.isChecked } }
                kids.add(cb)
                boxes.add(Pair(cb, s))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.leftMargin = dp(28)
                list.addView(cb, lp)
            }
            head.isChecked = kids.all { it.isChecked }
            head.setOnClickListener {
                for (k in kids) k.isChecked = head.isChecked
            }
        }
    }

    // ---------- Speichern, Sync, Aufräumen ----------

    private fun saveAndSync() {
        val p = plan
        if (p == null) {
            status.text = "Erst den Stundenplan laden."
            return
        }
        if (urlEdit.text.toString().trim() != loadedUrl) {
            status.text = "Link geändert – bitte erst „Stundenplan laden“ tippen."
            return
        }
        if (!hasPerms()) {
            loadCalendars()
            status.text = "Bitte Kalender-Zugriff erlauben und danach nochmal tippen."
            return
        }
        val cal = cals.getOrNull(spinner.selectedItemPosition)
        if (cal == null) {
            status.text = "Kein beschreibbarer Kalender gefunden."
            return
        }
        prefs.url = loadedUrl
        prefs.calendarId = cal.id
        prefs.selection = boxes.filter { it.first.isChecked }
            .map { Sel(it.second.baseKey, it.second.lecturers, it.second.rooms, it.second.order) }
        status.text = "Synchronisiere …"
        Syncer.schedule(applicationContext)
        Thread {
            val msg = try {
                Syncer.execute(applicationContext, p)
            } catch (e: Exception) {
                "Fehler: ${e.message}"
            }
            runOnUiThread { status.text = msg }
        }.start()
    }

    private fun cleanup() {
        val cal = cals.getOrNull(spinner.selectedItemPosition)
        if (cal == null) {
            status.text = "Kein Kalender gewählt."
            return
        }
        Syncer.stop(applicationContext)
        status.text = "Lösche …"
        Thread {
            val n = try {
                CalendarSync.deleteAll(applicationContext, cal.id)
            } catch (e: Exception) {
                -1
            }
            val msg = if (n >= 0) "$n App-Termine gelöscht, Auto-Sync gestoppt." else "Löschen fehlgeschlagen."
            prefs.status = msg
            runOnUiThread { status.text = msg }
        }.start()
    }
}
