package com.tpj.water

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// Márkaszínek (a vízjelenet és a rajzok használják)
val TIDE = 0xFF1E88C9.toInt()
val LAGOON = 0xFF0B3B5C.toInt()
val FOAM = 0xFFE8F6FD.toInt()
val BUBBLE = 0xFF7DD3F5.toInt()

// ---------- Tokenek és témák ----------
// Minden téma ugyanazt a tokenkészletet írja felül; a képernyők csak ezeket használják.
data class Palette(
    val id: String,
    val dark: Boolean,
    val bg: Int,          // oldal háttere
    val surface: Int,     // kártyák, listák, alsó sáv
    val field: Int,       // beviteli mezők, halvány gombok háttere
    val text: Int,        // fő szöveg
    val sub: Int,         // másodlagos szöveg
    val line: Int,        // elválasztó vonalak
    val primary: Int,     // fő kiemelő szín (gombok)
    val onPrimary: Int,   // szöveg a fő kiemelő színen
    val soft: Int,        // halvány kiemelés (másodlagos gomb, kijelölés)
    val onSoft: Int,      // szöveg a halvány kiemelésen
    val skyTop: Int,
    val skyBottom: Int,
    val waterBack: Int,
    val waterTop: Int,
    val waterBottom: Int
)

object Themes {
    val LIGHT = Palette(
        "light", false,
        0xFFF4F9FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFEAF4FA.toInt(),
        0xFF0B2A3E.toInt(), 0xFF4A6B80.toInt(), 0xFFDCE9F1.toInt(),
        0xFF1677B5.toInt(), 0xFFFFFFFF.toInt(), 0xFFD8ECF8.toInt(), 0xFF0E5F94.toInt(),
        0xFFF6FCFF.toInt(), 0xFFD6EEFA.toInt(), BUBBLE, TIDE, LAGOON
    )
    val OLED = Palette(
        "oled", true,
        0xFF000000.toInt(), 0xFF0F1418.toInt(), 0xFF161D22.toInt(),
        0xFFEAF4FA.toInt(), 0xFF8FA7B6.toInt(), 0xFF1C252C.toInt(),
        0xFF7DD3F5.toInt(), 0xFF04283D.toInt(), 0xFF14232E.toInt(), 0xFFA9E2F8.toInt(),
        0xFF050A0F.toInt(), 0xFF000000.toInt(), 0xFF2B6E95.toInt(), 0xFF14567F.toInt(), 0xFF0A2E45.toInt()
    )
    val OCEAN = Palette(
        "ocean", true,
        0xFF0B2233.toInt(), 0xFF12324A.toInt(), 0xFF0E2A3E.toInt(),
        0xFFE6F3FB.toInt(), 0xFF9FC1D6.toInt(), 0xFF1D4560.toInt(),
        0xFF7DD3F5.toInt(), 0xFF04283D.toInt(), 0xFF1F4D6B.toInt(), 0xFFBDE9FA.toInt(),
        0xFF143247.toInt(), 0xFF0B1C2A.toInt(), BUBBLE, TIDE, LAGOON
    )
    val LAVENDER = Palette(
        "lavender", false,
        0xFFF6F3FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFEEEAFB.toInt(),
        0xFF24194D.toInt(), 0xFF5F5788.toInt(), 0xFFECE8FA.toInt(),
        0xFF5A3FC4.toInt(), 0xFFFFFFFF.toInt(), 0xFFE6E0FF.toInt(), 0xFF3F2A99.toInt(),
        0xFFFBF9FF.toInt(), 0xFFE6E0FF.toInt(), 0xFFB9A8FF.toInt(), 0xFF7C5CFA.toInt(), 0xFF3F2A99.toInt()
    )
    val MINT = Palette(
        "mint", false,
        0xFFF1FAF7.toInt(), 0xFFFFFFFF.toInt(), 0xFFE3F4EE.toInt(),
        0xFF0F3A31.toInt(), 0xFF4C7268.toInt(), 0xFFDCEFE8.toInt(),
        0xFF0F7D6E.toInt(), 0xFFFFFFFF.toInt(), 0xFFCFEFE6.toInt(), 0xFF0A5A4F.toInt(),
        0xFFF5FCFA.toInt(), 0xFFD3F1E8.toInt(), 0xFF8EE0CF.toInt(), 0xFF2BB3A3.toInt(), 0xFF0F6E5E.toInt()
    )

    val ALL = listOf(LIGHT, OLED, OCEAN, LAVENDER, MINT)
    val NAMES = listOf("Világos", "Fekete", "Mélytenger", "Levendula", "Menta")

    fun byId(id: String?): Palette = ALL.firstOrNull { it.id == id } ?: LIGHT
}

object Theme {
    // A kiválasztott téma; "Rendszer követése" esetén sötét rendszernél sötét, világosnál világos párt kap
    fun p(c: Context): Palette {
        val prefs = Water.prefs(c)
        // régi beállítás átvétele: a korábbi "Sötét mód" a Mélytenger lesz
        val chosen = Themes.byId(prefs.getString("theme", if (prefs.getBoolean("dark", false)) "ocean" else "light"))
        if (!prefs.getBoolean("followSystem", false)) return chosen
        val sysDark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return when {
            sysDark && !chosen.dark -> Themes.OCEAN
            !sysDark && chosen.dark -> Themes.LIGHT
            else -> chosen
        }
    }

    fun dark(c: Context) = p(c).dark
    fun bg(c: Context) = p(c).bg
    fun card(c: Context) = p(c).surface
    fun text(c: Context) = p(c).text
    fun sub(c: Context) = p(c).sub
    fun field(c: Context) = p(c).field
    fun primary(c: Context) = p(c).primary

    // Takarékos mód: mattüveg-elmosás kikapcsolva gyengébb eszközön
    fun lite(c: Context) = Water.prefs(c).getBoolean("lite", false)
}

// ---------- Betűk: beágyazott Nunito (offline is működik) ----------
object Fonts {
    private val cache = HashMap<Int, Typeface>()

    fun weight(c: Context, w: Int): Typeface = cache.getOrPut(w) {
        runCatching {
            Typeface.Builder(c.assets, "fonts/Nunito.ttf")
                .setFontVariationSettings("'wght' $w")
                .build()
        }.getOrNull() ?: Typeface.create("sans-serif-rounded", if (w >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    fun display(c: Context) = weight(c, 800)
    fun ui(c: Context, bold: Boolean) = weight(c, if (bold) 750 else 500)
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

// Tipográfiai skála
object TypeScale {
    const val DISPLAY = 48f
    const val TITLE = 24f
    const val HEADLINE = 17f
    const val BODY = 15f
    const val CAPTION = 12f
}

// Erős ease-out görbe minden belépő mozgáshoz
val EASE_OUT = PathInterpolator(0.23f, 1f, 0.32f, 1f)

fun animationsOn(): Boolean = ValueAnimator.areAnimatorsEnabled()

// Lenyomáskor 0,97-re húzódik össze, elengedéskor visszaáll (a kattintást nem veszi el)
fun View.pressFeedback() {
    setOnTouchListener { v, e ->
        if (animationsOn()) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).setInterpolator(EASE_OUT).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(EASE_OUT).start()
            }
        }
        false
    }
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

fun rippleOf(bg: Drawable, color: Int = Color.argb(60, 128, 128, 128)): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(color), bg, null)

fun Context.lp(w: Int, h: Int, top: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams {
    val ctx = this
    return LinearLayout.LayoutParams(w, h, weight).apply { topMargin = ctx.dp(top) }
}

fun Context.card(): LinearLayout {
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = ctx.rect(Theme.card(ctx), 20)
        setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        layoutParams = ctx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 12)
    }
}

fun Context.column(): LinearLayout {
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(28))
    }
}

// Görgethető képernyő; a rendszersávok (állapotsor, navigációs sáv, billentyűzet) alá nem csúszik be
fun Context.screen(content: View): ScrollView {
    val sv = ScrollView(this)
    sv.setBackgroundColor(Theme.bg(this))
    sv.addView(content)
    sv.isFillViewport = true
    sv.applySystemBarPadding(top = true, bottom = true)
    return sv
}

// Széltől szélig megjelenítés: a nézet belső margót kap a rendszersávok és a billentyűzet méretével
fun View.applySystemBarPadding(top: Boolean, bottom: Boolean) {
    val l = paddingLeft
    val t = paddingTop
    val r = paddingRight
    val b = paddingBottom
    setOnApplyWindowInsetsListener { v, insets ->
        // [bal, fent, jobb, lent]
        val s: IntArray = if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            intArrayOf(i.left, i.top, i.right, i.bottom)
        } else {
            @Suppress("DEPRECATION")
            intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        v.setPadding(l + s[0], t + (if (top) s[1] else 0), r + s[2], b + (if (bottom) s[3] else 0))
        insets
    }
    requestApplyInsets()
}

// Ablak színei a témához: háttér, és sötét ikonok világos témában az állapot- és navigációs sávon
fun Activity.themeWindow() {
    val p = Theme.p(this)
    window.decorView.setBackgroundColor(p.bg)
    if (Build.VERSION.SDK_INT >= 30) {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (p.dark) 0 else mask, mask)
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (p.dark) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
    }
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
    tv.typeface = Fonts.ui(this, bold)
    if (center) tv.gravity = Gravity.CENTER
    return tv
}

// Számok azonos szélességű számjegyekkel, hogy ne ugráljanak
fun TextView.tabular(): TextView {
    fontFeatureSettings = "tnum"
    return this
}

// Egy gombpár: elsődleges (kitöltött) és másodlagos (halvány kiemelésű)
fun Context.button(label: String, filled: Boolean, onClick: () -> Unit): Button {
    val ctx = this
    val p = Theme.p(ctx)
    val b = Button(ctx)
    b.setText(label)
    b.isAllCaps = false
    b.textSize = 16f
    b.typeface = Fonts.ui(ctx, true)
    b.stateListAnimator = null
    b.setTextColor(if (filled) p.onPrimary else p.onSoft)
    b.background = rippleOf(ctx.rect(if (filled) p.primary else p.soft, 16))
    b.minHeight = ctx.dp(48)
    b.layoutParams = ctx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(52), top = 10)
    b.setOnClickListener { onClick() }
    b.pressFeedback()
    return b
}

fun Context.field(value: String): EditText {
    val ctx = this
    val e = EditText(ctx)
    e.setText(value)
    e.textSize = 17f
    e.typeface = Fonts.ui(ctx, false)
    e.setTextColor(Theme.text(ctx))
    e.setHintTextColor(Theme.sub(ctx))
    e.background = ctx.rect(Theme.field(ctx), 12)
    e.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
    e.minHeight = ctx.dp(48)
    e.layoutParams = ctx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 6)
    return e
}

// Választó sor: szöveges chipek, vagy színes mintapöttyök (swatches). Legalább 48 dp magas.
fun Context.chipRow(
    labels: List<String>,
    selected: Int,
    swatches: IntArray? = null,
    onPick: (Int) -> Unit
): LinearLayout {
    val ctx = this
    val p = Theme.p(ctx)
    var sel = selected
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.HORIZONTAL
    val btns = ArrayList<Button>()

    fun paint() {
        btns.forEachIndexed { i, b ->
            val on = i == sel
            if (swatches != null) {
                val d = ctx.rect(swatches[i], 24)
                if (on) d.setStroke(ctx.dp(3), p.text)
                b.background = rippleOf(d)
                b.text = ""
                b.contentDescription = "Szín ${i + 1}" + if (on) ", kiválasztva" else ""
            } else {
                b.setTextColor(if (on) p.onPrimary else p.text)
                b.text = labels[i]
                b.background = rippleOf(ctx.rect(if (on) p.primary else p.field, 14))
            }
        }
    }

    labels.indices.forEach { i ->
        val b = Button(ctx)
        b.isAllCaps = false
        b.textSize = 14f
        b.typeface = Fonts.ui(ctx, true)
        b.stateListAnimator = null
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(ctx.dp(4), 0, ctx.dp(4), 0)
        b.layoutParams = LinearLayout.LayoutParams(0, ctx.dp(48), 1f).apply {
            setMargins(ctx.dp(3), ctx.dp(8), ctx.dp(3), 0)
        }
        b.setOnClickListener {
            sel = i
            paint()
            onPick(i)
        }
        b.pressFeedback()
        btns.add(b)
        row.addView(b)
    }
    paint()
    return row
}

// Szakaszcím a csoportosított listák fölé
fun Context.sectionTitle(text: String): TextView =
    label(text, TypeScale.CAPTION, bold = true, color = Theme.sub(this)).apply {
        setPadding(dp(6), dp(18), dp(6), dp(6))
    }

// Csoport: lekerekített felület, benne elválasztott sorok
fun Context.group(vararg rows: View): LinearLayout {
    val ctx = this
    val p = Theme.p(ctx)
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = ctx.rect(p.surface, 18)
        rows.forEachIndexed { i, r ->
            if (i > 0) addView(View(ctx).apply { setBackgroundColor(p.line) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            addView(r)
        }
    }
}

// Egy listasor: cím, jobbra érték és nyíl; az egész sor megnyomható (legalább 48 dp)
fun Context.listRow(title: String, value: String = "", onClick: (() -> Unit)? = null): LinearLayout {
    val ctx = this
    val p = Theme.p(ctx)
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(52)
        setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(12), ctx.dp(12))
        addView(ctx.label(title, TypeScale.BODY), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (value.isNotEmpty()) addView(ctx.label(value, TypeScale.BODY, color = p.sub).tabular())
        if (onClick != null) {
            addView(ImageView(ctx).apply { setImageDrawable(ctx.tinted(R.drawable.ic_chevron_right, p.sub, 20)) },
                LinearLayout.LayoutParams(ctx.dp(20), ctx.dp(20)).apply { marginStart = ctx.dp(6) })
            isClickable = true
            isFocusable = true
            background = rippleOf(ctx.rect(Color.TRANSPARENT, 18))
            setOnClickListener { onClick() }
        }
    }
}

// Lenyíló: összefoglaló sor nyíllal; megnyomva kinyílik vagy összecsukódik a tartalom
fun Context.accordion(title: String, summary: String, body: View, open: Boolean = false): LinearLayout {
    val ctx = this
    val p = Theme.p(ctx)
    val chevron = ImageView(ctx).apply { setImageDrawable(ctx.tinted(R.drawable.ic_chevron, p.sub, 22)) }
    val sumTv = ctx.label(summary, TypeScale.CAPTION, color = p.sub)
    val header = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(56)
        setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(12), ctx.dp(10))
        isClickable = true
        isFocusable = true
        background = rippleOf(ctx.rect(Color.TRANSPARENT, 18))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ctx.label(title, TypeScale.BODY, bold = true))
            if (summary.isNotEmpty()) addView(sumTv)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(chevron, LinearLayout.LayoutParams(ctx.dp(22), ctx.dp(22)))
    }
    body.visibility = if (open) View.VISIBLE else View.GONE
    chevron.rotation = if (open) 180f else 0f
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = ctx.rect(p.surface, 18)
        layoutParams = ctx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10)
        addView(header)
        addView(body)
    }
    header.contentDescription = "$title, ${if (open) "kinyitva" else "összecsukva"}"
    header.setOnClickListener {
        val opening = body.visibility != View.VISIBLE
        header.contentDescription = "$title, ${if (opening) "kinyitva" else "összecsukva"}"
        if (opening) {
            body.visibility = View.VISIBLE
            if (animationsOn()) {
                body.alpha = 0f
                body.translationY = -ctx.dp(6).toFloat()
                body.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(EASE_OUT).start()
            }
        } else {
            body.visibility = View.GONE
        }
        chevron.animate().rotation(if (opening) 180f else 0f).setDuration(if (animationsOn()) 180 else 0).setInterpolator(EASE_OUT).start()
    }
    return box
}

// A lenyíló összefoglaló sorát később is lehet frissíteni (pl. "4 ivás, utolsó 09:30")
fun LinearLayout.setAccordionSummary(text: String) {
    val header = getChildAt(0) as? LinearLayout ?: return
    val texts = header.getChildAt(0) as? LinearLayout ?: return
    if (texts.childCount > 1) (texts.getChildAt(1) as? TextView)?.text = text
}

// ---------- Alsó navigáció ----------
// A Ma a gyökér; a többi fül a Ma fölött nyílik meg, így a Vissza mindig oda visz, ahonnan jöttél.
object Nav {
    const val TODAY = 0
    const val STATS = 1
    const val MEDS = 2
    const val SETTINGS = 3

    private val targets = listOf(
        MainActivity::class.java, StatsActivity::class.java, MedsActivity::class.java, SettingsActivity::class.java
    )
    private val labels = listOf("Ma", "Statisztika", "Gyógyszer", "Beállítások")
    private val icons = listOf(R.drawable.ic_drop, R.drawable.ic_chart, R.drawable.ic_pill, R.drawable.ic_gear)

    fun go(a: Activity, current: Int, target: Int) {
        if (target == current) return
        when {
            target == TODAY -> a.finish()
            current == TODAY -> a.startActivity(Intent(a, targets[target]))
            else -> {
                a.startActivity(Intent(a, targets[target]))
                a.finish()
            }
        }
        @Suppress("DEPRECATION")
        a.overridePendingTransition(0, 0)
    }

    fun bar(a: Activity, current: Int): LinearLayout {
        val p = Theme.p(a)
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(p.surface)
            setPadding(a.dp(6), a.dp(6), a.dp(6), a.dp(8))
        }
        labels.indices.forEach { i ->
            val on = i == current
            val pill = LinearLayout(a).apply {
                gravity = Gravity.CENTER
                background = a.rect(if (on) p.soft else Color.TRANSPARENT, 16)
                addView(ImageView(a).apply {
                    setImageDrawable(a.tinted(icons[i], if (on) p.onSoft else p.sub, 22))
                }, LinearLayout.LayoutParams(a.dp(22), a.dp(22)))
            }
            val item = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                minimumHeight = a.dp(56)
                isClickable = true
                isFocusable = true
                contentDescription = labels[i] + if (on) ", kiválasztva" else ""
                background = rippleOf(a.rect(Color.TRANSPARENT, 16))
                addView(pill, LinearLayout.LayoutParams(a.dp(56), a.dp(30)))
                addView(a.label(labels[i], 11f, bold = on, center = true, color = if (on) p.text else p.sub),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = a.dp(3) })
                setOnClickListener { go(a, current, i) }
            }
            row.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        row.applySystemBarPadding(top = false, bottom = true)
        return row
    }

    // Fül-képernyő: felül a görgethető tartalom, alul az állandó navigáció
    fun screen(a: Activity, current: Int, content: View): LinearLayout {
        val p = Theme.p(a)
        val sv = ScrollView(a).apply {
            setBackgroundColor(p.bg)
            isFillViewport = true
            addView(content)
            applySystemBarPadding(top = true, bottom = false)
        }
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.bg)
            addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar(a, current))
        }
    }
}

// Képernyőcím: a márkabetűvel
fun Context.screenTitle(text: String): TextView =
    label(text, TypeScale.TITLE, color = Theme.p(this).text).apply {
        typeface = Fonts.display(this@screenTitle)
        // kiegyensúlyozott sortörés a címekben
        breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        setPadding(dp(4), dp(8), 0, dp(4))
    }

fun View.reveal(delay: Long) {
    if (!animationsOn()) return
    alpha = 0f
    translationY = 16f
    animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(240).setInterpolator(EASE_OUT).start()
}

fun parseHm(s: String): Int? {
    val p = s.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

fun fmtHm(min: Int): String = String.format("%02d:%02d", min / 60, min % 60)
