package com.tpj.water

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// A vízjelenet rajzolása: ég, két hullám, a vízen lebegő cica, felszálló buborékok.
// A főképernyő és a kezdőképernyő-widget is ezt használja.
object WaterArt {
    private val sky = Paint(Paint.ANTI_ALIAS_FLAG)
    private val back = Paint(Paint.ANTI_ALIAS_FLAG)
    private val front = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private const val TWO_PI = (2 * PI).toFloat()

    // A vízfelszín magassága: üresen is marad egy kis víz az alján
    fun surfaceY(h: Float, level: Float): Float = h * (0.94f - 0.80f * level.coerceIn(0f, 1f))

    // t = eltelt idő másodpercben (a hullám 4 másodpercenként ér körbe)
    fun draw(
        canvas: Canvas, w: Float, h: Float, level: Float, t: Float,
        look: Look, mood: Int, joy: Boolean, pal: Palette,
        catLift: Float = 0f, catCenter: Float = 0.5f,
        offer: Boolean = false, pill: Boolean = false, night: Boolean = false, blink: Boolean = false
    ) {
        // night = a gyógyszer-ablak nyugodt, lilás éjszakai színvilága
        val skyTop = when {
            night -> 0xFF2E2A5C.toInt()
            else -> pal.skyTop
        }
        val skyBottom = when {
            night -> 0xFF15122E.toInt()
            else -> pal.skyBottom
        }
        sky.shader = LinearGradient(0f, 0f, 0f, h, skyTop, skyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, sky)

        val phase = t * TWO_PI / 4f
        val sy = surfaceY(h, level)
        val amp = h * 0.016f

        // hátsó, világosabb hullám
        back.color = if (night) 0xFFB9A8FF.toInt() else pal.waterBack
        back.alpha = if (night) 110 else 140
        wave(canvas, w, h, sy - amp * 0.6f, amp, phase * 1.3f + 1.7f, 1.4f, back)

        // a cica a felszínen lebeg; ha szinte üres a pohár, leül az aljára
        val cs = min(w * 0.52f, h * 0.44f)
        val bob = amp * sin(PI.toFloat() + phase)
        var cy = if (level < 0.1f) h - cs * 0.96f else sy - cs * 0.70f + bob
        cy = cy.coerceAtLeast(h * 0.02f)
        val cx = (w * catCenter - cs / 2f).coerceIn(0f, w - cs)
        canvas.save()
        canvas.translate(cx, cy + catLift)
        Mascot.draw(canvas, cs, look, mood, offer, pill, 0f, joy, blink)
        canvas.restore()

        // első hullám: kissé áttetsző, így a cica alsó fele a víz alatt látszik
        front.shader = if (night) {
            LinearGradient(0f, sy, 0f, h, 0xFF7C5CFA.toInt(), 0xFF2A2350.toInt(), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, sy, 0f, h, pal.waterTop, pal.waterBottom, Shader.TileMode.CLAMP)
        }
        front.alpha = 228
        wave(canvas, w, h, sy, amp, phase, 1f, front)

        // felszálló buborékok a vízben
        val wh = h - sy
        if (wh > h * 0.08f) {
            bubble.color = 0xFFFFFFFF.toInt()
            bubble.alpha = 80
            for (i in 0 until 7) {
                val x = w * (0.1f + 0.13f * i) + amp * sin(phase * 2f + i)
                val y = h - ((t * wh * 0.12f + i * wh / 7f) % wh)
                if (y > sy + amp) canvas.drawCircle(x, y, w * 0.006f * (1 + i % 3), bubble)
            }
        }
    }

    private fun wave(canvas: Canvas, w: Float, h: Float, base: Float, amp: Float, ph: Float, freq: Float, p: Paint) {
        path.reset()
        path.moveTo(0f, h)
        val step = w / 24f
        var x = 0f
        while (x <= w + step) {
            path.lineTo(x, base + amp * sin(TWO_PI * freq * x / w + ph))
            x += step
        }
        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, p)
    }

    // Állókép a widgethez, lekerekített sarkokkal
    fun bitmap(c: Context, wPx: Int, hPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = wPx * 0.12f
        canvas.clipPath(Path().apply {
            addRoundRect(RectF(0f, 0f, wPx.toFloat(), hPx.toFloat()), r, r, Path.Direction.CW)
        })
        draw(
            canvas, wPx.toFloat(), hPx.toFloat(), Water.progress(c), 1.3f,
            c.currentLook(), Water.currentMood(c), false, Theme.p(c)
        )
        return bmp
    }
}

// Élő vízjelenet a főképernyőn: hullámzik, a szint animálva emelkedik, ivásnál csobban
class WaterScene(c: Context) : View(c) {
    var look: Look = c.currentLook()
    var mood: Int = HAPPY
    var pal: Palette = Theme.p(c)
    private val blinker = Blinker(this)
    var offer: Boolean = false
    var pill: Boolean = false
    var night: Boolean = false
    var catCenter: Float = 0.66f
    // 0 = szögletes (teljes képernyős ébresztésnél)
    var cornerDp: Int = 28

    private var joy = false
    private var level = 0f
    private var lift = 0f
    private var t = 0f
    private val start = SystemClock.uptimeMillis()
    private var lastFrame = 0L
    private var levelAnim: ValueAnimator? = null

    private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float, var life: Float)

    private val drops = ArrayList<Drop>()
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BUBBLE }

    private val loop = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { tick() }
    }

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.context.dp(cornerDp).toFloat())
            }
        }
        clipToOutline = true
        contentDescription = "Vízszint"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // kikapcsolt animációknál (akadálymentesítés) a víz áll
        if (ValueAnimator.areAnimatorsEnabled()) loop.start()
        blinker.schedule()
    }

    override fun onDetachedFromWindow() {
        loop.cancel()
        levelAnim?.cancel()
        blinker.stop()
        super.onDetachedFromWindow()
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        t = (now - start) / 1000f
        val g = height * 2.2f
        val iter = drops.iterator()
        while (iter.hasNext()) {
            val d = iter.next()
            d.vy += g * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.life -= dt
            if (d.life <= 0f) iter.remove()
        }
        invalidate()
    }

    // A vízszint animált emelése vagy süllyesztése
    fun setLevel(target: Float, animate: Boolean) {
        val to = target.coerceIn(0f, 1f)
        levelAnim?.cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            level = to
            invalidate()
            return
        }
        levelAnim = ValueAnimator.ofFloat(level, to).apply {
            duration = 1200L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                level = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Ivás: a cica felugrik és visszacsobban, vízcseppek fröccsennek
    fun splash() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val sy = WaterArt.surfaceY(h, level)
        repeat(18) {
            drops.add(
                Drop(
                    x = w * 0.5f + (Random.nextFloat() - 0.5f) * w * 0.3f,
                    y = sy,
                    vx = (Random.nextFloat() - 0.5f) * w * 1.1f,
                    vy = -(0.5f + Random.nextFloat() * 0.6f) * h * 1.1f,
                    r = w * (0.008f + Random.nextFloat() * 0.012f),
                    life = 0.9f + Random.nextFloat() * 0.4f
                )
            )
        }
        joy = true
        ValueAnimator.ofFloat(0f, -h * 0.12f, 0f).apply {
            duration = 650L
            addUpdateListener {
                lift = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        postDelayed({
            joy = false
            invalidate()
        }, 1600L)
    }

    override fun onDraw(canvas: Canvas) {
        WaterArt.draw(
            canvas, width.toFloat(), height.toFloat(), level, t,
            look, mood, joy, pal, lift, catCenter, offer, pill, night, blinker.closed && !joy
        )
        drops.forEach {
            dropPaint.alpha = (255 * it.life.coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(it.x, it.y, it.r, dropPaint)
        }
    }
}
