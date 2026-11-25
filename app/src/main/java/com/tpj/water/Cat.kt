package com.tpj.water

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

const val HAPPY = 0
const val NEUTRAL = 1
const val SAD = 2
const val CRY = 3
const val ANGRY = 4

// tabby = cirmos cica (narancs, csíkos); egyébként az alap kék cica
data class Look(val color: Int, val eyes: Int, val mouth: Int, val whiskers: Boolean, val tabby: Boolean = false)

val BODY_COLORS = intArrayOf(
    0xFF1E88C9.toInt(), // kék (az alap cica színe)
    0xFF2BB3A3.toInt(), // türkiz
    0xFF7C5CFA.toInt(), // lila
    0xFFFF8FA3.toInt(), // rózsaszín
    0xFFF2A65A.toInt()  // narancs (a cirmos cica színe)
)
const val BLUE_INDEX = 0
const val ORANGE_INDEX = 4

fun Context.currentLook(): Look {
    val p = Water.prefs(this)
    return Look(
        color = p.getInt("catColor", BODY_COLORS[BLUE_INDEX]),
        eyes = p.getInt("eyes", 0),
        mouth = p.getInt("mouth", 0),
        whiskers = p.getBoolean("whiskers", true),
        tabby = p.getString("species", "cat") == "tabby"
    )
}

private val INK_CAT = 0xFF12324A.toInt()

private fun paint(col: Int, alpha: Int = 255): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = col
    this.alpha = alpha
    style = Paint.Style.FILL
}

private fun stroke(col: Int, width: Float, alpha: Int = 255): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = col
    this.alpha = alpha
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

// Két szín keverése: t = 0 → a, t = 1 → b
private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
)

// Kellékek: a mancs a test előtt fogja a poharat vagy a pirulát
internal object Props {
    private fun paw(canvas: Canvas, color: Int, left: Float) {
        canvas.drawOval(RectF(left, 80f, left + 16f, 92f), paint(color))
        val toe = stroke(mix(color, Color.BLACK, 0.3f), 1.4f)
        canvas.drawLine(left + 5f, 80.6f, left + 5f, 83.6f, toe)
        canvas.drawLine(left + 10f, 80.6f, left + 10f, 83.6f, toe)
    }

    fun glass(canvas: Canvas, color: Int) {
        val glass = Path().apply { moveTo(88f, 62f); lineTo(108f, 62f); lineTo(105f, 94f); lineTo(91f, 94f); close() }
        val water = Path().apply { moveTo(89f, 74f); lineTo(107f, 74f); lineTo(105f, 94f); lineTo(91f, 94f); close() }
        canvas.drawPath(glass, paint(Color.WHITE, 120))
        canvas.drawPath(water, paint(BUBBLE, 235))
        canvas.drawOval(RectF(89f, 72.5f, 107f, 75.5f), paint(0xFFB9E8FA.toInt()))
        canvas.drawLine(92f, 66f, 93.4f, 90f, stroke(Color.WHITE, 1.8f, 200))
        canvas.drawPath(glass, stroke(INK_CAT, 1.6f))
        canvas.drawOval(RectF(88f, 60f, 108f, 64f), stroke(INK_CAT, 1.4f))
        paw(canvas, color, 83f)
    }

    fun pill(canvas: Canvas, color: Int) {
        canvas.save()
        canvas.translate(98f, 76f)
        canvas.rotate(-30f)
        val r = RectF(-12f, -6f, 12f, 6f)
        canvas.drawRoundRect(r, 6f, 6f, paint(Color.WHITE))
        canvas.save()
        canvas.clipRect(-12f, -6f, 0f, 6f)
        canvas.drawRoundRect(r, 6f, 6f, paint(0xFFFF8FA3.toInt()))
        canvas.restore()
        canvas.drawLine(-8f, -3f, 6f, -3f, stroke(Color.WHITE, 1.6f, 180))
        canvas.drawRoundRect(r, 6f, 6f, stroke(INK_CAT, 1.6f))
        canvas.restore()
        paw(canvas, color, 85f)
    }
}

// A vízcsepp-cica rajzolása a 120x120-as koordinátarendszerben
object CatDraw {
    private val INK = INK_CAT

    private val body = Path().apply {
        moveTo(60f, 6f)
        cubicTo(60f, 6f, 22f, 50f, 22f, 78f)
        cubicTo(22f, 100f, 39f, 114f, 60f, 114f)
        cubicTo(81f, 114f, 98f, 100f, 98f, 78f)
        cubicTo(98f, 50f, 60f, 6f, 60f, 6f)
        close()
    }
    // lekerekített hegyű fülek
    private val earL = Path().apply { moveTo(24f, 62f); quadTo(22f, 30f, 27f, 20f); quadTo(31f, 18f, 54f, 38f); close() }
    private val earR = Path().apply { moveTo(96f, 62f); quadTo(98f, 30f, 93f, 20f); quadTo(89f, 18f, 66f, 38f); close() }
    private val innerL = Path().apply { moveTo(30f, 54f); quadTo(29f, 36f, 31f, 29f); quadTo(34f, 28f, 45f, 40f); close() }
    private val innerR = Path().apply { moveTo(90f, 54f); quadTo(91f, 36f, 89f, 29f); quadTo(86f, 28f, 75f, 40f); close() }
    // fénycsík a csepp csúcsán
    private val shine = Path().apply { moveTo(60f, 8f); cubicTo(56f, 13f, 48f, 22f, 41f, 33f) }

    // joy = örömteli arc; pill = gyógyszer a kézben; offer = pohár a kézben
    fun draw(canvas: Canvas, size: Float, look: Look, mood: Int, offer: Boolean, lift: Float, joy: Boolean = false, pill: Boolean = false) {
        val s = size / 120f
        canvas.save()
        canvas.translate(0f, lift * s)
        canvas.scale(s, s)

        val c = look.color
        canvas.drawPath(earL, paint(c))
        canvas.drawPath(earR, paint(c))
        val inner = paint(0xFFFFC9D3.toInt())
        canvas.drawPath(innerL, inner)
        canvas.drawPath(innerR, inner)
        canvas.drawPath(body, paint(c))

        if (look.tabby) {
            // cirmos csíkok a homlokon és az arc két oldalán
            val st = stroke(mix(c, Color.BLACK, 0.22f), 3f)
            canvas.drawLine(54f, 30f, 56f, 42f, st)
            canvas.drawLine(60f, 26f, 60f, 40f, st)
            canvas.drawLine(66f, 30f, 64f, 42f, st)
            canvas.drawLine(24f, 74f, 33f, 76f, st)
            canvas.drawLine(24f, 82f, 32f, 82f, st)
            canvas.drawLine(96f, 74f, 87f, 76f, st)
            canvas.drawLine(96f, 82f, 88f, 82f, st)
        } else {
            canvas.drawPath(shine, stroke(Color.WHITE, 3f, 150))
        }

        // világos pofafolt
        canvas.drawOval(RectF(43f, 81f, 77f, 107f), paint(mix(c, Color.WHITE, 0.88f)))

        // pofik (mérgesnél pirosak)
        val blush = paint(if (mood == ANGRY) 0xFFFF6B6B.toInt() else 0xFFFFB8C6.toInt(), if (joy) 255 else 215)
        canvas.drawCircle(37f, 88f, if (joy) 6f else 4.5f, blush)
        canvas.drawCircle(83f, 88f, if (joy) 6f else 4.5f, blush)

        // szemek
        val dot = paint(INK)
        val line = stroke(INK, 3.2f)
        val white = paint(Color.WHITE)
        if (joy || (mood == HAPPY && look.eyes == 1)) {
            canvas.drawPath(Path().apply { moveTo(41f, 76f); quadTo(47f, 69f, 53f, 76f) }, line)
            canvas.drawPath(Path().apply { moveTo(67f, 76f); quadTo(73f, 69f, 79f, 76f) }, line)
        } else if (look.eyes == 2) {
            canvas.drawCircle(47f, 75f, 6f, dot)
            canvas.drawCircle(73f, 75f, 6f, dot)
            canvas.drawCircle(45f, 72.5f, 2f, white)
            canvas.drawCircle(71f, 72.5f, 2f, white)
        } else {
            canvas.drawOval(RectF(42.8f, 69.8f, 51.2f, 80.2f), dot)
            canvas.drawOval(RectF(68.8f, 69.8f, 77.2f, 80.2f), dot)
            canvas.drawCircle(45.6f, 73f, 1.6f, white)
            canvas.drawCircle(71.6f, 73f, 1.6f, white)
        }

        // mérges szemöldök
        if (mood == ANGRY) {
            canvas.drawLine(39f, 64f, 52f, 69f, line)
            canvas.drawLine(81f, 64f, 68f, 69f, line)
        }

        // pink háromszög-orr
        canvas.drawPath(Path().apply { moveTo(56.5f, 86f); lineTo(63.5f, 86f); lineTo(60f, 90f); close() }, paint(0xFFFF8FA3.toInt()))

        // száj a hangulat szerint
        val ml = stroke(INK, 2.6f)
        if (joy) {
            canvas.drawPath(Path().apply { moveTo(52f, 91f); quadTo(60f, 104f, 68f, 91f); close() }, dot)
            canvas.drawCircle(60f, 98f, 3f, paint(0xFFFF8FA3.toInt()))
        } else if (mood == HAPPY && look.mouth == 2) {
            canvas.drawOval(RectF(56f, 91f, 64f, 98f), dot)
        } else {
            val m = Path()
            when (mood) {
                // 0 = :3 (alapértelmezett), 1 = mosoly
                HAPPY -> if (look.mouth == 1) {
                    m.moveTo(54f, 92f); m.quadTo(60f, 97f, 66f, 92f)
                } else {
                    m.moveTo(54f, 92f); m.quadTo(57f, 95f, 60f, 91f); m.quadTo(63f, 95f, 66f, 92f)
                }
                NEUTRAL -> { m.moveTo(56f, 94f); m.lineTo(64f, 94f) }
                else -> { m.moveTo(54f, 97f); m.quadTo(60f, 91f, 66f, 97f) }
            }
            canvas.drawPath(m, ml)
        }

        // könnyek sírásnál
        if (mood == CRY) {
            val tear = paint(BUBBLE)
            canvas.drawCircle(42f, 84f, 2.6f, tear)
            canvas.drawCircle(78f, 84f, 2.6f, tear)
            canvas.drawCircle(43f, 92f, 2f, tear)
            canvas.drawCircle(77f, 92f, 2f, tear)
        }

        // bajusz a pofafoltból
        if (look.whiskers) {
            val w = stroke(INK, 1.4f)
            canvas.drawLine(44f, 92f, 28f, 89f, w)
            canvas.drawLine(44f, 96f, 28f, 98f, w)
            canvas.drawLine(76f, 92f, 92f, 89f, w)
            canvas.drawLine(76f, 96f, 92f, 98f, w)
        }

        if (offer) Props.glass(canvas, c)
        else if (pill) Props.pill(canvas, c)

        canvas.restore()
    }
}

// A karakter (cica) rajzolása
object Mascot {
    fun draw(canvas: Canvas, size: Float, look: Look, mood: Int, offer: Boolean, pill: Boolean, lift: Float, joy: Boolean) {
        CatDraw.draw(canvas, size, look, mood, offer, lift, joy, pill)
    }

    fun bitmap(c: Context, px: Int, mood: Int, offer: Boolean, pill: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), px.toFloat(), c.currentLook(), mood, offer, pill, 0f, false)
        return bmp
    }
}

// Élő, animált karakter nézet
class CatView(c: Context) : View(c) {
    var look: Look = c.currentLook()
    var mood: Int = HAPPY
    var offer: Boolean = false
    var pill: Boolean = false
    var joy: Boolean = false
    private var bounceY = 0f
    private var idle = 0f

    private val idleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            idle = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        idleAnim.start()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        Mascot.draw(canvas, size, look, mood, offer, pill, bounceY - 3f * idle, joy)
    }

    // Boldog ugrálás (pl. Ittam után)
    fun bounce() {
        ValueAnimator.ofFloat(0f, -14f, 0f).apply {
            duration = 520L
            addUpdateListener {
                bounceY = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Ittam utáni öröm: mosoly a szemekkel és a szájjal, ugrás
    fun celebrate() {
        mood = HAPPY
        joy = true
        invalidate()
        bounce()
        postDelayed({
            joy = false
            invalidate()
        }, 1600L)
    }

    fun applyLook(l: Look) {
        look = l
        invalidate()
    }

    override fun onDetachedFromWindow() {
        idleAnim.cancel()
        super.onDetachedFromWindow()
    }
}
