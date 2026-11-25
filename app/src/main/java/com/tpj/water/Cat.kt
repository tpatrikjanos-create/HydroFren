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
import kotlin.random.Random

const val HAPPY = 0
const val NEUTRAL = 1
const val SAD = 2
const val CRY = 3
const val ANGRY = 4

// ---------- Fajok ----------
// Mind ugyanarra a vízcsepp-testre és arcrendszerre épül; csak a jellegzetes részek térnek el.
data class Species(val id: String, val name: String, val color: Int, val whiskers: Boolean, val sound: Int)

object SpeciesList {
    val ALL = listOf(
        Species("cat", "Kék cica", 0xFF1E88C9.toInt(), true, R.raw.snd_cat),
        Species("tabby", "Cirmos cica", 0xFFF2A65A.toInt(), true, R.raw.snd_tabby),
        Species("bunny", "Nyúl", 0xFFCFC6F2.toInt(), true, R.raw.snd_bunny),
        Species("fox", "Róka", 0xFFF28C38.toInt(), true, R.raw.snd_fox),
        Species("frog", "Béka", 0xFF6CC66B.toInt(), false, R.raw.snd_frog),
        Species("panda", "Panda", 0xFFF7F7F7.toInt(), false, R.raw.snd_panda),
        Species("axolotl", "Axolotl", 0xFFFFB3C8.toInt(), false, R.raw.snd_axolotl),
        Species("dog", "Kutya", 0xFFE8C79A.toInt(), false, R.raw.snd_dog),
        Species("duck", "Kacsa", 0xFFFFD84D.toInt(), false, R.raw.snd_duck),
        Species("seal", "Fóka", 0xFFA9B6C2.toInt(), true, R.raw.snd_seal)
    )

    fun byId(id: String?): Species = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

data class Look(
    val color: Int,
    val eyes: Int,
    val mouth: Int,
    val whiskers: Boolean,
    val species: String = "cat"
) {
    val tabby: Boolean get() = species == "tabby"
}

// Választható testszínek (a faj saját színe mindig az első)
val BODY_COLORS = intArrayOf(
    0xFF1E88C9.toInt(), // kék
    0xFF2BB3A3.toInt(), // türkiz
    0xFF7C5CFA.toInt(), // lila
    0xFFFF8FA3.toInt(), // rózsaszín
    0xFFF2A65A.toInt()  // narancs
)

fun colorChoices(speciesId: String): IntArray {
    val own = SpeciesList.byId(speciesId).color
    return (listOf(own) + BODY_COLORS.toList()).distinct().toIntArray()
}

fun Context.currentLook(): Look {
    val p = Water.prefs(this)
    val sp = SpeciesList.byId(p.getString("species", "cat"))
    return Look(
        color = p.getInt("catColor", sp.color),
        eyes = p.getInt("eyes", 0),
        mouth = p.getInt("mouth", 0),
        whiskers = p.getBoolean("whiskers", true),
        species = sp.id
    )
}

fun Context.currentSpecies(): Species = SpeciesList.byId(Water.prefs(this).getString("species", "cat"))

private val INK = 0xFF12324A.toInt()

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

private fun Canvas.oval(cx: Float, cy: Float, rx: Float, ry: Float, p: Paint, rot: Float = 0f) {
    save()
    if (rot != 0f) rotate(rot, cx, cy)
    drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
    restore()
}

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
        canvas.drawPath(glass, stroke(INK, 1.6f))
        canvas.drawOval(RectF(88f, 60f, 108f, 64f), stroke(INK, 1.4f))
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
        canvas.drawRoundRect(r, 6f, 6f, stroke(INK, 1.6f))
        canvas.restore()
        paw(canvas, color, 85f)
    }
}

// ---------- A közös rajzoló a 120x120-as koordinátarendszerben ----------
object CritterDraw {
    private val body = Path().apply {
        moveTo(60f, 6f)
        cubicTo(60f, 6f, 22f, 50f, 22f, 78f)
        cubicTo(22f, 100f, 39f, 114f, 60f, 114f)
        cubicTo(81f, 114f, 98f, 100f, 98f, 78f)
        cubicTo(98f, 50f, 60f, 6f, 60f, 6f)
        close()
    }
    private val shine = Path().apply { moveTo(60f, 8f); cubicTo(56f, 13f, 48f, 22f, 41f, 33f) }

    // cica fülek
    private val catEarL = Path().apply { moveTo(24f, 62f); quadTo(22f, 30f, 27f, 20f); quadTo(31f, 18f, 54f, 38f); close() }
    private val catEarR = Path().apply { moveTo(96f, 62f); quadTo(98f, 30f, 93f, 20f); quadTo(89f, 18f, 66f, 38f); close() }
    private val catInL = Path().apply { moveTo(30f, 54f); quadTo(29f, 36f, 31f, 29f); quadTo(34f, 28f, 45f, 40f); close() }
    private val catInR = Path().apply { moveTo(90f, 54f); quadTo(91f, 36f, 89f, 29f); quadTo(86f, 28f, 75f, 40f); close() }

    // kutya lelógó fülek
    private val dogEarL = Path().apply { moveTo(36f, 40f); cubicTo(18f, 42f, 8f, 66f, 16f, 86f); cubicTo(20f, 95f, 32f, 93f, 34f, 82f); cubicTo(36f, 68f, 40f, 54f, 46f, 44f); close() }
    private val dogEarR = Path().apply { moveTo(84f, 40f); cubicTo(102f, 42f, 112f, 66f, 104f, 86f); cubicTo(100f, 95f, 88f, 93f, 86f, 82f); cubicTo(84f, 68f, 80f, 54f, 74f, 44f); close() }

    // blink = csukott szem (pislogás); joy = örömteli arc; offer = pohár; pill = gyógyszer
    fun draw(
        canvas: Canvas, size: Float, look: Look, mood: Int, offer: Boolean, lift: Float,
        joy: Boolean = false, pill: Boolean = false, blink: Boolean = false
    ) {
        val s = size / 120f
        canvas.save()
        canvas.translate(0f, lift * s)
        canvas.scale(s, s)

        val sp = look.species
        val c = if (sp == "panda") 0xFFF7F7F7.toInt() else look.color

        behind(canvas, sp, c)
        canvas.drawPath(body, paint(c))
        if (sp == "panda") canvas.drawPath(body, stroke(0xFF2B2B2B.toInt(), 2f))

        // mintázat a testen (a test alakjára vágva)
        canvas.save()
        canvas.clipPath(body)
        pattern(canvas, sp, c)
        canvas.restore()
        if (sp != "tabby" && sp != "panda") canvas.drawPath(shine, stroke(Color.WHITE, 3f, 140))

        front(canvas, sp, c)
        muzzle(canvas, sp, c)

        // pofik (mérgesnél pirosak)
        val bx = if (sp == "axolotl") 38f else 37f
        val by = if (sp == "duck") 86f else 88f
        val blush = paint(if (mood == ANGRY) 0xFFFF6B6B.toInt() else 0xFFFFB8C6.toInt(), if (joy) 255 else 210)
        canvas.drawCircle(bx, by, if (joy) 6f else 4.5f, blush)
        canvas.drawCircle(120f - bx, by, if (joy) 6f else 4.5f, blush)

        eyes(canvas, sp, look, mood, joy, blink)
        brows(canvas, sp, mood)
        nose(canvas, sp, c)
        mouth(canvas, sp, look, mood, joy)

        // könnyek sírásnál
        if (mood == CRY) {
            val tear = paint(BUBBLE)
            canvas.drawCircle(42f, 85f, 2.6f, tear)
            canvas.drawCircle(78f, 85f, 2.6f, tear)
            canvas.drawCircle(43f, 92f, 2f, tear)
            canvas.drawCircle(77f, 92f, 2f, tear)
        }

        if (look.whiskers && SpeciesList.byId(sp).whiskers) whiskers(canvas, sp)

        val pawColor = when (sp) {
            "panda" -> 0xFF2B2B2B.toInt()
            else -> c
        }
        if (offer) Props.glass(canvas, pawColor)
        else if (pill) Props.pill(canvas, pawColor)

        canvas.restore()
    }

    // A test mögötti részek: fülek, szárnyak, uszonyok, kopoltyúk
    private fun behind(canvas: Canvas, sp: String, c: Int) {
        when (sp) {
            "cat", "tabby" -> {
                canvas.drawPath(catEarL, paint(c)); canvas.drawPath(catEarR, paint(c))
                val inner = paint(0xFFFFC9D3.toInt())
                canvas.drawPath(catInL, inner); canvas.drawPath(catInR, inner)
            }
            "bunny" -> {
                canvas.oval(44f, 34f, 9f, 28f, paint(c), -14f)
                canvas.oval(76f, 34f, 9f, 28f, paint(c), 14f)
                canvas.oval(44f, 36f, 4.5f, 19f, paint(0xFFFFB8C6.toInt()), -14f)
                canvas.oval(76f, 36f, 4.5f, 19f, paint(0xFFFFB8C6.toInt()), 14f)
            }
            "fox" -> {
                val l = Path().apply { moveTo(22f, 64f); lineTo(22f, 18f); lineTo(54f, 40f); close() }
                val r = Path().apply { moveTo(98f, 64f); lineTo(98f, 18f); lineTo(66f, 40f); close() }
                canvas.drawPath(l, paint(c)); canvas.drawPath(r, paint(c))
                val tip = paint(0xFF3A2416.toInt())
                canvas.drawPath(Path().apply { moveTo(22f, 18f); lineTo(22f, 32f); lineTo(32f, 26f); close() }, tip)
                canvas.drawPath(Path().apply { moveTo(98f, 18f); lineTo(98f, 32f); lineTo(88f, 26f); close() }, tip)
                val inner = paint(0xFFFFD9B8.toInt())
                canvas.drawPath(Path().apply { moveTo(28f, 54f); lineTo(28f, 30f); lineTo(44f, 41f); close() }, inner)
                canvas.drawPath(Path().apply { moveTo(92f, 54f); lineTo(92f, 30f); lineTo(76f, 41f); close() }, inner)
            }
            "panda" -> {
                val ear = paint(0xFF2B2B2B.toInt())
                canvas.drawCircle(32f, 48f, 11f, ear)
                canvas.drawCircle(88f, 48f, 11f, ear)
            }
            "axolotl" -> {
                val g = stroke(mix(c, 0xFFE0306A.toInt(), 0.45f), 5f)
                canvas.drawLine(30f, 66f, 12f, 52f, g); canvas.drawLine(28f, 74f, 8f, 70f, g); canvas.drawLine(30f, 82f, 12f, 88f, g)
                canvas.drawLine(90f, 66f, 108f, 52f, g); canvas.drawLine(92f, 74f, 112f, 70f, g); canvas.drawLine(90f, 82f, 108f, 88f, g)
            }
            "duck" -> {
                val wing = paint(mix(c, Color.BLACK, 0.05f))
                canvas.oval(24f, 88f, 7f, 13f, wing, 25f)
                canvas.oval(96f, 88f, 7f, 13f, wing, -25f)
                val tuft = stroke(mix(c, Color.BLACK, 0.12f), 3f)
                canvas.drawPath(Path().apply { moveTo(60f, 9f); cubicTo(56f, 2f, 51f, 1f, 49f, 4f) }, tuft)
                canvas.drawPath(Path().apply { moveTo(60f, 9f); cubicTo(62f, 2f, 67f, 1f, 69f, 4f) }, tuft)
            }
            "seal" -> {
                val fl = paint(mix(c, Color.BLACK, 0.2f))
                canvas.oval(22f, 96f, 7f, 13f, fl, 35f)
                canvas.oval(98f, 96f, 7f, 13f, fl, -35f)
            }
        }
    }

    // Minták a testen
    private fun pattern(canvas: Canvas, sp: String, c: Int) {
        when (sp) {
            "tabby" -> {
                val st = stroke(mix(c, Color.BLACK, 0.22f), 3f)
                canvas.drawLine(54f, 30f, 56f, 42f, st); canvas.drawLine(60f, 26f, 60f, 40f, st); canvas.drawLine(66f, 30f, 64f, 42f, st)
                canvas.drawLine(24f, 74f, 33f, 76f, st); canvas.drawLine(24f, 82f, 32f, 82f, st)
                canvas.drawLine(96f, 74f, 87f, 76f, st); canvas.drawLine(96f, 82f, 88f, 82f, st)
            }
            "dog" -> canvas.oval(74f, 74f, 11f, 10f, paint(mix(c, Color.BLACK, 0.3f)))
            "seal" -> {
                val sp2 = paint(mix(c, Color.BLACK, 0.2f))
                canvas.drawCircle(40f, 58f, 3f, sp2); canvas.drawCircle(80f, 50f, 2.4f, sp2); canvas.drawCircle(70f, 34f, 2f, sp2)
            }
            "frog" -> {
                val sp2 = paint(mix(c, Color.BLACK, 0.18f))
                canvas.drawCircle(72f, 104f, 2.2f, sp2); canvas.drawCircle(48f, 102f, 1.8f, sp2)
            }
            "axolotl" -> {
                val sp2 = paint(mix(c, 0xFFE0306A.toInt(), 0.3f))
                canvas.drawCircle(66f, 104f, 2f, sp2); canvas.drawCircle(54f, 100f, 1.6f, sp2)
            }
            "fox" -> {
                val mask = Path().apply {
                    moveTo(24f, 84f); quadTo(60f, 128f, 96f, 84f); quadTo(78f, 96f, 60f, 90f); quadTo(42f, 96f, 24f, 84f); close()
                }
                canvas.drawPath(mask, paint(0xFFFFF6EC.toInt()))
            }
        }
    }

    // A test elé kerülő részek
    private fun front(canvas: Canvas, sp: String, c: Int) {
        when (sp) {
            "dog" -> {
                val ear = paint(mix(c, Color.BLACK, 0.45f))
                canvas.drawPath(dogEarL, ear); canvas.drawPath(dogEarR, ear)
            }
            "frog" -> {
                canvas.drawCircle(38f, 58f, 13f, paint(c)); canvas.drawCircle(82f, 58f, 13f, paint(c))
                canvas.drawCircle(38f, 58f, 9f, paint(Color.WHITE)); canvas.drawCircle(82f, 58f, 9f, paint(Color.WHITE))
            }
            "panda" -> {
                val patch = paint(0xFF2B2B2B.toInt())
                canvas.oval(46f, 76f, 7f, 10f, patch, 25f)
                canvas.oval(74f, 76f, 7f, 10f, patch, -25f)
            }
        }
    }

    private fun muzzle(canvas: Canvas, sp: String, c: Int) {
        when (sp) {
            "cat", "tabby" -> canvas.drawOval(RectF(43f, 81f, 77f, 107f), paint(mix(c, Color.WHITE, 0.88f)))
            "dog" -> canvas.drawOval(RectF(42f, 82f, 78f, 108f), paint(0xFFFFF3E0.toInt()))
            "seal" -> canvas.drawOval(RectF(45f, 82f, 75f, 104f), paint(0xFFE3E9EE.toInt()))
        }
    }

    private fun eyes(canvas: Canvas, sp: String, look: Look, mood: Int, joy: Boolean, blink: Boolean) {
        val line = stroke(INK, 3.2f)
        val dot = paint(INK)
        val white = paint(Color.WHITE)
        val happyArcs = joy || (mood == HAPPY && look.eyes == 1)

        when (sp) {
            "frog" -> {
                for (x in listOf(38f, 82f)) {
                    when {
                        blink -> canvas.drawLine(x - 6f, 59f, x + 6f, 59f, line)
                        happyArcs -> canvas.drawPath(Path().apply { moveTo(x - 6f, 61f); quadTo(x, 53f, x + 6f, 61f) }, line)
                        else -> {
                            canvas.drawCircle(x + 1f, 59f, if (look.eyes == 2) 5.5f else 4.5f, dot)
                            canvas.drawCircle(x - 0.5f, 57f, 1.5f, white)
                        }
                    }
                }
                return
            }
            "panda" -> {
                val wl = stroke(Color.WHITE, 2.6f)
                for (x in listOf(47f, 73f)) {
                    when {
                        blink -> canvas.drawLine(x - 4f, 76f, x + 4f, 76f, wl)
                        happyArcs -> canvas.drawPath(Path().apply { moveTo(x - 4f, 77f); quadTo(x, 71f, x + 4f, 77f) }, wl)
                        else -> canvas.drawCircle(x, 75f, if (look.eyes == 2) 3.4f else 2.6f, white)
                    }
                }
                return
            }
        }

        val ex = if (sp == "axolotl") 43f else 47f
        val ey = when (sp) { "axolotl" -> 76f; "fox", "seal" -> 74f; else -> 75f }
        val round = sp == "dog" || sp == "seal" || sp == "axolotl"
        for (x in listOf(ex, 120f - ex)) {
            when {
                blink -> canvas.drawPath(Path().apply { moveTo(x - 5f, ey); quadTo(x, ey + 2.5f, x + 5f, ey) }, line)
                happyArcs -> canvas.drawPath(Path().apply { moveTo(x - 6f, ey + 1f); quadTo(x, ey - 6f, x + 6f, ey + 1f) }, line)
                look.eyes == 2 -> {
                    canvas.drawCircle(x, ey, 6f, dot)
                    canvas.drawCircle(x - 2f, ey - 2.5f, 2f, white)
                }
                round -> {
                    val r = if (sp == "seal") 5.6f else 5f
                    canvas.drawCircle(x, ey, r, dot)
                    canvas.drawCircle(x - 1.6f, ey - 2f, 1.7f, white)
                }
                else -> {
                    canvas.drawOval(RectF(x - 4.2f, ey - 5.2f, x + 4.2f, ey + 5.2f), dot)
                    canvas.drawCircle(x - 1.4f, ey - 2f, 1.6f, white)
                }
            }
        }
    }

    // Szemöldök: mérgesnél befelé lejt, szomorúnál és sírásnál befelé emelkedik
    private fun brows(canvas: Canvas, sp: String, mood: Int) {
        if (mood != ANGRY && mood != SAD && mood != CRY) return
        val dy = if (sp == "frog") -20f else 0f
        val p = stroke(if (sp == "panda") 0xFF2B2B2B.toInt() else INK, 3f)
        if (mood == ANGRY) {
            canvas.drawLine(39f, 64f + dy, 52f, 69f + dy, p)
            canvas.drawLine(81f, 64f + dy, 68f, 69f + dy, p)
        } else {
            canvas.drawLine(40f, 67f + dy, 52f, 63f + dy, p)
            canvas.drawLine(80f, 67f + dy, 68f, 63f + dy, p)
        }
    }

    private fun nose(canvas: Canvas, sp: String, c: Int) {
        val dark = paint(0xFF1B1B1B.toInt())
        when (sp) {
            "cat", "tabby" -> canvas.drawPath(Path().apply { moveTo(56.5f, 86f); lineTo(63.5f, 86f); lineTo(60f, 90f); close() }, paint(0xFFFF8FA3.toInt()))
            "bunny" -> canvas.drawPath(Path().apply { moveTo(57f, 83f); lineTo(63f, 83f); lineTo(60f, 86f); close() }, paint(0xFFFF8FA3.toInt()))
            "fox" -> canvas.oval(60f, 86f, 4.5f, 3.2f, dark)
            "seal" -> canvas.oval(60f, 87f, 5f, 3.4f, dark)
            "panda" -> canvas.oval(60f, 88f, 4f, 2.8f, dark)
            "dog" -> {
                canvas.drawPath(Path().apply { moveTo(53.5f, 86f); quadTo(60f, 83f, 66.5f, 86f); quadTo(66f, 92f, 60f, 93f); quadTo(54f, 92f, 53.5f, 86f); close() }, dark)
                canvas.oval(58f, 86.6f, 2f, 1f, paint(Color.WHITE, 150))
            }
            "frog" -> {
                val n = paint(mix(c, Color.BLACK, 0.45f))
                canvas.drawCircle(56f, 80f, 1.4f, n); canvas.drawCircle(64f, 80f, 1.4f, n)
            }
        }
    }

    private fun mouth(canvas: Canvas, sp: String, look: Look, mood: Int, joy: Boolean) {
        if (sp == "duck") {
            // a kacsa csőre helyettesíti az orrot és a szájat
            val beak = paint(0xFFF58A1F.toInt())
            if (joy) {
                canvas.oval(60f, 88f, 14f, 5.5f, beak)
                canvas.oval(60f, 95f, 11f, 4f, paint(0xFFE0761A.toInt()))
                canvas.oval(60f, 91.5f, 8f, 2f, paint(0xFF7A2E10.toInt()))
            } else {
                canvas.oval(60f, 90f, 14f, 6.5f, beak)
                val lineY = if (mood == SAD || mood == CRY || mood == ANGRY) 92f else 90f
                canvas.drawPath(Path().apply { moveTo(47f, 90f); quadTo(60f, lineY + 4f, 73f, 90f) }, stroke(0xFFC4650F.toInt(), 1.6f))
            }
            return
        }
        val dy = when (sp) { "dog" -> 5f; "seal" -> 2f; "panda" -> 3f; "bunny" -> -3f; "fox" -> 1f; "frog" -> -2f; else -> 0f }
        val ml = stroke(INK, 2.6f)
        val dot = paint(INK)
        canvas.save()
        canvas.translate(0f, dy)
        if (joy) {
            canvas.drawPath(Path().apply { moveTo(52f, 91f); quadTo(60f, 104f, 68f, 91f); close() }, dot)
            canvas.drawCircle(60f, 98f, 3f, paint(0xFFFF8FA3.toInt()))
        } else if (mood == HAPPY && look.mouth == 2) {
            canvas.drawOval(RectF(56f, 91f, 64f, 98f), dot)
        } else {
            val m = Path()
            val wide = sp == "frog" || sp == "axolotl"
            when (mood) {
                // 0 = :3 (alapértelmezett), 1 = mosoly
                HAPPY -> if (look.mouth == 1 || wide) {
                    if (wide) { m.moveTo(48f, 90f); m.quadTo(60f, 99f, 72f, 90f) } else { m.moveTo(54f, 92f); m.quadTo(60f, 97f, 66f, 92f) }
                } else {
                    m.moveTo(54f, 92f); m.quadTo(57f, 95f, 60f, 91f); m.quadTo(63f, 95f, 66f, 92f)
                }
                NEUTRAL -> { m.moveTo(56f, 94f); m.lineTo(64f, 94f) }
                else -> { m.moveTo(54f, 97f); m.quadTo(60f, 91f, 66f, 97f) }
            }
            canvas.drawPath(m, ml)
            // nyúlfogak és kutyanyelv a boldog arcon
            if (mood == HAPPY && sp == "bunny") {
                val t = RectF(57f, 93f, 63f, 98f)
                canvas.drawRect(t, paint(Color.WHITE)); canvas.drawRect(t, stroke(INK, 1f))
            }
            if (mood == HAPPY && sp == "dog") {
                canvas.drawPath(Path().apply { moveTo(56.5f, 93.5f); quadTo(56.5f, 103f, 60f, 103f); quadTo(63.5f, 103f, 63.5f, 93.5f); close() }, paint(0xFFFF8FA3.toInt()))
            }
        }
        canvas.restore()
    }

    private fun whiskers(canvas: Canvas, sp: String) {
        val w = stroke(if (sp == "seal") 0xFF2A2F35.toInt() else INK, 1.4f)
        val y0 = if (sp == "bunny") 88f else 92f
        canvas.drawLine(44f, y0, 28f, y0 - 3f, w)
        canvas.drawLine(44f, y0 + 4f, 28f, y0 + 6f, w)
        canvas.drawLine(76f, y0, 92f, y0 - 3f, w)
        canvas.drawLine(76f, y0 + 4f, 92f, y0 + 6f, w)
    }
}

// Régi név megtartva a kompatibilitás miatt
object CatDraw {
    fun draw(canvas: Canvas, size: Float, look: Look, mood: Int, offer: Boolean, lift: Float, joy: Boolean = false, pill: Boolean = false) =
        CritterDraw.draw(canvas, size, look, mood, offer, lift, joy, pill)
}

// A kiválasztott karakter rajzolása
object Mascot {
    fun draw(
        canvas: Canvas, size: Float, look: Look, mood: Int, offer: Boolean, pill: Boolean,
        lift: Float, joy: Boolean, blink: Boolean = false
    ) {
        CritterDraw.draw(canvas, size, look, mood, offer, lift, joy, pill, blink)
    }

    fun bitmap(c: Context, px: Int, mood: Int, offer: Boolean, pill: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), px.toFloat(), c.currentLook(), mood, offer, pill, 0f, false)
        return bmp
    }
}

// Pislogás-ütemező: 3,5–7 másodpercenként 140 ms-ra lecsukja a szemet
class Blinker(private val view: View) {
    var closed = false
        private set
    private val close = Runnable {
        closed = true
        view.invalidate()
        view.postDelayed(open, 140L)
    }
    private val open = Runnable {
        closed = false
        view.invalidate()
        schedule()
    }

    fun schedule() {
        if (!animationsOn()) return
        view.removeCallbacks(close)
        view.postDelayed(close, 3500L + Random.nextLong(3500L))
    }

    fun stop() {
        view.removeCallbacks(close)
        view.removeCallbacks(open)
        closed = false
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
    private val blinker = Blinker(this)

    private val idleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            idle = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsOn()) idleAnim.start()
        blinker.schedule()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        Mascot.draw(canvas, size, look, mood, offer, pill, bounceY - 3f * idle, joy, blinker.closed && !joy)
    }

    // Boldog ugrálás (pl. Ittam után)
    fun bounce() {
        if (!animationsOn()) return
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
        blinker.stop()
        super.onDetachedFromWindow()
    }
}
