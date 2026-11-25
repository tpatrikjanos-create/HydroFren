package com.tpj.water

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class Entry(val id: Long, val t: Long, val ml: Int)

object Water {
    private const val PREFS = "water"
    private const val DAY_MS = 86_400_000L
    const val MARGIN_MS = 30 * 60_000L     // a célt 30 perccel a vég előtt el kell érni
    const val REPEAT_MS = 10 * 60_000L     // ismétlés 10 percenként
    const val SPORT_ML = 500               // egy sportolás plusz célmennyisége
    const val HARD_MIN_MS = 45 * 60_000L   // ennél sűrűbben nem itat
    const val MAX_GAP_MS = 90 * 60_000L    // ennél ritkábban sem, ha sokkal előrébb vagyunk

    fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun goal(c: Context) = prefs(c).getInt("goal", 2500)
    fun cup(c: Context) = prefs(c).getInt("cup", 250)
    fun startMin(c: Context) = prefs(c).getInt("start", 6 * 60)
    fun endMin(c: Context) = prefs(c).getInt("end", 22 * 60)
    fun name(c: Context) = prefs(c).getString("name", "Cica") ?: "Cica"
    fun setupDone(c: Context) = prefs(c).getBoolean("setupDone", false)
    fun repeatOn(c: Context) = prefs(c).getBoolean("repeatOn", true)
    fun repeatMax(c: Context) = prefs(c).getInt("repeatMax", 3)
    // Elfoglalt: a kezdési időpontig (következő nap, ha már elmúlt) tart
    fun isBusy(c: Context, now: Long = System.currentTimeMillis()) =
        prefs(c).getBoolean("busy", false) && now < prefs(c).getLong("busyUntil", 0L)
    fun pending(c: Context) = prefs(c).getBoolean("pending", false)
    // Alap ivásköz a beállításokból (60 vagy 45 perc)
    fun prefGapMs(c: Context) = prefs(c).getInt("gapMin", 60) * 60_000L

    // Napi cél: 35 ml / kg, nemmel és korral módosítva, 50 ml-re kerekítve
    fun suggestedGoal(sex: Int, age: Int, weightKg: Int): Int {
        val sexF = when (sex) {
            0 -> 1.05
            1 -> 0.95
            else -> 1.0
        }
        val ageF = if (age >= 65) 0.9 else 1.0
        val raw = weightKg * 35.0 * sexF * ageF
        return ((raw / 50).roundToInt() * 50).coerceIn(1000, 5000)
    }

    // ---- Napló ----
    fun entries(c: Context): List<Entry> {
        val arr = JSONArray(prefs(c).getString("log", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.getLong("id"), o.getLong("t"), o.getInt("ml"))
        }.sortedBy { it.t }
    }

    private fun saveEntries(c: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("t", it.t).put("ml", it.ml)) }
        prefs(c).edit().putString("log", arr.toString()).apply()
    }

    fun dayKey(ms: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    fun todayEntries(c: Context, now: Long = System.currentTimeMillis()): List<Entry> =
        entries(c).filter { dayKey(it.t) == dayKey(now) }

    fun todayTotal(c: Context, now: Long = System.currentTimeMillis()): Int =
        todayEntries(c, now).sumOf { it.ml }

    fun addDrink(c: Context, ml: Int = cup(c)) {
        val now = System.currentTimeMillis()
        val keep = entries(c).filter { now - it.t < 60 * DAY_MS }
        saveEntries(c, keep + Entry(System.nanoTime(), now, ml))
    }

    fun removeEntry(c: Context, id: Long) {
        saveEntries(c, entries(c).filter { it.id != id })
    }

    // Visszavonás: a törölt bejegyzés visszakerül
    fun restoreEntry(c: Context, e: Entry) {
        saveEntries(c, entries(c).filter { it.id != e.id } + e)
    }

    // A mai előrehaladás 0..1 között (a vízszinthez)
    fun progress(c: Context, now: Long = System.currentTimeMillis()): Float {
        val target = dailyTarget(c, now)
        if (target <= 0) return 1f
        return (todayTotal(c, now).toFloat() / target).coerceIn(0f, 1f)
    }

    // A cica hangulata a lemaradás alapján: két pohárnyi lemaradás felett szomorú
    fun currentMood(c: Context, now: Long = System.currentTimeMillis()): Int {
        val total = todayTotal(c, now)
        val target = dailyTarget(c, now)
        if (total >= target) return HAPPY
        val (ws, we) = window(c, now)
        if (now < ws) return HAPPY
        val span = we - ws
        val frac = if (span <= 0) 1.0 else ((now - ws).toDouble() / span).coerceIn(0.0, 1.0)
        val deficit = target * frac - total
        return when {
            deficit > 2 * cup(c) -> SAD
            deficit > cup(c) -> NEUTRAL
            else -> HAPPY
        }
    }

    // Utolsó n nap: (dátum felirat, összes ml)
    fun lastDays(c: Context, n: Int): List<Pair<String, Int>> {
        val all = entries(c)
        val fmt = SimpleDateFormat("MM.dd", Locale.getDefault())
        val now = System.currentTimeMillis()
        return (n - 1 downTo 0).map { back ->
            val day = now - back * DAY_MS
            fmt.format(Date(day)) to all.filter { dayKey(it.t) == dayKey(day) }.sumOf { it.ml }
        }
    }

    // ---- Napi cél és ivásköz ----
    private fun window(c: Context, now: Long): Pair<Long, Long> =
        atMinute(now, startMin(c)) to (atMinute(now, endMin(c)) - MARGIN_MS)

    // Mennyit lehet még meginni a nap végéig a legsűrűbb (45 perces) tempóval
    fun achievableMl(c: Context, now: Long = System.currentTimeMillis()): Int {
        val (ws, we) = window(c, now)
        val from = maxOf(now, ws)
        if (we <= from) return 0
        val cups = ((we - from) / HARD_MIN_MS).toInt() + 1
        return cups * cup(c)
    }

    // Az aznapi tényleges cél. Csak akkor csökken, ha a sűrű tempó sem elég az eredeti célhoz.
    fun dailyTarget(c: Context, now: Long = System.currentTimeMillis()): Int {
        val full = goal(c) + sportToday(c, now)
        val total = todayTotal(c, now)
        val ach = achievableMl(c, now)
        if (full - total <= ach) return full
        val reachable = (total + ach) / 50 * 50
        return maxOf(reachable, total)
    }

    fun isReduced(c: Context, now: Long = System.currentTimeMillis()): Boolean =
        dailyTarget(c, now) < goal(c) + sportToday(c, now)

    // Ivásköz: lemaradásnál 45 perc, ha sokkal előrébb vagyunk, 1,5 óra, egyébként a beállított
    fun spacingMs(c: Context, now: Long = System.currentTimeMillis()): Long {
        val (ws, we) = window(c, now)
        val span = we - ws
        val frac = if (span <= 0) 1.0 else ((now - ws).toDouble() / span).coerceIn(0.0, 1.0)
        val expected = (goal(c) + sportToday(c, now)) * frac
        val deficit = expected - todayTotal(c, now)
        val pref = prefGapMs(c)
        val gap = when {
            deficit > cup(c) -> HARD_MIN_MS
            deficit < -cup(c) -> minOf(MAX_GAP_MS, pref * 3 / 2)
            else -> pref
        }
        return gap.coerceAtLeast(HARD_MIN_MS)
    }

    // Az utolsó ivás vagy emlékeztető, ettől számoljuk a következő időpontot
    private fun lastAnchor(c: Context, now: Long): Long {
        val (ws, _) = window(c, now)
        val lastDrink = todayEntries(c, now).maxOfOrNull { it.t } ?: 0L
        val lastRemind = prefs(c).getLong("lastRemind", 0L)
        return maxOf(lastDrink, lastRemind, ws)
    }

    fun computeNext(c: Context, now: Long = System.currentTimeMillis()): Long? {
        if (todayTotal(c, now) >= dailyTarget(c, now)) return null
        val (_, we) = window(c, now)
        if (now >= we) return null
        val next = maxOf(lastAnchor(c, now) + spacingMs(c, now), now + 5 * 60_000L)
        return if (next < we) next else null
    }

    fun sportToday(c: Context, now: Long = System.currentTimeMillis()): Int {
        val p = prefs(c)
        return if (p.getInt("sportDay", -1) == dayKey(now)) p.getInt("sportMl", 0) else 0
    }

    // Sportoltam: aznapi cél megnő SPORT_ML-lel
    fun addSport(c: Context, ml: Int = SPORT_ML) {
        val now = System.currentTimeMillis()
        val total = sportToday(c, now) + ml
        prefs(c).edit().putInt("sportDay", dayKey(now)).putInt("sportMl", total).apply()
        scheduleNext(c)
    }

    // ---- Ismétlések állapota ----
    fun startPending(c: Context) {
        prefs(c).edit()
            .putBoolean("pending", true)
            .putInt("repeatCount", 0)
            .putLong("lastRemind", System.currentTimeMillis())
            .apply()
    }

    fun endPending(c: Context) {
        prefs(c).edit().putBoolean("pending", false).putInt("repeatCount", 0).apply()
    }

    fun bumpRepeat(c: Context): Int {
        val n = prefs(c).getInt("repeatCount", 0) + 1
        prefs(c).edit().putInt("repeatCount", n).apply()
        return n
    }

    // "Elfoglalt vagyok": minden értesítés kikapcsol az app következő megnyitásáig
    fun setBusy(c: Context) {
        prefs(c).edit().putBoolean("busy", true).putLong("busyUntil", dayStartAt(c)).apply()
        endPending(c)
        cancelAll(c)
    }

    fun clearBusy(c: Context) {
        prefs(c).edit().putBoolean("busy", false).apply()
    }

    // Ittam: naplóz, lezárja az ismétléseket, újraszámol
    fun onDrink(c: Context, ml: Int = cup(c)) {
        addDrink(c, ml)
        endPending(c)
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(reminderPi(c, true))
        scheduleNext(c)
    }

    private fun atMinute(now: Long, minute: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ---- Ébresztések ----
    private fun flags(): Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun reminderPi(c: Context, repeat: Boolean): PendingIntent = PendingIntent.getBroadcast(
        c, if (repeat) 4 else 0,
        Intent(c, ReminderReceiver::class.java).putExtra("repeat", repeat),
        flags()
    )

    private fun testPi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 9, Intent(c, ReminderReceiver::class.java).putExtra("test", true), flags()
    )

    private fun openPi(c: Context, code: Int): PendingIntent =
        PendingIntent.getActivity(c, code, Intent(c, MainActivity::class.java), flags())

    fun canExact(c: Context): Boolean {
        val am = c.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun setAt(c: Context, at: Long, pi: PendingIntent, showCode: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (canExact(c)) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openPi(c, showCode)), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // Egyetlen ébresztés a következő normál emlékeztetőre
    fun scheduleNext(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(reminderPi(c, false))
        scheduleDayStart(c)
        // a kezdőképernyő-widget mindig a friss állapotot mutassa
        runCatching { HydroWidget.updateAll(c) }
        // háttérvédelem: óránkénti őrkutya és (ha be van kapcsolva) az állandó értesítés frissítése
        runCatching { KeepAlive.ensureWatchdog(c) }
        runCatching { KeepAlive.refreshStatus(c) }
        if (isBusy(c) || pending(c)) return
        val next = computeNext(c) ?: return
        setAt(c, next, reminderPi(c, false), 1)
    }

    fun scheduleRepeat(c: Context) {
        setAt(c, System.currentTimeMillis() + REPEAT_MS, reminderPi(c, true), 2)
    }

    fun cancelAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(reminderPi(c, false))
        am.cancel(reminderPi(c, true))
    }

    fun scheduleTest(c: Context, delayMs: Long = 5_000L) {
        setAt(c, System.currentTimeMillis() + delayMs, testPi(c), 8)
    }

    // Napi újraindítás a kezdési időpontban: következő előfordulás (ma vagy holnap)
    fun dayStartAt(c: Context, now: Long = System.currentTimeMillis()): Long {
        val today = atMinute(now, startMin(c))
        if (today > now) return today
        return Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    fun scheduleDayStart(c: Context) {
        setAt(c, dayStartAt(c), dayStartPi(c), 12)
    }

    private fun dayStartPi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 12, Intent(c, DayStartReceiver::class.java), flags()
    )
}
