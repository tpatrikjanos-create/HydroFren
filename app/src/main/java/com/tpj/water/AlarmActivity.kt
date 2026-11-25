package com.tpj.water

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.app.NotificationManagerCompat

// Teljes képernyős ébresztés a vízjelenettel a háttérben.
// kind = water: víz-emlékeztető; kind = pill: gyógyszer, nyugodt éjszakai színekkel.
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
        // az ugyanabban az időpontban esedékes gyógyszerek együtt jelennek meg
        val due = if (isPill) Meds.dueWith(this, medId) else emptyList()
        val total = Water.todayTotal(this)
        val target = Water.dailyTarget(this)
        val left = (target - total).coerceAtLeast(0)

        // háttér: a vízjelenet; a cica a képernyő közepén maradjon, a szövegek és a gombok között
        val scene = WaterScene(this).apply {
            cornerDp = 0
            catCenter = 0.5f
            this.mood = mood
            offer = !isPill
            pill = isPill
            night = isPill
            setLevel(Water.progress(this@AlarmActivity).coerceIn(0.38f, 0.75f), animate = true)
        }

        val onLight = !isPill && !Theme.dark(this)
        val textColor = if (onLight) LAGOON else Color.WHITE
        val subColor = if (onLight) TIDE else 0xFFD7E3FF.toInt()

        val title = label(
            when {
                !isPill -> Water.name(this)
                due.size > 1 -> "Vitaminok"
                else -> due.firstOrNull()?.name ?: intent.getStringExtra("name") ?: "Vitamin"
            },
            30f, bold = true, center = true, color = textColor
        )
        val msg = label(
            when {
                !isPill -> moodText(mood)
                due.size > 1 -> due.joinToString(", ") { it.name }
                else -> "Ideje bevenni"
            },
            TypeScale.HEADLINE, center = true, color = textColor
        )
        val sub = label(
            if (isPill) "${fmtHm(due.firstOrNull()?.minute ?: 0)} · ha bevetted, nyomd meg a gombot"
            else "$total / $target ml · még $left ml",
            TypeScale.BODY, center = true, color = subColor
        )
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(56), dp(28), 0)
            addView(title)
            addView(msg, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 6))
            addView(sub, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 6))
        }

        val primary = button(
            when {
                !isPill -> "Ittam · ${Water.cup(this)} ml"
                due.size > 1 -> "Bevettem mindet"
                else -> "Bevettem"
            },
            true
        ) {
            if (isPill) {
                due.forEach { Meds.markTaken(this, it.id) }
                finish()
            } else {
                drink(Water.cup(this))
            }
        }
        primary.background = rippleOf(rect(Color.WHITE, 30))
        primary.setTextColor(if (isPill) 0xFF2A2350.toInt() else LAGOON)
        primary.layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))

        val later = button("Később (10 perc)", false) {
            if (isPill) due.forEach { NotificationManagerCompat.from(this).cancel(Meds.notifId(it.id)) }
            else NotificationManagerCompat.from(this).cancel(NOTIF_ID)
            finish()
        }
        later.setTextColor(Color.WHITE)
        later.background = rippleOf(rect(Color.argb(40, 255, 255, 255), 16))
        later.layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), top = 10)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), 0, dp(28), dp(40))
            addView(primary)
        }
        // víznél más mennyiség is választható
        if (!isPill) {
            bottom.addView(chipRow(amounts.map { "$it ml" }, -1) { drink(amounts[it]) })
        }
        bottom.addView(later)
        // víznél szünet a következő kezdési időpontig
        if (!isPill) {
            val busy = button("Szünet ${fmtHm(Water.startMin(this))}-ig", false) {
                Water.setBusy(this)
                NotificationManagerCompat.from(this).cancel(NOTIF_ID)
                finish()
            }
            busy.setTextColor(Color.WHITE)
            busy.background = rippleOf(rect(Color.TRANSPARENT, 16))
            busy.layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), top = 4)
            bottom.addView(busy)
        }

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(top)
            addView(View(this@AlarmActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(bottom)
        }

        val root = FrameLayout(this).apply {
            addView(scene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)

        handler.postDelayed(autoClose, 60_000L)
    }

    private fun drink(ml: Int) {
        Water.onDrink(this, ml)
        NotificationManagerCompat.from(this).cancel(NOTIF_ID)
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
