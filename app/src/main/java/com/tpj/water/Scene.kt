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
        offer: Boolean = false, pill: Boolean = false, night: Boolean = false, blink: Boolean = false,
        catDx: Float = 0f
    ): RectF {
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

        // Állapot a vízszint szerint:
        // kevés víz: szomorúan ül a pohár alján; úszás: úszógumival lebeg és örül;
        // sok víz: búvárfelszereléssel lemerül és buborékokat fúj
        val dry = level < 0.12f
        val dive = level >= 0.75f
        val gear = when {
            night || offer || pill -> GEAR_NONE
            dry -> GEAR_NONE
            dive -> GEAR_DIVE
            else -> GEAR_RING
        }
        val sceneMood = when {
            night || offer || pill -> mood
            dry -> SAD
            else -> HAPPY
        }
        val cs = min(w * 0.52f, h * 0.44f)
        val bob = amp * sin(PI.toFloat() + phase)
        var cy = when {
            dry -> h - cs * 0.96f
            gear == GEAR_DIVE -> (sy + cs * 0.06f + bob).coerceAtMost(h - cs * 0.98f)
            else -> sy - cs * 0.72f + bob
        }
        cy = cy.coerceAtLeast(h * 0.02f)
        val cx = (w * catCenter - cs / 2f + catDx).coerceIn(0f, w - cs)
        canvas.save()
        canvas.translate(cx, cy + catLift)
        Mascot.draw(canvas, cs, look, sceneMood, offer, pill, 0f, joy, blink, gear)
        canvas.restore()

        // első hullám: kissé áttetsző, így a cica alsó fele a víz alatt látszik
        front.shader = if (night) {
            LinearGradient(0f, sy, 0f, h, 0xFF7C5CFA.toInt(), 0xFF2A2350.toInt(), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, sy, 0f, h, pal.waterTop, pal.waterBottom, Shader.TileMode.CLAMP)
        }
        // búvárkodáskor áttetszőbb a víz, hogy a karakter látsszon a felszín alatt
        front.alpha = if (gear == GEAR_DIVE) 150 else 228
        wave(canvas, w, h, sy, amp, phase, 1f, front)

        // búvár: buborékfüzér a légzőcsőtől a felszínig
        if (gear == GEAR_DIVE) {
            bubble.color = 0xFFFFFFFF.toInt()
            bubble.alpha = 170
            val bx = cx + cs * 0.62f
            val by = cy + catLift + cs * 0.78f
            val span = (by - sy).coerceAtLeast(1f)
            for (i in 0 until 4) {
                val p = ((t * 0.6f + i / 4f) % 1f)
                val y = by - span * p
                val x = bx + amp * 0.8f * sin(phase * 3f + i * 1.7f)
                canvas.drawCircle(x, y, cs * (0.025f + 0.012f * (i % 2)), bubble)
            }
        }

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
        return RectF(cx, cy + catLift, cx + cs, cy + catLift + cs)
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

// Élő vízjelenet a főképernyőn: hullámzik, a szint animálva emelkedik, ivásnál csobban.
// A karakterrel játszani lehet: koppintásra felugrik és megszólal, simogatásra örül és követi az ujjad.
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

    // Játék-visszajelzések: a főképernyő köti be a hangokat
    var onTapCharacter: (() -> Unit)? = null
    var onPetStart: (() -> Unit)? = null
    var onPetting: (() -> Unit)? = null
    var onPetEnd: (() -> Unit)? = null

    private var joy = false
    private var level = 0f
    private var lift = 0f
    private var catDx = 0f
    private var t = 0f
    private val start = SystemClock.uptimeMillis()
    private var lastFrame = 0L
    private var levelAnim: ValueAnimator? = null
    private var catRect = RectF()

    private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float, var life: Float)
    private class Heart(var x: Float, var y: Float, val vx: Float, val size: Float, var life: Float)

    private val drops = ArrayList<Drop>()
    private val hearts = ArrayList<Heart>()
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BUBBLE }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6F91.toInt() }
    private val heartPath = Path()

    // simogatás állapota
    private var touching = false
    private var petting = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var travel = 0f
    private var sinceHeart = 0f
    private var sinceTick = 0f
    private val slop = android.view.ViewConfiguration.get(c).scaledTouchSlop.toFloat()

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
        contentDescription = "Vízszint és a karakter. Koppints rá vagy simogasd."
        isClickable = true
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
        if (petting) onPetEnd?.invoke()
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
        val hi = hearts.iterator()
        while (hi.hasNext()) {
            val h = hi.next()
            h.y -= height * 0.25f * dt
            h.x += h.vx * dt
            h.life -= dt
            if (h.life <= 0f) hi.remove()
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

    private fun hop(height01: Float, ms: Long) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        ValueAnimator.ofFloat(0f, -height * height01, 0f).apply {
            duration = ms
            interpolator = EASE_OUT
            addUpdateListener {
                lift = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun showJoy(ms: Long) {
        joy = true
        invalidate()
        removeCallbacks(endJoy)
        postDelayed(endJoy, ms)
    }

    private val endJoy = Runnable {
        if (!petting) {
            joy = false
            invalidate()
        }
    }

    private fun addHearts(x: Float, y: Float, n: Int) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val w = width.toFloat()
        repeat(n) {
            hearts.add(Heart(
                x + (Random.nextFloat() - 0.5f) * w * 0.08f, y,
                (Random.nextFloat() - 0.5f) * w * 0.15f,
                w * (0.022f + Random.nextFloat() * 0.014f),
                1.1f + Random.nextFloat() * 0.4f
            ))
        }
    }

    // Ivás: a karakter felugrik és visszacsobban, vízcseppek fröccsennek
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
        hop(0.12f, 650L)
        showJoy(1600L)
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                // csak a karakterre koppintás számít, máshol a görgetés marad
                val hit = RectF(catRect).apply { inset(-width * 0.04f, -width * 0.04f) }
                if (!hit.contains(e.x, e.y)) return false
                touching = true
                petting = false
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                travel = 0f; sinceHeart = 0f; sinceTick = 0f
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                val d = kotlin.math.hypot(e.x - lastX, e.y - lastY)
                travel += d; sinceHeart += d; sinceTick += d
                lastX = e.x; lastY = e.y
                if (!petting && travel > slop) {
                    petting = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    joy = true
                    onPetStart?.invoke()
                }
                if (petting) {
                    // a karakter kicsit követi az ujjadat, a szélein egyre nehezebben
                    val raw = (e.x - downX) * 0.6f
                    val lim = width * 0.22f
                    catDx = lim * kotlin.math.tanh(raw / lim)
                    if (sinceHeart > width * 0.10f) {
                        addHearts(e.x, e.y - height * 0.04f, 1)
                        sinceHeart = 0f
                    }
                    if (sinceTick > width * 0.16f) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        onPetting?.invoke()
                        sinceTick = 0f
                    }
                    invalidate()
                }
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (!touching) return false
                touching = false
                if (!petting && e.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    // koppintás: felugrik, örül, megszólal
                    hop(0.08f, 420L)
                    showJoy(1200L)
                    addHearts(catRect.centerX(), catRect.top + catRect.height() * 0.2f, 3)
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onTapCharacter?.invoke()
                    performClick()
                } else if (petting) {
                    petting = false
                    onPetEnd?.invoke()
                    showJoy(700L)
                    // visszaúszik középre
                    val from = catDx
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        ValueAnimator.ofFloat(from, 0f).apply {
                            duration = 380L
                            interpolator = EASE_OUT
                            addUpdateListener { catDx = it.animatedValue as Float; invalidate() }
                            start()
                        }
                    } else catDx = 0f
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, s: Float) {
        heartPath.reset()
        heartPath.moveTo(x, y + s * 0.9f)
        heartPath.cubicTo(x - s * 1.4f, y - s * 0.1f, x - s * 0.6f, y - s * 1.2f, x, y - s * 0.4f)
        heartPath.cubicTo(x + s * 0.6f, y - s * 1.2f, x + s * 1.4f, y - s * 0.1f, x, y + s * 0.9f)
        heartPath.close()
        canvas.drawPath(heartPath, heartPaint)
    }

    override fun onDraw(canvas: Canvas) {
        catRect = WaterArt.draw(
            canvas, width.toFloat(), height.toFloat(), level, t,
            look, mood, joy, pal, lift, catCenter, offer, pill, night, blinker.closed && !joy, catDx
        )
        drops.forEach {
            dropPaint.alpha = (255 * it.life.coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(it.x, it.y, it.r, dropPaint)
        }
        hearts.forEach {
            heartPaint.alpha = (255 * it.life.coerceIn(0f, 1f)).toInt()
            drawHeart(canvas, it.x, it.y, it.size)
        }
    }
}
