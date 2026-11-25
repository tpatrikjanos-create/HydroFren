package com.tpj.water

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

// Oszlopdiagram: egy oszlop naponként, a szaggatott vonal a napi cél.
// A célt elérő napok teljes színűek, a többi halványabb; a mai nap kerettel kiemelve.
class BarChart(c: Context, private val pal: Palette) : View(c) {
    private var labels: List<String> = emptyList()
    private var values: List<Int> = emptyList()
    private var goal = 2500
    private val density = c.resources.displayMetrics.density

    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primary }
    private val lowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.soft }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.primary
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.sub
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(8f, 7f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.sub
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        typeface = Fonts.ui(c, false)
    }

    fun setData(l: List<String>, v: List<Int>, g: Int) {
        labels = l
        values = v
        goal = g
        contentDescription = "Napi ivás, cél $g ml: " + l.zip(v).joinToString(", ") { "${it.first} ${it.second} ml" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = values.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val labelH = 20f * density
        val chartH = h - labelH - 4f * density
        val maxV = maxOf(goal, values.maxOrNull() ?: 0).toFloat().coerceAtLeast(1f) * 1.08f
        val slot = w / n
        val barW = slot * if (n > 10) 0.7f else 0.58f
        val radius = (if (n > 10) 3f else 7f) * density

        values.forEachIndexed { i, v ->
            val bh = (chartH * (v / maxV)).coerceAtLeast(2f * density)
            val left = i * slot + (slot - barW) / 2f
            val r = RectF(left, chartH - bh, left + barW, chartH)
            canvas.drawRoundRect(r, radius, radius, if (v >= goal) okPaint else lowPaint)
            if (i == n - 1) canvas.drawRoundRect(RectF(r.left - 3f * density, r.top - 3f * density, r.right + 3f * density, r.bottom), radius, radius, todayPaint)
            if (n <= 10 || i % 5 == 0 || i == n - 1) {
                canvas.drawText(labels.getOrElse(i) { "" }, left + barW / 2f, h - 4f * density, textPaint)
            }
        }
        val gy = chartH - chartH * (goal / maxV)
        canvas.drawLine(0f, gy, w, gy, goalPaint)
    }
}

class StatsActivity : Activity() {
    private lateinit var pal: Palette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Theme.p(this)
        themeWindow()
        val goal = Water.goal(this)

        // Ez a hét: összefoglaló és diagram
        val week = Water.lastDays(this, 7)
        val weekAvg = week.map { it.second }.average().toInt()
        val weekOk = week.count { it.second >= goal }
        val weekChart = BarChart(this, pal).apply { setData(week.map { it.first }, week.map { it.second }, goal) }
        val weekCard = card().apply {
            addView(label("Ez a hét", TypeScale.HEADLINE, bold = true))
            addView(label("átlag $weekAvg ml · $weekOk napon megvolt a cél", TypeScale.CAPTION, color = pal.sub).tabular())
            addView(weekChart, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(170), top = 12))
        }

        // Sorozat a cicával
        val streak = streak(goal)
        val cat = CatView(this).apply { mood = if (streak > 0) HAPPY else NEUTRAL }
        val streakCard = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(cat, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) })
            addView(LinearLayout(this@StatsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("$streak nap", 22f).apply { typeface = Fonts.display(this@StatsActivity); tabular() })
                addView(label(if (streak > 0) "sorozat: ennyi napja egymás után megvan a cél" else "Érd el ma a célt, és indul a sorozat.",
                    TypeScale.CAPTION, color = pal.sub))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        // Napok részletesen (lenyíló)
        val dayRows = week.reversed().map { (day, ml) ->
            listRow(day, if (ml >= goal) "$ml ml · cél megvolt" else "$ml ml")
        }
        val daysBody = group(*dayRows.toTypedArray())

        // Korábbi hetek: 30 napos diagram (lenyíló)
        val month = Water.lastDays(this, 30)
        val monthAvg = month.map { it.second }.average().toInt()
        val best = month.maxByOrNull { it.second }
        val monthBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(14))
            addView(BarChart(this@StatsActivity, pal).apply { setData(month.map { it.first }, month.map { it.second }, goal) },
                lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)))
            addView(label("30 nap átlaga: $monthAvg ml · legjobb: ${best?.first ?: "–"} (${best?.second ?: 0} ml) · " +
                "cél megvolt ${month.count { it.second >= goal }} napon", TypeScale.CAPTION, color = pal.sub).tabular(),
                lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 8))
        }

        val content = column().apply {
            addView(screenTitle("Statisztika"))
            addView(weekCard)
            addView(streakCard)
            addView(accordion("Napok részletesen", "az elmúlt 7 nap", daysBody))
            addView(accordion("Korábbi hetek", "30 nap", monthBody))
        }
        setContentView(Nav.screen(this, Nav.STATS, content))
    }

    // Egymást követő napok, amikor megvolt a cél (a mai nap csak akkor számít, ha már megvan)
    private fun streak(goal: Int): Int {
        val days = Water.lastDays(this, 90).map { it.second }
        var n = 0
        var i = days.size - 1
        if (i >= 0 && days[i] < goal) i--
        while (i >= 0 && days[i] >= goal) {
            n++
            i--
        }
        return n
    }
}
