package com.tpj.water

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
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
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Ma képernyő: vízjelenet a mai állással, egy fő akció (Ittam), lenyíló napló, alsó navigáció
class MainActivity : Activity() {
    private lateinit var pal: Palette
    private lateinit var mainLayout: LinearLayout
    private lateinit var scene: WaterScene
    private lateinit var totalTv: TextView
    private lateinit var targetTv: TextView
    private lateinit var nextChip: TextView
    private lateinit var nameTv: TextView
    private lateinit var bubble: TextView
    private lateinit var drinkBtn: LinearLayout
    private lateinit var drinkLabel: TextView
    private lateinit var logAccordion: LinearLayout
    private lateinit var timelineBox: LinearLayout
    private lateinit var warnBox: LinearLayout
    private lateinit var undoBar: LinearLayout
    private lateinit var undoText: TextView
    private lateinit var scrim: View
    private lateinit var sheet: LinearLayout

    private var shownTotal = 0
    private var countAnim: ValueAnimator? = null
    private var undoEntry: Entry? = null
    private var sheetBack: Any? = null

    private val amounts = listOf(150, 250, 330, 500)
    private val handler = Handler(Looper.getMainLooper())
    private val hideBubble = Runnable {
        bubble.animate().alpha(0f).setDuration(200).withEndAction { bubble.visibility = View.GONE }.start()
    }
    private val hideUndo = Runnable {
        undoEntry = null
        undoBar.animate().alpha(0f).translationY(dp(16).toFloat()).setDuration(160)
            .withEndAction { undoBar.visibility = View.GONE }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Water.setupDone(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        pal = Theme.p(this)
        themeWindow()

        // Fejléc: márkanév és a nap neve
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        header.addView(label("HydroFren", TypeScale.TITLE, color = if (pal.dark) pal.primary else pal.onSoft).apply {
            typeface = Fonts.display(this@MainActivity)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label(SimpleDateFormat("EEEE", Locale("hu")).format(Date()), TypeScale.CAPTION, color = pal.sub))

        // Vízjelenet a számokkal, a következő időponttal és a szövegbuborékkal
        scene = WaterScene(this).apply {
            cornerDp = 26
            // játék a karakterrel: koppintás, simogatás
            onTapCharacter = { PetSounds.voice(this@MainActivity) }
            onPetStart = { PetSounds.petStart(this@MainActivity) }
            onPetting = { PetSounds.petting(this@MainActivity) }
            onPetEnd = { PetSounds.stop() }
        }
        totalTv = label("0", TypeScale.DISPLAY).apply {
            typeface = Fonts.display(this@MainActivity)
            includeFontPadding = false
            letterSpacing = -0.02f
            tabular()
        }
        targetTv = label("", TypeScale.BODY, color = pal.sub).tabular() as TextView
        nextChip = label("", TypeScale.CAPTION, bold = true, color = pal.onSoft).apply {
            background = rect(if (pal.dark) Color.argb(220, Color.red(pal.surface), Color.green(pal.surface), Color.blue(pal.surface)) else Color.argb(225, 255, 255, 255), 14)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val numbers = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), 0)
            addView(totalTv)
            addView(targetTv)
            addView(nextChip, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10))
        }
        nameTv = label(Water.name(this), TypeScale.CAPTION, bold = true, center = true, color = Color.WHITE).apply {
            letterSpacing = 0.04f
        }
        bubble = label("", TypeScale.BODY, center = true).apply {
            background = rect(pal.surface, 18)
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
            ).apply { setMargins(dp(14), dp(150), 0, 0) })
            addView(nameTv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            ).apply { bottomMargin = dp(14) })
        }

        // Fő akció: Ittam
        drinkLabel = label("", 18f, color = pal.onPrimary).apply { typeface = Fonts.display(this@MainActivity) }
        drinkBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rippleOf(rect(pal.primary, 18))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_drop)
                imageTintList = ColorStateList.valueOf(pal.onPrimary)
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
            addView(drinkLabel)
            setOnClickListener { drink(Water.cup(this@MainActivity)) }
            setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                openSheet()
                true
            }
            pressFeedback()
        }

        // Másodlagos akciók: sport és más mennyiség
        val sportBtn = softButton(R.drawable.ic_run, "Sport +${Water.SPORT_ML}") {
            Water.addSport(this)
            say("Sportolás után pótolni kell a vizet!")
            refresh()
        }
        val moreBtn = softButton(R.drawable.ic_drop, "Más mennyiség") { openSheet() }
        val secondary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(sportBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
            addView(moreBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        }

        // Figyelmeztetés hiányzó engedélyekről
        warnBox = card().apply { visibility = View.GONE }

        // Mai ivások: lenyíló, összefoglaló sorral
        timelineBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        logAccordion = accordion("Mai ivások", "", timelineBox)

        val content = column().apply {
            addView(header)
            addView(sceneBox, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(300), top = 10))
            addView(drinkBtn, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(56), top = 14))
            addView(secondary, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 8))
            addView(warnBox)
            addView(logAccordion)
        }

        // Visszavonás-sáv a navigáció fölött
        undoText = label("", TypeScale.BODY, color = pal.bg)
        val undoBtn = label("Visszavonás", TypeScale.BODY, bold = true, color = if (pal.dark) pal.onSoft else pal.soft).apply {
            minHeight = dp(48)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(4), 0)
            isClickable = true
            setOnClickListener { undo() }
        }
        undoBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rect(pal.text, 14)
            setPadding(dp(16), 0, dp(12), 0)
            addView(undoText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(undoBtn)
            visibility = View.GONE
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
            applySystemBarPadding(top = true, bottom = false)
        }
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(undoBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                setMargins(dp(16), dp(4), dp(16), dp(6))
            })
            addView(Nav.bar(this@MainActivity, Nav.TODAY))
        }

        buildSheet()
        val root = FrameLayout(this).apply {
            addView(mainLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        setContentView(root)
        requestNotifPermission()
    }

    override fun onResume() {
        super.onResume()
        if (!Water.setupDone(this)) return
        // ha közben témát váltottál, a képernyő újraépül az új színekkel
        if (Theme.p(this).id != pal.id) {
            recreate()
            return
        }
        Water.scheduleNext(this)
        scene.look = currentLook()
        scene.pal = pal
        buildWarnings()
        refresh()
        if (Water.isReduced(this)) {
            say("Ma később kezdtük, ezért a mai cél ${Water.dailyTarget(this)} ml. Próbáljuk meghaladni!", 6000L)
        }
    }

    override fun onPause() {
        PetSounds.stop()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        countAnim?.cancel()
        super.onDestroy()
    }

    private fun softButton(icon: Int, text: String, onClick: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = rippleOf(rect(pal.soft, 14))
        addView(ImageView(this@MainActivity).apply { setImageDrawable(tinted(icon, pal.onSoft, 18)) },
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) })
        addView(label(text, 14f, bold = true, color = pal.onSoft))
        setOnClickListener { onClick() }
        pressFeedback()
    }

    // ---------- Mennyiségválasztó panel (alulról jön, lehúzással zárható) ----------
    private fun buildSheet() {
        scrim = View(this).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0))
            alpha = 0f
            visibility = View.GONE
            isClickable = true
            contentDescription = "Panel bezárása"
            setOnClickListener { closeSheet() }
        }
        val handleBar = View(this).apply { background = rect(pal.line, 2) }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        amounts.forEach { ml ->
            val isCup = ml == Water.cup(this)
            val b = label("$ml", 18f, center = true, color = if (isCup) pal.onPrimary else pal.text).apply {
                typeface = Fonts.display(this@MainActivity)
                tabular()
                background = rippleOf(rect(if (isCup) pal.primary else pal.field, 16))
                isClickable = true
                isFocusable = true
                contentDescription = "$ml milliliter"
                setOnClickListener {
                    closeSheet()
                    drink(ml)
                }
                pressFeedback()
            }
            grid.addView(b, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        val sheetBg = if (Theme.lite(this) || Build.VERSION.SDK_INT < 31) pal.surface
        else Color.argb(238, Color.red(pal.surface), Color.green(pal.surface), Color.blue(pal.surface))
        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(sheetBg)
                cornerRadii = floatArrayOf(dp(26).toFloat(), dp(26).toFloat(), dp(26).toFloat(), dp(26).toFloat(), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(16), dp(10), dp(16), dp(18))
            elevation = dp(12).toFloat()
            visibility = View.GONE
            isClickable = true
            addView(handleBar, LinearLayout.LayoutParams(dp(40), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(label("Mennyit ittál?", TypeScale.HEADLINE, bold = true).apply { setPadding(dp(4), dp(14), 0, dp(12)) })
            addView(grid)
            addView(label("Húzd le a bezáráshoz", TypeScale.CAPTION, center = true, color = pal.sub),
                lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 12))
            applySystemBarPadding(top = false, bottom = true)
        }
        attachSheetDrag()
    }

    private fun openSheet() {
        if (sheet.visibility == View.VISIBLE) return
        sheet.visibility = View.VISIBLE
        scrim.visibility = View.VISIBLE
        // mattüveg: a mögötte lévő képernyő elmosódik (Android 12+, Takarékos módban nem)
        if (Build.VERSION.SDK_INT >= 31 && !Theme.lite(this)) {
            mainLayout.setRenderEffect(RenderEffect.createBlurEffect(16f, 16f, Shader.TileMode.CLAMP))
        }
        sheet.post {
            if (animationsOn()) {
                sheet.translationY = sheet.height.toFloat()
                sheet.animate().translationY(0f).setDuration(240).setInterpolator(EASE_OUT).start()
                scrim.animate().alpha(1f).setDuration(240).setInterpolator(EASE_OUT).start()
            } else {
                sheet.translationY = 0f
                scrim.alpha = 1f
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val cb = OnBackInvokedCallback { closeSheet() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            sheetBack = cb
        }
    }

    private fun closeSheet() {
        if (sheet.visibility != View.VISIBLE) return
        if (Build.VERSION.SDK_INT >= 33) {
            (sheetBack as? OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            sheetBack = null
        }
        if (Build.VERSION.SDK_INT >= 31) mainLayout.setRenderEffect(null)
        val done = {
            sheet.visibility = View.GONE
            scrim.visibility = View.GONE
        }
        if (animationsOn()) {
            // a kilépés rövidebb a belépésnél
            sheet.animate().translationY(sheet.height.toFloat()).setDuration(190).setInterpolator(EASE_OUT).withEndAction { done() }.start()
            scrim.animate().alpha(0f).setDuration(190).start()
        } else done()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (sheet.visibility == View.VISIBLE) closeSheet() else super.onBackPressed()
    }

    // Lehúzás: a panel követi az ujjat; elég messzire húzva bezárul, különben visszaugrik
    private fun attachSheetDrag() {
        var downY = 0f
        var dragging = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        sheet.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = ev.rawY; dragging = false; v.animate().cancel(); true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.rawY - downY
                    if (!dragging && dy > slop) dragging = true
                    if (dragging) {
                        v.translationY = max(0f, dy)
                        scrim.alpha = 1f - min(1f, v.translationY / max(1, v.height))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging && v.translationY > v.height * 0.25f) closeSheet()
                    else {
                        v.animate().translationY(0f).setDuration(200).setInterpolator(EASE_OUT).start()
                        scrim.animate().alpha(1f).setDuration(200).start()
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Ivás ----------
    private fun drink(ml: Int) {
        Water.onDrink(this, ml)
        haptic(drinkBtn)
        scene.splash()
        playSound(this, R.raw.win_jingle)
        val snd = currentSpecies().sound
        handler.postDelayed({ playSound(this, snd) }, 650L)
        say(nextEncouragement(this))
        refresh()
    }

    private fun haptic(v: View) {
        if (Build.VERSION.SDK_INT >= 30) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        else v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun say(text: String, ms: Long = 4000L) {
        bubble.text = text
        bubble.alpha = 0f
        bubble.visibility = View.VISIBLE
        bubble.animate().alpha(1f).setDuration(200).setInterpolator(EASE_OUT).start()
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, ms)
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
        if (animationsOn()) {
            countAnim = ValueAnimator.ofInt(shownTotal, total).apply {
                duration = 700L
                interpolator = EASE_OUT
                addUpdateListener { totalTv.text = "${it.animatedValue}" }
                start()
            }
        } else totalTv.text = "$total"
        shownTotal = total

        scene.mood = Water.currentMood(this)
        scene.setLevel(Water.progress(this), animate = true)

        nextChip.text = when {
            Water.isBusy(this) -> "Szünet ${fmtHm(Water.startMin(this))}-ig"
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
        logAccordion.setAccordionSummary(
            if (entries.isEmpty()) "Még nincs ivás ma"
            else "${entries.size} ivás, utolsó ${fmt.format(Date(entries.maxOf { it.t }))}"
        )
        if (entries.isEmpty()) {
            timelineBox.addView(label("Az első pohár után itt látod a mai ivásokat.", TypeScale.BODY, color = pal.sub).apply {
                setPadding(0, dp(4), 0, dp(4))
            })
            return
        }
        timelineBox.addView(label("Húzd balra a törléshez", TypeScale.CAPTION, color = pal.sub).apply { setPadding(0, 0, 0, dp(4)) })
        entries.sortedByDescending { it.t }.forEach { e ->
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(pal.primary)
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setBackgroundColor(pal.surface)
                contentDescription = "${fmt.format(Date(e.t))}, ${e.ml} ml. Húzd balra a törléshez."
                addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(14) })
                addView(label(fmt.format(Date(e.t)), TypeScale.BODY, bold = true).tabular(),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(label("${e.ml} ml", TypeScale.BODY, color = pal.sub).tabular())
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
                MotionEvent.ACTION_DOWN -> { downX = ev.rawX; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    if (!dragging && abs(dx) > slop) {
                        dragging = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        v.translationX = min(0f, dx)
                        v.alpha = 1f - min(1f, abs(v.translationX) / max(1, v.width))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging && v.translationX < -v.width * 0.35f) {
                        v.animate().translationX(-v.width.toFloat()).alpha(0f).setDuration(150)
                            .withEndAction { deleteEntry(e) }.start()
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(180).setInterpolator(EASE_OUT).start()
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
        undoBar.translationY = dp(16).toFloat()
        undoBar.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(EASE_OUT).start()
        handler.removeCallbacks(hideUndo)
        handler.postDelayed(hideUndo, 5000L)
    }

    private fun undo() {
        val e = undoEntry ?: return
        Water.restoreEntry(this, e)
        Water.scheduleNext(this)
        refresh()
        handler.removeCallbacks(hideUndo)
        hideUndo.run()
    }

    // ---------- Engedély-figyelmeztetés ----------
    private fun buildWarnings() {
        warnBox.removeAllViews()
        val needExact = Build.VERSION.SDK_INT >= 31 &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val needFull = Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        val needBattery = !KeepAlive.batteryOk(this)
        if (!needExact && !needFull && !needBattery) {
            warnBox.visibility = View.GONE
            return
        }
        warnBox.visibility = View.VISIBLE
        warnBox.addView(label("Néhány beállítás hiányzik, ezért lehet, hogy nem szólok időben.", TypeScale.BODY))
        if (needExact) warnBox.addView(button("Pontos ébresztés engedélyezése", false) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        })
        if (needFull) warnBox.addView(button("Teljes képernyős értesítés", false) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        })
        if (needBattery) warnBox.addView(button("Akkumulátor-optimalizálás kikapcsolása", false) {
            KeepAlive.requestBatteryExemption(this)
        })
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
