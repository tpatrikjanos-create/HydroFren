package com.tpj.water

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// Az Android a csatorna hangját az első létrehozáskor rögzíti, ezért új azonosító kell a hang váltásához
const val CHANNEL_ID = "hydro_drop_v4"
const val NOTIF_ID = 1001

fun moodText(mood: Int): String = when (mood) {
    HAPPY -> "Egy pohár víz vár rád"
    NEUTRAL -> "Még nem ittál. Egy kortyot?"
    SAD -> "Ugye nem felejtesz el inni?"
    ANGRY -> "Ez már nem vicces! Igyál egy pohár vizet!"
    else -> "Kérlek, igyál egy pohár vizet"
}

// Vízcsepp hang, egy rezgés
fun ensureChannel(c: Context) {
    val nm = c.getSystemService(NotificationManager::class.java)
    // a régi csatornák hangját már nem lehet felülírni, ezért töröljük őket
    nm.deleteNotificationChannel("hydro_drop_v2")
    nm.deleteNotificationChannel("hydro_drop_v3")
    if (nm.getNotificationChannel(CHANNEL_ID) != null) return
    val soundUri = Uri.parse("android.resource://${c.packageName}/${R.raw.water_drop}")
    val ch = NotificationChannel(CHANNEL_ID, "HydroFren emlékeztető", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Mély, visszhangos vízcsepp és egy rezgés"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 450)
        setSound(
            soundUri,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
    }
    nm.createNotificationChannel(ch)
}

fun showReminder(c: Context, mood: Int, left: Int) {
    ensureChannel(c)
    if (Build.VERSION.SDK_INT >= 33 &&
        ActivityCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val total = Water.todayTotal(c)
    val target = Water.dailyTarget(c)
    val cupsLeft = (left.coerceAtLeast(0) + Water.cup(c) - 1) / Water.cup(c)
    val statusLine = "$total / $target ml · még $cupsLeft pohár"

    val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    val full = PendingIntent.getActivity(
        c, 2,
        Intent(c, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("kind", "water")
            .putExtra("mood", mood)
            .putExtra("left", left),
        flags
    )
    val drink = PendingIntent.getBroadcast(c, 3, Intent(c, DrinkReceiver::class.java).setAction("drink"), flags)
    val later = PendingIntent.getBroadcast(c, 5, Intent(c, LaterReceiver::class.java).setAction("later"), flags)
    val busy = PendingIntent.getBroadcast(c, 6, Intent(c, BusyReceiver::class.java).setAction("busy"), flags)

    val n = NotificationCompat.Builder(c, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notif_drop)
        .setColor(Color.parseColor("#1E88C9"))
        .setLargeIcon(Mascot.bitmap(c, 256, mood, true))
        // a cím a cica üzenete, a szöveg a mai állás
        .setContentTitle(moodText(mood))
        .setContentText(statusLine)
        .setProgress(target, total.coerceAtMost(target), false)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setFullScreenIntent(full, true)
        .setContentIntent(full)
        .addAction(0, "Ittam · ${Water.cup(c)}", drink)
        .addAction(0, "Később", later)
        .addAction(0, "Szünet ${fmtHm(Water.startMin(c))}-ig", busy)
        .setAutoCancel(true)
        .build()
    // az előző értesítést töröljük, hogy az Android az ismétlésnél is új értesítésként kezelje,
    // és újra megnyissa a teljes képernyőt
    NotificationManagerCompat.from(c).cancel(NOTIF_ID)
    NotificationManagerCompat.from(c).notify(NOTIF_ID, n)
}

// Ütemezett emlékeztető (normál vagy ismétlés), vagy teszt
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.getBooleanExtra("test", false)) {
            showReminder(c, HAPPY, Water.dailyTarget(c) - Water.todayTotal(c))
            return
        }
        if (Water.isBusy(c)) return
        val left = Water.dailyTarget(c) - Water.todayTotal(c)

        if (intent.getBooleanExtra("repeat", false)) {
            if (!Water.pending(c)) return
            val n = Water.bumpRepeat(c)
            val mood = when (n) {
                1 -> NEUTRAL
                2 -> SAD
                else -> ANGRY
            }
            showReminder(c, mood, left)
            if (n < Water.repeatMax(c)) {
                Water.scheduleRepeat(c)
            } else {
                Water.endPending(c)
                Water.scheduleNext(c)
            }
        } else {
            if (left <= 0) {
                Water.scheduleNext(c)
                return
            }
            Water.startPending(c)
            showReminder(c, HAPPY, left)
            if (Water.repeatOn(c) && Water.repeatMax(c) > 0) {
                Water.scheduleRepeat(c)
            } else {
                Water.endPending(c)
                Water.scheduleNext(c)
            }
        }
    }
}

// Értesítés: Ittam
class DrinkReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Water.onDrink(c)
        NotificationManagerCompat.from(c).cancel(NOTIF_ID)
    }
}

// Értesítés: Később (az ismétlés már ütemezve van 10 percre)
class LaterReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        NotificationManagerCompat.from(c).cancel(NOTIF_ID)
    }
}

// Értesítés: Elfoglalt vagyok
class BusyReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Water.setBusy(c)
        NotificationManagerCompat.from(c).cancel(NOTIF_ID)
    }
}

// Újraindítás és app-frissítés után a rendszer minden ébresztést töröl, óraállításkor elcsúszhatnak.
// Ilyenkor mindent újra beállítunk, az app megnyitása nélkül.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // a félbemaradt ismétlés-lánc ébresztései elvesztek
                Water.endPending(c)
                KeepAlive.rearmAll(c)
            }
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> KeepAlive.rearmAll(c)
        }
    }
}

// Minden nap a kezdési időpontban: busy és függő állapot törlése, újraütemezés
class DayStartReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Water.clearBusy(c)
        Water.endPending(c)
        Water.scheduleNext(c)
        Meds.dailyReset(c)
    }
}
