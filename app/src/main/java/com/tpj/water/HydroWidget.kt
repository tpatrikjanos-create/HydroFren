package com.tpj.water

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

// Kezdőképernyő-widget: a cica a vízszinttel, a mai állással és egy "Ittam" gombbal
class HydroWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        val v = views(c)
        ids.forEach { mgr.updateAppWidget(it, v) }
    }

    companion object {
        // Minden ivás, törlés és újraütemezés után frissül
        fun updateAll(c: Context) {
            val mgr = AppWidgetManager.getInstance(c) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(c, HydroWidget::class.java))
            if (ids.isEmpty()) return
            val v = views(c)
            ids.forEach { mgr.updateAppWidget(it, v) }
        }

        private fun views(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget_hydro)
            v.setImageViewBitmap(R.id.widget_img, WaterArt.bitmap(c, 360, 360))
            v.setTextViewText(R.id.widget_text, "${Water.todayTotal(c)} / ${Water.dailyTarget(c)} ml")
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            v.setOnClickPendingIntent(
                R.id.widget_drink,
                PendingIntent.getBroadcast(c, 30, Intent(c, DrinkReceiver::class.java).setAction("drink_widget"), flags)
            )
            v.setOnClickPendingIntent(
                R.id.widget_img,
                PendingIntent.getActivity(c, 31, Intent(c, MainActivity::class.java), flags)
            )
            return v
        }
    }
}
