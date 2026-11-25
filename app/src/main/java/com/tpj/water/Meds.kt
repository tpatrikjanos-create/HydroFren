package com.tpj.water

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// új csatorna a saját csengőhang miatt (a hangot az Android a létrehozáskor rögzíti)
const val MED_CHANNEL_ID = "hydro_meds_v2"

data class Med(val id: Long, val minute: Int, val name: String)

// Vitamin / gyógyszer emlékeztetők: napi időpont, ismétlés nem vétel esetén
object Meds {
    fun list(c: Context): List<Med> {
        val arr = JSONArray(Water.prefs(c).getString("meds", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Med(o.getLong("id"), o.getInt("min"), o.getString("name"))
        }.sortedBy { it.minute }
    }

    private fun save(c: Context, list: List<Med>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("min", it.minute).put("name", it.name)) }
        Water.prefs(c).edit().putString("meds", arr.toString()).apply()
    }

    fun add(c: Context, minute: Int, name: String) {
        val m = Med(System.nanoTime(), minute, name)
        save(c, list(c) + m)
        schedule(c, m)
    }

    fun remove(c: Context, id: Long) {
        cancel(c, id)
        save(c, list(c).filter { it.id != id })
    }

    fun scheduleAll(c: Context) {
        list(c).forEach { schedule(c, it) }
    }

    // Napi reset a kezdési időpontban: a tegnapi ismétlések törlődnek, minden gyógyszer a következő előfordulásra áll
    fun dailyReset(c: Context) {
        list(c).forEach { med ->
            cancel(c, med.id)
            schedule(c, med)
        }
    }

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun requestCode(id: Long, repeat: Boolean): Int =
        (id % 100_000).toInt() * 2 + (if (repeat) 1 else 0) + 100

    private fun pi(c: Context, id: Long, repeat: Boolean, n: Int): PendingIntent {
        val action = if (repeat) "medr_$id" else "med_$id"
        val intent = Intent(c, MedReceiver::class.java)
            .setAction(action)
            .putExtra("id", id)
            .putExtra("n", n)
        return PendingIntent.getBroadcast(
            c, requestCode(id, repeat), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // A következő előfordulás: ma, ha még nem volt, különben holnap
    private fun nextOccurrence(minute: Int, now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now + 60_000L) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun schedule(c: Context, med: Med) {
        val at = nextOccurrence(med.minute, System.currentTimeMillis())
        Water.setAt(c, at, pi(c, med.id, false, 0), 10)
    }

    fun scheduleRepeat(c: Context, id: Long, n: Int) {
        Water.setAt(c, System.currentTimeMillis() + Water.REPEAT_MS, pi(c, id, true, n), 11)
    }

    fun cancel(c: Context, id: Long) {
        am(c).cancel(pi(c, id, false, 0))
        am(c).cancel(pi(c, id, true, 0))
    }

    fun isTaken(c: Context, id: Long, now: Long = System.currentTimeMillis()): Boolean =
        Water.prefs(c).getInt("medTaken_$id", -1) == Water.dayKey(now)

    // Bevettem: mára kész, az ismétlések leállnak, a következő nap ütemezve marad
    // Gyógyszerenként külön értesítés, így az egyszerre esedékesek nem írják felül egymást
    fun notifId(id: Long): Int = 2000 + (id % 100_000).toInt()

    // Az ugyanabban az időpontban esedékes, még be nem vett gyógyszerek
    fun dueWith(c: Context, id: Long): List<Med> {
        val all = list(c)
        val med = all.firstOrNull { it.id == id } ?: return emptyList()
        return all.filter { it.minute == med.minute && !isTaken(c, it.id) }.ifEmpty { listOf(med) }
    }

    fun markTaken(c: Context, id: Long) {
        Water.prefs(c).edit().putInt("medTaken_$id", Water.dayKey(System.currentTimeMillis())).apply()
        am(c).cancel(pi(c, id, true, 0))
        NotificationManagerCompat.from(c).cancel(notifId(id))
    }

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("hydro_meds_v1")
        if (nm.getNotificationChannel(MED_CHANNEL_ID) != null) return
        val sound = android.net.Uri.parse("android.resource://${c.packageName}/${R.raw.med_chime}")
        val ch = NotificationChannel(MED_CHANNEL_ID, "Vitamin és gyógyszer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lágy csengő, hogy hallásból is megkülönböztethető legyen a víztől"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 450, 250, 450)
            setSound(
                sound,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(ch)
    }

    fun show(c: Context, med: Med, mood: Int) {
        ensureChannel(c)
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val full = PendingIntent.getActivity(
            c, 300_000 + (med.id % 100_000).toInt(),
            Intent(c, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("kind", "pill")
                .putExtra("mood", mood)
                .putExtra("name", med.name)
                .putExtra("id", med.id),
            flags
        )
        val taken = PendingIntent.getBroadcast(
            c, 400_000 + (med.id % 100_000).toInt(),
            Intent(c, MedActionReceiver::class.java).setAction("med_taken").putExtra("id", med.id),
            flags
        )
        val later = PendingIntent.getBroadcast(
            c, 500_000 + (med.id % 100_000).toInt(),
            Intent(c, MedActionReceiver::class.java).setAction("med_later").putExtra("id", med.id),
            flags
        )

        // zárolt képernyőn a gyógyszer neve nem látszik
        val public = NotificationCompat.Builder(c, MED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_drop)
            .setColor(Color.parseColor("#7C5CFA"))
            .setContentTitle("Ideje bevenni a vitamint")
            .build()

        val n = NotificationCompat.Builder(c, MED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_drop)
            .setColor(Color.parseColor("#7C5CFA"))
            .setLargeIcon(Mascot.bitmap(c, 256, mood, false, true))
            .setContentTitle("Ideje bevenni")
            .setContentText(med.name)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Bevettem", taken)
            .addAction(0, "Később (10 perc)", later)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).cancel(notifId(med.id))
        NotificationManagerCompat.from(c).notify(notifId(med.id), n)
    }
}

// Gyógyszer-ébresztés: az első értesítés, majd az ismétlések, ha nem veszik be
class MedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        val n = intent.getIntExtra("n", 0)
        val med = Meds.list(c).firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        if (Meds.isTaken(c, id, now)) return

        if (n == 0) Meds.schedule(c, med) // a következő napra állítja

        val mood = when (n) {
            0 -> HAPPY
            1 -> NEUTRAL
            2 -> SAD
            else -> ANGRY
        }
        Meds.show(c, med, mood)

        if (Water.repeatOn(c) && n < Water.repeatMax(c)) {
            Meds.scheduleRepeat(c, id, n + 1)
        }
    }
}

// Értesítés gombjai: Bevettem / Később
class MedActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (intent.action == "med_taken") {
            Meds.markTaken(c, id)
        } else {
            // Később: az ismétlés már ütemezve van, csak az értesítés tűnik el
            NotificationManagerCompat.from(c).cancel(Meds.notifId(id))
        }
    }
}
