package com.tpj.water

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

// Oszlopdiagram: egy oszlop naponként, a szaggatott vonal a napi cél
class BarChart(c: Context) : View(c) {
    private var labels: List<String> = emptyList()
    private var values: List<Int> = emptyList()
    private var goal = 2500
    private val density = c.resources.displayMetrics.density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TIDE }
    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LAGOON }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.sub(c)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.sub(c)
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
    }

    fun setData(l: List<String>, v: List<Int>, g: Int) {
        labels = l
        values = v
        goal = g
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = values.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val labelH = 22f * density
        val chartH = h - labelH
        val maxV = maxOf(goal, values.maxOrNull() ?: 0).toFloat().coerceAtLeast(1f)
        val slot = w / n
        val barW = slot * 0.6f
        val radius = 8f * density

        values.forEachIndexed { i, v ->
            val bh = chartH * (v / maxV)
            val left = i * slot + (slot - barW) / 2f
            val r = RectF(left, chartH - bh, left + barW, chartH)
            canvas.drawRoundRect(r, radius, radius, if (v >= goal) okPaint else barPaint)
            if (n <= 10 || i % 5 == 0) {
                canvas.drawText(labels.getOrElse(i) { "" }, left + barW / 2f, h - 4f * density, textPaint)
            }
        }
        val gy = chartH - chartH * (goal / maxV)
        canvas.drawLine(0f, gy, w, gy, goalPaint)
    }
}

class StatsActivity : Activity() {
    private var days = 7
    private lateinit var chart: BarChart
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chart = BarChart(this).apply {
            layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(220), top = 8)
        }
        summary = label("", 15f)

        val range = chipRow(listOf("Hét", "Hónap"), 0) {
            days = if (it == 0) 7 else 30
            render()
        }
        val chartCard = card().apply {
            addView(label("Ivás naponként", 17f, bold = true))
            addView(chart)
        }
        val sumCard = card().apply { addView(summary) }

        val content = column().apply {
            addView(label("Statisztika", 26f, bold = true))
            addView(range)
            addView(chartCard)
            addView(sumCard)
        }
        setContentView(screen(content))
        render()
    }

    private fun render() {
        val data = Water.lastDays(this, days)
        val goal = Water.goal(this)
        chart.setData(data.map { it.first }, data.map { it.second }, goal)
        val avg = data.map { it.second }.average().toInt()
        val best = data.maxByOrNull { it.second }
        val reached = data.count { it.second >= goal }
        summary.text = "Átlag: $avg ml / nap\n" +
            "Legjobb nap: ${best?.first ?: "–"} (${best?.second ?: 0} ml)\n" +
            "Cél elérve: $reached / $days nap"
    }
}
