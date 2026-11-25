package com.tpj.water

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.app.NotificationManagerCompat
import kotlin.math.min

// Fényudvar lassan lüktető körökkel, mintha egy csepp esne a vízbe.
// Éjszakai módban (gyógyszer) hold és csillagok is látszanak.
class PulseRings(c: Context, private val halo: Int, private val ring: Int, private val night: Boolean) : View(c) {
    private var t = 0f
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = halo }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ring
        style = Paint.Style.STROKE
        strokeWidth = 2f * c.resources.displayMetrics.density
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7E0FF.toInt() }
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsOn()) anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r0 = min(width, height) * 0.30f
        val rMax = min(width, height) * 0.49f
        // három, egymás után induló kör; animáció nélkül két álló kör
        if (animationsOn()) {
            for (i in 0 until 3) {
                val p = (t + i / 3f) % 1f
                ringPaint.alpha = (255 * (1f - p) * 0.9f).toInt()
                canvas.drawCircle(cx, cy, r0 + (rMax - r0) * p, ringPaint)
            }
        } else {
            ringPaint.alpha = 200
            canvas.drawCircle(cx, cy, r0 + (rMax - r0) * 0.45f, ringPaint)
            ringPaint.alpha = 120
            canvas.drawCircle(cx, cy, rMax, ringPaint)
        }
        canvas.drawCircle(cx, cy, r0, haloPaint)

        if (night) {
            val d = resources.displayMetrics.density
            // hold
            val mx = width - 34f * d
            val my = 26f * d
            canvas.drawCircle(mx, my, 13f * d, starPaint)
            canvas.save()
            val clip = Path().apply { addCircle(mx + 7f * d, my - 4f * d, 11f * d, Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawColor(0xFF1E1A3D.toInt())
            canvas.restore()
            // csillagok
            for ((x, y, r) in listOf(Triple(0.12f, 0.10f, 1.8f), Triple(0.30f, 0.04f, 1.3f), Triple(0.85f, 0.30f, 1.5f), Triple(0.08f, 0.40f, 1.2f))) {
                canvas.drawCircle(width * x, height * y, r * d, starPaint)
            }
        }
    }
}

// Teljes képernyős ébresztés: kind = water (víz) vagy pill (gyógyszer).
// 60 másodperc után magától bezárul.
class AlarmActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }
    private val amounts = listOf(150, 330, 500)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val isPill = intent.getStringExtra("kind") == "pill"
        val mood = intent.getIntExtra("mood", HAPPY)
        val medId = intent.getLongExtra("id", -1L)
        val due = if (isPill) Meds.dueWith(this, medId) else emptyList()
        val total = Water.todayTotal(this)
        val target = Water.dailyTarget(this)
        val cup = Water.cup(this)

        // Színek: víznél a választott téma, gyógyszernél nyugodt éjszakai színek
        val pal = Theme.p(this)
        val bg = if (isPill) 0xFF1E1A3D.toInt() else pal.bg
        val text = if (isPill) 0xFFF1EEFF.toInt() else pal.text
        val sub = if (isPill) 0xFFC9C0F2.toInt() else pal.sub
        val halo = if (isPill) 0xFF2E2760.toInt() else pal.soft
        val ring = if (isPill) 0xFF4D4290.toInt() else pal.waterBack
        val primary = if (isPill) 0xFFE7E0FF.toInt() else pal.primary
        val onPrimary = if (isPill) 0xFF2A2350.toInt() else pal.onPrimary
        window.decorView.setBackgroundColor(bg)

        val title = label(
            when {
                !isPill -> Water.name(this)
                due.size > 1 -> "Vitaminok"
                else -> due.firstOrNull()?.name ?: intent.getStringExtra("name") ?: "Vitamin"
            },
            30f, center = true, color = text
        ).apply { typeface = Fonts.display(this@AlarmActivity) }
        val msg = label(
            when {
                !isPill -> moodText(mood)
                due.size > 1 -> due.joinToString(", ") { it.name }
                else -> "Ideje bevenni"
            },
            TypeScale.HEADLINE, center = true, color = text
        )
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), 0)
            addView(title)
            addView(msg, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 4))
            if (isPill) addView(label("${fmtHm(due.firstOrNull()?.minute ?: 0)} · ha bevetted, nyomd meg a gombot",
                TypeScale.BODY, center = true, color = sub), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 4))
        }

        // Középen: fényudvar, lüktető körök, a cica a pohárral vagy pirulával
        val cat = CatView(this).apply {
            this.mood = mood
            offer = !isPill
            pill = isPill
        }
        val stage = FrameLayout(this).apply {
            addView(PulseRings(this@AlarmActivity, halo, ring, isPill),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(cat, FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER))
        }

        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(stage, LinearLayout.LayoutParams(dp(290), dp(290)))
        }
        // Víznél: cseppsor a mai poharakkal, alatta a mennyiség
        if (!isPill) {
            val cups = ((target + cup - 1) / cup).coerceIn(1, 12)
            val filled = (total / cup).coerceIn(0, cups)
            val drops = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                contentDescription = "$filled / $cups pohár"
                for (i in 0 until cups) {
                    addView(ImageView(this@AlarmActivity).apply {
                        setImageDrawable(tinted(R.drawable.ic_drop, if (i < filled) primary else pal.line, 18))
                    }, LinearLayout.LayoutParams(dp(18), dp(20)).apply { setMargins(dp(2), 0, dp(2), 0) })
                }
            }
            middle.addView(drops)
            middle.addView(label("$total / $target ml · még ${(cups - filled).coerceAtLeast(0)} pohár", TypeScale.BODY, center = true, color = sub).tabular(),
                lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 6))
        }

        // Gombok alul, hüvelykujjal elérhető helyen
        val primaryBtn = label(
            when {
                !isPill -> "Ittam · $cup ml"
                due.size > 1 -> "Bevettem mindet"
                else -> "Bevettem"
            },
            18f, center = true, color = onPrimary
        ).apply {
            typeface = Fonts.display(this@AlarmActivity)
            background = rippleOf(rect(primary, 18))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (isPill) {
                    due.forEach { Meds.markTaken(this@AlarmActivity, it.id) }
                    finish()
                } else drink(cup)
            }
            pressFeedback()
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), 0, dp(24), dp(16))
            addView(primaryBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        }
        if (!isPill) {
            bottom.addView(chipRow(amounts.map { "$it ml" }, -1) { drink(amounts[it]) })
        }
        bottom.addView(textAction("Később (10 perc)", text) {
            if (isPill) due.forEach { NotificationManagerCompat.from(this).cancel(Meds.notifId(it.id)) }
            else NotificationManagerCompat.from(this).cancel(NOTIF_ID)
            finish()
        })
        if (!isPill) {
            bottom.addView(textAction("Szünet ${fmtHm(Water.startMin(this))}-ig", sub) {
                Water.setBusy(this)
                NotificationManagerCompat.from(this).cancel(NOTIF_ID)
                finish()
            })
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            addView(top)
            addView(View(this@AlarmActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(middle)
            addView(View(this@AlarmActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(bottom)
            applySystemBarPadding(top = true, bottom = true)
        }
        setContentView(root)
        handler.postDelayed(autoClose, 60_000L)
    }

    private fun textAction(text: String, color: Int, onClick: () -> Unit): View =
        label(text, TypeScale.BODY, bold = true, center = true, color = color).apply {
            minHeight = dp(48)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rippleOf(rect(Color.TRANSPARENT, 14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) }
            setOnClickListener { onClick() }
        }

    private fun drink(ml: Int) {
        Water.onDrink(this, ml)
        NotificationManagerCompat.from(this).cancel(NOTIF_ID)
        playSound(this, currentSpecies().sound)
        finish()
    }

    // Ismétlésnél a már nyitott ébresztő képernyő is frissül az új adatokkal
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoClose)
        super.onDestroy()
    }
}
