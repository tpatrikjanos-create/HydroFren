package com.tpj.water

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Háttérvédelem: akkor is szóljon az app, ha napokig nem nyitod meg.
object KeepAlive {
    private const val JOB_ID = 42
    private const val STATUS_CHANNEL = "hydro_status_v1"
    const val STATUS_NOTIF_ID = 1003

    // Óránkénti őrkutya: a rendszer ütemezője hívja meg, újraindítás után is
    fun ensureWatchdog(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(c, WatchdogJob::class.java))
            .setPeriodic(60 * 60 * 1000L)
            .setPersisted(true)
            .build()
        runCatching { js.schedule(job) }
    }

    // Minden ébresztés újraélesztése (őrkutya, újraindítás, frissítés, óraállítás után)
    fun rearmAll(c: Context) {
        Water.scheduleNext(c)
        Meds.scheduleAll(c)
        ensureWatchdog(c)
    }

    // Akkumulátor-optimalizálás: ha be van kapcsolva, a telefon leállíthatja az ébresztéseket
    fun batteryOk(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(c.packageName) ?: true

    fun requestBatteryExemption(c: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { c.startActivity(direct) }.onFailure { runCatching { c.startActivity(list) } }
    }

    // Gyártói beállítások (pl. Xiaomi "Automatikus indítás", Samsung "Alvó appok") az app adatlapjáról érhetők el
    fun openAppDetails(c: Context) {
        runCatching {
            c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")))
        }
    }

    // ---- Opcionális állandó értesítés (előtérszolgáltatás) ----
    fun statusOn(c: Context): Boolean = Water.prefs(c).getBoolean("statusOn", false)

    fun setStatus(c: Context, on: Boolean) {
        Water.prefs(c).edit().putBoolean("statusOn", on).apply()
        if (on) refreshStatus(c) else c.stopService(Intent(c, StatusService::class.java))
    }

    // Ha be van kapcsolva: friss tartalom, vagy a szolgáltatás elindítása, ha nem fut
    fun refreshStatus(c: Context) {
        if (!statusOn(c)) return
        if (StatusService.running) {
            if (canNotify(c)) c.getSystemService(NotificationManager::class.java).notify(STATUS_NOTIF_ID, buildStatus(c))
        } else {
            runCatching {
                c.startForegroundService(Intent(c, StatusService::class.java))
            }
        }
    }

    private fun canNotify(c: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ActivityCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureStatusChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(STATUS_CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, "Mai állás (állandó)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Néma értesítés a mai állással; életben tartja az appot a háttérben"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun buildStatus(c: Context): android.app.Notification {
        ensureStatusChannel(c)
        val total = Water.todayTotal(c)
        val target = Water.dailyTarget(c)
        val next = Water.computeNext(c)
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val tail = when {
            Water.isBusy(c) -> "szünet ${fmtHm(Water.startMin(c))}-ig"
            next != null -> "következő ${fmt.format(Date(next))}"
            total >= target -> "mai cél teljesítve"
            else -> "mára nincs több emlékeztető"
        }
        val open = PendingIntent.getActivity(
            c, 40, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(c, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notif_drop)
            .setColor(Color.parseColor("#1E88C9"))
            .setContentTitle("$total / $target ml")
            .setContentText(tail)
            .setProgress(target, total.coerceAtMost(target), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }
}

// Óránként lefut: ha valamelyik ébresztés elveszett, újra beállítja
class WatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        runCatching { KeepAlive.rearmAll(this) }
        runCatching { KeepAlive.refreshStatus(this) }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

// Előtérszolgáltatás az állandó értesítéshez; a rendszer nem állítja le az appot, amíg fut
class StatusService : Service() {
    companion object {
        @Volatile
        var running = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!KeepAlive.statusOn(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching {
            ServiceCompat.startForeground(this, KeepAlive.STATUS_NOTIF_ID, KeepAlive.buildStatus(this), type)
            running = true
        }.onFailure { stopSelf() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
