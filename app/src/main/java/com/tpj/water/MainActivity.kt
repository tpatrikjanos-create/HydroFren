package com.tpj.water

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var scene: WaterScene
    private lateinit var totalTv: TextView
    private lateinit var targetTv: TextView
    private lateinit var nextChip: TextView
    private lateinit var nameTv: TextView
    private lateinit var bubble: TextView
    private lateinit var drinkBtn: LinearLayout
    private lateinit var drinkLabel: TextView
    private lateinit var amountRow: LinearLayout
    private lateinit var timelineBox: LinearLayout
    private lateinit var warnBox: LinearLayout
    private lateinit var undoBar: LinearLayout
    private lateinit var undoText: TextView

    private var shownTotal = 0
    private var countAnim: ValueAnimator? = null
    private var undoEntry: Entry? = null

    private val amounts = listOf(150, 250, 330, 500)
    private val handler = Handler(Looper.getMainLooper())
    private val hideBubble = Runnable {
        bubble.animate().alpha(0f).setDuration(300).withEndAction { bubble.visibility = View.GONE }.start()
    }
    private val hideUndo = Runnable {
        undoEntry = null
        undoBar.animate().alpha(0f).translationY(dp(20).toFloat()).setDuration(200)
            .withEndAction { undoBar.visibility = View.GONE }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Water.setupDone(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        // Fejléc: cím és három ikon (gyógyszer, statisztika, beállítások)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            label("HydroFren", TypeScale.TITLE, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(iconBtn(R.drawable.ic_pill, "Gyógyszer") {
            startActivity(Intent(this, MedsActivity::class.java))
        })
        header.addView(iconBtn(R.drawable.ic_chart, "Statisztika") {
            startActivity(Intent(this, StatsActivity::class.java))
        })
        header.addView(iconBtn(R.drawable.ic_gear, "Beállítások") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })

        // Vízjelenet a számokkal, a következő időponttal és a szövegbuborékkal
        scene = WaterScene(this)
        totalTv = label("0", TypeScale.DISPLAY, bold = true).apply {
            includeFontPadding = false
            letterSpacing = -0.02f
        }
        targetTv = label("", TypeScale.BODY, color = Theme.sub(this))
        nextChip = label("", TypeScale.CAPTION, bold = true, color = LAGOON).apply {
            background = rect(Color.argb(210, 255, 255, 255), 20)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val numbers = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), 0)
            addView(totalTv)
            addView(targetTv)
            addView(nextChip, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10))
        }
        nameTv = label(Water.name(this), TypeScale.CAPTION, bold = true, center = true, color = Color.WHITE).apply {
            letterSpacing = 0.05f
        }
        bubble = label("", TypeScale.BODY, center = true).apply {
            background = rect(Theme.card(this@MainActivity), 18)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            maxWidth = dp(180)
            elevation = dp(3).toFloat()
            visibility = View.GONE
        }
        val sceneBox = FrameLayout(this).apply {
            addView(scene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(numbers, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START
            ))
            addView(bubble, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START
            ).apply { setMargins(dp(16), dp(150), 0, 0) })
            addView(nameTv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            ).apply { bottomMargin = dp(16) })
        }

        // Széles Ittam gomb, hüvelykujjal elérhető helyen; nyomva tartva más mennyiség
        drinkLabel = label("", 19f, bold = true, color = Color.WHITE)
        drinkBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rippleOf(
                GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(TIDE, LAGOON)).apply {
                    cornerRadius = dp(22).toFloat()
                }
            )
            elevation = dp(6).toFloat()
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_drop)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
            addView(drinkLabel)
        }
        drinkBtn.setOnClickListener { drink(Water.cup(this)) }
        drinkBtn.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            amountRow.visibility = if (amountRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            true
        }
        val hint = label("Tartsd nyomva más mennyiséghez", TypeScale.CAPTION, center = true, color = Theme.sub(this))
        amountRow = chipRow(amounts.map { "$it ml" }, -1) { i ->
            amountRow.visibility = View.GONE
            drink(amounts[i])
        }
        amountRow.visibility = View.GONE

        val sportBtn = button("Sportoltam  +${Water.SPORT_ML} ml", false) {
            Water.addSport(this)
            say("Sportolás után pótolni kell a vizet!")
            refresh()
        }
        sportBtn.setCompoundDrawablesRelative(tinted(R.drawable.ic_run, TIDE, 20), null, null, null)
        sportBtn.compoundDrawablePadding = dp(8)
        sportBtn.setPadding(dp(18), 0, dp(18), 0)
        sportBtn.layoutParams = lp(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48), top = 14).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }

        warnBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        warnBox.addView(button("Pontos ébresztés engedélyezése", false) { openExactAlarmSettings() })
        warnBox.addView(button("Teljes képernyős engedély", false) { openFullScreenSettings() })
        warnBox.addView(button("Akkumulátor-optimalizálás kikapcsolása", false) { KeepAlive.requestBatteryExemption(this) })

        // A mai ivások idővonala; balra húzva törölhető, visszavonással
        timelineBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val timelineCard = card().apply {
            addView(label("Mai ivások", TypeScale.HEADLINE, bold = true))
            addView(label("Húzd balra a törléshez", TypeScale.CAPTION, color = Theme.sub(this@MainActivity)))
            addView(timelineBox)
        }

        undoText = label("", TypeScale.BODY, color = Color.WHITE)
        val undoBtn = label("Visszavonás", TypeScale.BODY, bold = true, color = BUBBLE).apply {
            setPadding(dp(12), dp(8), dp(4), dp(8))
            setOnClickListener { undo() }
        }
        undoBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rect(LAGOON, 16)
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(6), dp(12), dp(6))
            addView(undoText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(undoBtn)
            visibility = View.GONE
        }

        val content = column().apply {
            addView(header)
            addView(sceneBox, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(440), top = 16))
            addView(drinkBtn, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(64), top = 18))
            addView(hint, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 6))
            addView(amountRow)
            addView(sportBtn)
            addView(warnBox)
            addView(timelineCard)
            addView(View(this@MainActivity), lp(1, dp(24)))
        }

        val root = FrameLayout(this).apply {
            addView(screen(content))
            addView(undoBar, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            ).apply { setMargins(dp(16), 0, dp(16), dp(16)) })
        }
        setContentView(root)

        requestNotifPermission()
    }

    override fun onResume() {
        super.onResume()
        if (!Water.setupDone(this)) return
        Water.scheduleNext(this)
        // Beállításokban változhatott a kinézet vagy a téma
        scene.look = currentLook()
        scene.dark = Theme.dark(this)
        val needExact = Build.VERSION.SDK_INT >= 31 &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val needFull = Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        val needBattery = !KeepAlive.batteryOk(this)
        warnBox.visibility = if (needExact || needFull || needBattery) View.VISIBLE else View.GONE
        refresh()
        if (Water.isReduced(this)) {
            say("Ma később kezdtük, ezért a mai cél ${Water.dailyTarget(this)} ml. Próbáljuk meghaladni!", 6000L)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        countAnim?.cancel()
        super.onDestroy()
    }

    // Ivás: naplóz, rezeg, csobban, a cica örül, a szint emelkedik
    private fun drink(ml: Int) {
        Water.onDrink(this, ml)
        haptic(drinkBtn)
        scene.splash()
        playSound(this, R.raw.win_jingle)
        handler.postDelayed({ playSound(this, R.raw.meow) }, 650L)
        say(nextEncouragement(this))
        refresh()
    }

    private fun haptic(v: View) {
        if (Build.VERSION.SDK_INT >= 30) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        else v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    // Szövegbuborék megjelenítése egy rövid ideig
    private fun say(text: String, ms: Long = 4000L) {
        bubble.text = text
        bubble.alpha = 0f
        bubble.visibility = View.VISIBLE
        bubble.animate().alpha(1f).setDuration(250).start()
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, ms)
    }

    private fun iconBtn(res: Int, desc: String, onClick: () -> Unit): ImageButton {
        val b = ImageButton(this)
        b.setImageResource(res)
        b.imageTintList = ColorStateList.valueOf(Theme.text(this))
        b.background = rippleOf(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Theme.card(this@MainActivity))
        })
        b.setPadding(dp(10), dp(10), dp(10), dp(10))
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        b.contentDescription = desc
        b.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(8) }
        b.setOnClickListener { onClick() }
        return b
    }

    private fun refresh() {
        val target = Water.dailyTarget(this)
        val total = Water.todayTotal(this)
        val next = Water.computeNext(this)
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        nameTv.text = Water.name(this)
        targetTv.text = "/ $target ml"
        drinkLabel.text = "Ittam · ${Water.cup(this)} ml"

        // a szám felpörög az új értékre
        countAnim?.cancel()
        countAnim = ValueAnimator.ofInt(shownTotal, total).apply {
            duration = 900L
            addUpdateListener { totalTv.text = "${it.animatedValue}" }
            start()
        }
        shownTotal = total

        scene.mood = Water.currentMood(this)
        scene.setLevel(Water.progress(this), animate = true)

        // a következő időpont egyetlen helyen jelenik meg
        nextChip.text = when {
            Water.pending(this) -> "Igyál most egy pohárral"
            next != null -> "Következő ${fmt.format(Date(next))}"
            total >= target -> "Mai cél teljesítve"
            else -> "Mára nincs több emlékeztető"
        }
        buildTimeline(fmt)
        runCatching { HydroWidget.updateAll(this) }
    }

    private fun buildTimeline(fmt: SimpleDateFormat) {
        timelineBox.removeAllViews()
        val entries = Water.todayEntries(this)
        if (entries.isEmpty()) {
            timelineBox.addView(label("Még nincs bevitel ma.", TypeScale.BODY, color = Theme.sub(this)).apply {
                setPadding(0, dp(10), 0, 0)
            })
        }
        entries.forEach { e ->
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(TIDE)
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                setBackgroundColor(Theme.card(this@MainActivity))
                contentDescription = "${fmt.format(Date(e.t))}, ${e.ml} ml. Húzd balra a törléshez."
                addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(14) })
                addView(
                    label(fmt.format(Date(e.t)), TypeScale.BODY, bold = true),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(label("${e.ml} ml", TypeScale.BODY, color = Theme.sub(this@MainActivity)))
            }
            attachSwipe(row, e)
            timelineBox.addView(row)
        }
    }

    // Balra húzás: a sor kicsúszik és törlődik, alul megjelenik a visszavonás
    private fun attachSwipe(row: View, e: Entry) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var dragging = false
        row.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    if (!dragging && abs(dx) > slop) {
                        dragging = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        v.translationX = min(0f, dx)
                        v.alpha = 1f - min(1f, abs(v.translationX) / v.width)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging && v.translationX < -v.width * 0.35f) {
                        v.animate().translationX(-v.width.toFloat()).alpha(0f).setDuration(160)
                            .withEndAction { deleteEntry(e) }.start()
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(160).start()
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun deleteEntry(e: Entry) {
        Water.removeEntry(this, e.id)
        undoEntry = e
        Water.scheduleNext(this)
        refresh()
        undoText.text = "Törölve: ${e.ml} ml"
        undoBar.visibility = View.VISIBLE
        undoBar.alpha = 0f
        undoBar.translationY = dp(20).toFloat()
        undoBar.animate().alpha(1f).translationY(0f).setDuration(200).start()
        handler.removeCallbacks(hideUndo)
        handler.postDelayed(hideUndo, 4000L)
    }

    private fun undo() {
        val e = undoEntry ?: return
        Water.restoreEntry(this, e)
        Water.scheduleNext(this)
        refresh()
        handler.removeCallbacks(hideUndo)
        hideUndo.run()
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun openExactAlarmSettings() {
        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }

    private fun openFullScreenSettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
    }
}
