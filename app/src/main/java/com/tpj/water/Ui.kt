package com.tpj.water

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

val TIDE = 0xFF1E88C9.toInt()
val LAGOON = 0xFF0B3B5C.toInt()
val FOAM = 0xFFE8F6FD.toInt()
val BUBBLE = 0xFF7DD3F5.toInt()

// Világos és sötét téma színei
object Theme {
    fun dark(c: Context) = Water.prefs(c).getBoolean("dark", false)
    fun bg(c: Context) = if (dark(c)) 0xFF0E1E2C.toInt() else FOAM
    fun card(c: Context) = if (dark(c)) 0xFF183247.toInt() else Color.WHITE
    fun text(c: Context) = if (dark(c)) FOAM else LAGOON
    fun sub(c: Context) = if (dark(c)) BUBBLE else TIDE
    fun field(c: Context) = if (dark(c)) 0xFF0E2638.toInt() else FOAM
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

// Tipográfiai skála: ezt az öt méretet használja a főképernyő
object TypeScale {
    const val DISPLAY = 52f
    const val TITLE = 22f
    const val HEADLINE = 17f
    const val BODY = 15f
    const val CAPTION = 12f
}

// Saját vektoros ikon a megadott színnel és méretben
fun Context.tinted(res: Int, color: Int, sizeDp: Int): Drawable {
    val d = getDrawable(res)!!.mutate()
    d.setTint(color)
    d.setBounds(0, 0, dp(sizeDp), dp(sizeDp))
    return d
}

fun Context.rect(color: Int, radiusDp: Int): GradientDrawable {
    val ctx = this
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = ctx.dp(radiusDp).toFloat()
        setColor(color)
    }
}

fun rippleOf(bg: Drawable): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), bg, null)

fun Context.lp(w: Int, h: Int, top: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams {
    val ctx = this
    return LinearLayout.LayoutParams(w, h, weight).apply { topMargin = ctx.dp(top) }
}

fun Context.card(): LinearLayout {
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = ctx.rect(Theme.card(ctx), 24)
        setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(16))
        layoutParams = ctx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 14)
    }
}

fun Context.column(): LinearLayout {
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(20), ctx.dp(24), ctx.dp(20), ctx.dp(32))
    }
}

fun Context.screen(content: View): ScrollView {
    val sv = ScrollView(this)
    sv.setBackgroundColor(Theme.bg(this))
    sv.addView(content)
    return sv
}

fun Context.label(
    text: String,
    size: Float,
    bold: Boolean = false,
    center: Boolean = false,
    color: Int? = null
): TextView {
    val tv = TextView(this)
    tv.setText(text)
    tv.textSize = size
    tv.setTextColor(color ?: Theme.text(this))
    tv.typeface = Typeface.create("sans-serif-rounded", if (bold) Typeface.BOLD else Typeface.NORMAL)
    if (center) tv.gravity = Gravity.CENTER
    return tv
}

fun Context.button(label: String, filled: Boolean, onClick: () -> Unit): Button {
    val ctx = this
    val b = Button(ctx)
    b.setText(label)
    b.isAllCaps = false
    b.textSize = 16f
    b.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
    b.setTextColor(if (filled) Color.WHITE else TIDE)
    val shape = ctx.rect(if (filled) TIDE else Theme.card(ctx), 16)
    if (!filled) shape.setStroke(ctx.dp(2), TIDE)
    b.background = rippleOf(shape)
    b.layoutParams = ctx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(52), top = 10)
    b.setOnClickListener { onClick() }
    return b
}

fun Context.field(value: String): EditText {
    val ctx = this
    val e = EditText(ctx)
    e.setText(value)
    e.textSize = 17f
    e.setTextColor(Theme.text(ctx))
    e.background = ctx.rect(Theme.field(ctx), 12)
    e.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
    e.layoutParams = ctx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 6)
    return e
}

// Választó sor: szöveges chipek, vagy színes mintapöttyök (swatches)
fun Context.chipRow(
    labels: List<String>,
    selected: Int,
    swatches: IntArray? = null,
    onPick: (Int) -> Unit
): LinearLayout {
    val ctx = this
    var sel = selected
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.HORIZONTAL
    val btns = ArrayList<Button>()

    fun paint() {
        btns.forEachIndexed { i, b ->
            val on = i == sel
            val fill = when {
                swatches != null -> swatches[i]
                on -> TIDE
                else -> Theme.field(ctx)
            }
            b.setTextColor(if (on) Color.WHITE else Theme.text(ctx))
            b.text = if (swatches != null) (if (on) "✓" else "") else labels[i]
            b.background = rippleOf(ctx.rect(fill, 12))
        }
    }

    labels.indices.forEach { i ->
        val b = Button(ctx)
        b.isAllCaps = false
        b.textSize = 14f
        b.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        b.layoutParams = LinearLayout.LayoutParams(0, ctx.dp(46), 1f).apply {
            setMargins(ctx.dp(3), ctx.dp(8), ctx.dp(3), 0)
        }
        b.setOnClickListener {
            sel = i
            paint()
            onPick(i)
        }
        btns.add(b)
        row.addView(b)
    }
    paint()
    return row
}

fun View.reveal(delay: Long) {
    alpha = 0f
    translationY = 24f
    animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(420).start()
}

fun parseHm(s: String): Int? {
    val p = s.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

fun fmtHm(min: Int): String = String.format("%02d:%02d", min / 60, min % 60)
