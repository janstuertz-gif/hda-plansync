package app.hdaplan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDateTime

/** Homescreen-Widget mit den nächsten Terminen (liest nur den Cache, braucht kein Netz). */
class NextWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val rv = build(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, rv)
    }

    companion object {
        fun refresh(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, NextWidget::class.java))
                if (ids.isEmpty()) return
                val rv = build(ctx)
                for (id in ids) mgr.updateAppWidget(id, rv)
            } catch (e: Exception) {
                // Widget ist optional
            }
        }

        private fun build(ctx: Context): RemoteViews {
            val st = Store(ctx)
            val refs = st.plans
            val plans = HashMap<String, Plan>()
            val sel = HashMap<String, List<Sel>>()
            for (r in refs) {
                val p = st.loadPlan(r.id)
                if (p != null) plans[r.id] = p
                sel[r.id] = st.selection(r.id)
            }
            val aliases = st.aliases
            val now = LocalDateTime.now()
            val next = Occurrences.build(refs, plans, sel, st.hidden)
                .filter { !it.hidden && it.end.isAfter(now) }
                .take(4)

            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            val rows = intArrayOf(R.id.w_r1, R.id.w_r2, R.id.w_r3, R.id.w_r4)
            for (i in rows.indices) {
                if (i < next.size) {
                    val o = next[i]
                    val s = o.slot
                    val day = if (o.date == now.toLocalDate()) "" else Fmt.dayShort(o.date.dayOfWeek) + " "
                    val tag = if (s.type == '?') "" else " (" + s.type + ")"
                    val room = if (s.rooms.isEmpty()) "" else "\n" + s.rooms
                    rv.setTextViewText(rows[i], day + s.start + " " + Fmt.display(s, aliases) + tag + room)
                    rv.setViewVisibility(rows[i], View.VISIBLE)
                } else {
                    rv.setViewVisibility(rows[i], View.GONE)
                }
            }
            rv.setViewVisibility(R.id.w_empty, if (next.isEmpty()) View.VISIBLE else View.GONE)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)
            return rv
        }
    }
}
