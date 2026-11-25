package com.tpj.water

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

// Kis előnézeti kép egy fajról (a karakterválasztó rácshoz)
fun speciesBitmap(c: Context, sp: Species, px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    CritterDraw.draw(Canvas(bmp), px.toFloat(), Look(sp.color, 0, 0, true, sp.id), HAPPY, false, 0f)
    return bmp
}

// Beállítások: csoportosított lista, azonnali mentés "Mentve" jelzéssel
class SettingsActivity : Activity() {
    private lateinit var pal: Palette
    private lateinit var cat: CatView
    private lateinit var savedTv: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideSaved = Runnable { savedTv.animate().alpha(0f).setDuration(200).start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this
        val p = Water.prefs(act)
        pal = Theme.p(act)
        themeWindow()

        // Fejléc a "Mentve" jelzéssel
        savedTv = label("Mentve", TypeScale.CAPTION, bold = true, color = pal.onSoft).apply {
            background = rect(pal.soft, 12)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            alpha = 0f
        }
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(screenTitle("Beállítások"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(savedTv)
        }

        // ---------- Megjelenés ----------
        val current = Themes.byId(p.getString("theme", if (p.getBoolean("dark", false)) "ocean" else "light"))
        val swatches = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(12))
        }
        Themes.ALL.forEachIndexed { i, th ->
            val on = th.id == current.id
            val dot = View(act).apply {
                background = rect(if (th.id == "light") th.skyBottom else if (th.dark) th.surface else th.primary, 24).apply {
                    setStroke(dp(if (on) 3 else 1), if (on) pal.text else pal.line)
                }
                contentDescription = Themes.NAMES[i] + if (on) ", kiválasztva" else ""
                isClickable = true
                setOnClickListener {
                    p.edit().putString("theme", th.id).putBoolean("dark", th.dark).apply()
                    recreate()
                }
            }
            val cell = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(dot, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(label(Themes.NAMES[i], 11f, center = true, color = if (on) pal.text else pal.sub, bold = on),
                    lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 4))
            }
            swatches.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val themeRow = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Téma", TypeScale.BODY).apply { setPadding(dp(16), dp(14), dp(16), dp(10)) })
            addView(swatches)
        }
        val appearance = group(
            themeRow,
            switchRow("Rendszer követése", "Sötét rendszerben sötét, világosban világos", p.getBoolean("followSystem", false)) {
                p.edit().putBoolean("followSystem", it).apply()
                recreate()
            },
            switchRow("Takarékos mód", "Mattüveg-elmosás kikapcsolása gyengébb telefonon", p.getBoolean("lite", false)) {
                p.edit().putBoolean("lite", it).apply()
                saved()
            }
        )

        // ---------- Napi rutin ----------
        val goalRow = fieldRow("Napi cél", "${Water.goal(act)}", "ml", true) { v ->
            val g = v.toIntOrNull()
            if (g == null || g !in 500..6000) "500 és 6000 ml között legyen."
            else { p.edit().putInt("goal", g).apply(); Water.scheduleNext(act); null }
        }
        val cupRow = fieldRow("Pohár", "${Water.cup(act)}", "ml", true) { v ->
            val c = v.toIntOrNull()
            if (c == null || c !in 50..1000) "50 és 1000 ml között legyen."
            else { p.edit().putInt("cup", c).apply(); Water.scheduleNext(act); null }
        }
        val startRow = fieldRow("Emlékeztetők kezdete", fmtHm(Water.startMin(act)), "", false) { v ->
            val m = parseHm(v)
            if (m == null) "ÓÓ:PP formában, például 06:00."
            else if (m >= Water.endMin(act)) "Legyen korábbi, mint a vége."
            else { p.edit().putInt("start", m).apply(); Water.scheduleNext(act); null }
        }
        val endRow = fieldRow("Emlékeztetők vége", fmtHm(Water.endMin(act)), "", false) { v ->
            val m = parseHm(v)
            if (m == null) "ÓÓ:PP formában, például 22:00."
            else if (m <= Water.startMin(act)) "Legyen későbbi, mint a kezdete."
            else { p.edit().putInt("end", m).apply(); Water.scheduleNext(act); null }
        }
        val gapIdx = if (p.getInt("gapMin", 60) == 45) 1 else 0
        val gapRow = chipBlock("Alap ivásköz", "Ehhez képest gyorsítok, ha lemaradsz", listOf("60 perc", "45 perc"), gapIdx) {
            p.edit().putInt("gapMin", if (it == 1) 45 else 60).apply()
            Water.scheduleNext(act)
            saved()
        }
        val repeatRow = chipBlock("Ismétlés, ha nem reagálsz", "10 percenként, egyre szomorúbban",
            listOf("Ki", "1×", "2×", "3×"), if (Water.repeatOn(act)) Water.repeatMax(act).coerceIn(1, 3) else 0) {
            if (it == 0) p.edit().putBoolean("repeatOn", false).apply()
            else p.edit().putBoolean("repeatOn", true).putInt("repeatMax", it).apply()
            saved()
        }
        val routine = group(goalRow, cupRow, startRow, endRow, gapRow, repeatRow)

        // ---------- Értesítés hangja ----------
        val soundBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun buildSounds() {
            soundBox.removeAllViews()
            val cur = NotifSounds.current(act).key
            val rows = NotifSounds.ALL.map { s ->
                val on = s.key == cur
                LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(52)
                    setPadding(dp(16), dp(10), dp(14), dp(10))
                    isClickable = true
                    isFocusable = true
                    contentDescription = s.name + if (on) ", kiválasztva" else ""
                    background = rippleOf(rect(Color.TRANSPARENT, 18))
                    addView(label(s.name, TypeScale.BODY, bold = on), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (on) addView(ImageView(act).apply { setImageDrawable(tinted(R.drawable.ic_check, pal.primary, 22)) },
                        LinearLayout.LayoutParams(dp(22), dp(22)))
                    setOnClickListener {
                        playSound(act, s.res)
                        NotifSounds.select(act, s.key)
                        buildSounds()
                        saved()
                    }
                }
            }
            soundBox.addView(group(*rows.toTypedArray()))
        }
        buildSounds()

        // ---------- Karakter ----------
        cat = CatView(act).apply {
            offer = true
            contentDescription = "Karakter előnézet, koppints rá"
            setOnClickListener {
                celebrate()
                PetSounds.voice(act)
            }
        }
        var colorIdx = 0
        val colorHolder = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun pushLook() {
            cat.applyLook(act.currentLook())
            saved()
        }
        fun buildColors() {
            val sp = act.currentSpecies()
            val colors = colorChoices(sp.id)
            colorIdx = colors.indexOf(p.getInt("catColor", sp.color)).coerceAtLeast(0)
            colorHolder.removeAllViews()
            if (sp.id == "panda") {
                colorHolder.addView(label("A panda mindig fekete-fehér.", TypeScale.CAPTION, color = pal.sub).apply { setPadding(0, dp(8), 0, 0) })
                return
            }
            colorHolder.addView(chipRow(List(colors.size) { "" }, colorIdx, swatches = colors) {
                p.edit().putInt("catColor", colors[it]).apply()
                pushLook()
            })
        }
        buildColors()

        val grid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun buildGrid() {
            grid.removeAllViews()
            val sel = act.currentSpecies().id
            SpeciesList.ALL.chunked(5).forEach { rowItems ->
                val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                rowItems.forEach { sp ->
                    val on = sp.id == sel
                    val tile = LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, dp(6), 0, dp(6))
                        background = rippleOf(rect(if (on) pal.soft else Color.TRANSPARENT, 14).apply {
                            if (on) setStroke(dp(2), pal.primary)
                        })
                        isClickable = true
                        contentDescription = sp.name + if (on) ", kiválasztva" else ""
                        addView(ImageView(act).apply { setImageBitmap(speciesBitmap(act, sp, dp(48))) },
                            LinearLayout.LayoutParams(dp(48), dp(48)))
                        addView(label(sp.name, 11f, center = true, bold = on, color = if (on) pal.text else pal.sub))
                        setOnClickListener {
                            p.edit().putString("species", sp.id).putInt("catColor", sp.color).apply()
                            playSound(act, sp.sound)
                            buildGrid()
                            buildColors()
                            pushLook()
                        }
                    }
                    row.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    })
                }
                grid.addView(row)
            }
        }
        buildGrid()

        val nameRow = fieldRow("Neve", Water.name(act), "", false, numeric = false) { v ->
            if (v.isBlank()) "Adj nevet a karakternek."
            else { p.edit().putString("name", v.trim()).apply(); null }
        }
        val looks = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(14))
            addView(label("Szín", TypeScale.CAPTION, color = pal.sub))
            addView(colorHolder)
            addView(label("Szem", TypeScale.CAPTION, color = pal.sub), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10))
            addView(chipRow(listOf("Pöttyös", "Boldog", "Nagy"), p.getInt("eyes", 0)) {
                p.edit().putInt("eyes", it).apply(); pushLook()
            })
            addView(label("Száj", TypeScale.CAPTION, color = pal.sub), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10))
            addView(chipRow(listOf(":3", "Mosoly", "Nyitott"), p.getInt("mouth", 0)) {
                p.edit().putInt("mouth", it).apply(); pushLook()
            })
            addView(switchRow("Bajusz", "Ahol a fajhoz illik", p.getBoolean("whiskers", true)) {
                p.edit().putBoolean("whiskers", it).apply(); pushLook()
            }.apply { setPadding(0, dp(10), 0, 0) })
        }
        val charBox = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(pal.surface, 18)
            setPadding(dp(8), dp(10), dp(8), dp(8))
            addView(cat, LinearLayout.LayoutParams(dp(110), dp(110)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(grid)
        }

        // ---------- Profil (lenyíló) ----------
        val sexIdx = p.getInt("sex", 0)
        var sexSel = sexIdx
        val ageEt = smallField(p.getInt("age", 0).takeIf { it > 0 }?.toString() ?: "", "év")
        val weightEt = smallField(p.getInt("weight", 0).takeIf { it > 0 }?.toString() ?: "", "kg")
        val heightEt = smallField(p.getInt("height", 0).takeIf { it > 0 }?.toString() ?: "", "cm")
        val profErr = label("", TypeScale.CAPTION, color = 0xFFD64545.toInt()).apply { visibility = View.GONE }
        val profile = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(14))
            addView(chipRow(listOf("Férfi", "Nő", "Egyéb"), sexIdx) { sexSel = it; p.edit().putInt("sex", it).apply(); saved() })
            addView(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf("Kor" to ageEt, "Súly" to weightEt, "Magasság" to heightEt).forEach { (t, e) ->
                    addView(LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label(t, TypeScale.CAPTION, color = pal.sub))
                        addView(e)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(10), dp(3), 0) })
                }
            })
            addView(profErr)
            addView(button("Napi cél javaslata a profil alapján", false) {
                val age = ageEt.text.toString().toIntOrNull() ?: 0
                val w = weightEt.text.toString().toIntOrNull() ?: 0
                val h = heightEt.text.toString().toIntOrNull() ?: 0
                if (age !in 1..120 || w !in 30..250) {
                    profErr.text = "Add meg az életkort (1–120) és a súlyt (30–250 kg)."
                    profErr.visibility = View.VISIBLE
                } else {
                    profErr.visibility = View.GONE
                    val g = Water.suggestedGoal(sexSel, age, w)
                    p.edit().putInt("age", age).putInt("weight", w).putInt("height", h).putInt("goal", g).apply()
                    (goalRow.tag as? EditText)?.setText("$g")
                    Water.scheduleNext(act)
                    saved()
                }
            })
        }

        // ---------- Haladó (lenyíló): engedélyek, háttérben futás, teszt ----------
        val advanced = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(14))
            addView(label("Engedélyek", TypeScale.CAPTION, bold = true, color = pal.sub))
            addView(button("Értesítések engedélyezése", false) {
                if (Build.VERSION.SDK_INT >= 33) act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            })
            addView(button("Pontos ébresztés", false) {
                act.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${act.packageName}")))
            })
            addView(button("Teljes képernyős értesítés", false) {
                act.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${act.packageName}")))
            })
            addView(label("Háttérben futás", TypeScale.CAPTION, bold = true, color = pal.sub), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 16))
            addView(label(
                if (KeepAlive.batteryOk(act)) "Akkumulátor-optimalizálás: kikapcsolva (jó)"
                else "Akkumulátor-optimalizálás: bekapcsolva, a telefon leállíthatja az emlékeztetőket",
                TypeScale.CAPTION, color = pal.sub
            ))
            addView(button("Akkumulátor-optimalizálás kikapcsolása", false) { KeepAlive.requestBatteryExemption(act) })
            addView(button("Gyártói beállítások (automatikus indítás)", false) { KeepAlive.openAppDetails(act) })
            addView(switchRow("Állandó értesítés a mai állással", "Életben tartja az appot a háttérben", KeepAlive.statusOn(act)) {
                KeepAlive.setStatus(act, it)
                saved()
            }.apply { setPadding(0, dp(8), 0, 0) })
            addView(label("Próba", TypeScale.CAPTION, bold = true, color = pal.sub), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 16))
            addView(button("Teszt értesítés 5 másodperc múlva", true) {
                Water.scheduleTest(act)
                savedTv.text = "5 mp múlva jön"
                saved()
            })
        }

        val content = column().apply {
            addView(header)
            addView(sectionTitle("Megjelenés"))
            addView(appearance)
            addView(sectionTitle("Napi rutin"))
            addView(routine)
            addView(sectionTitle("Értesítés hangja"))
            addView(soundBox)
            addView(sectionTitle("Karakter"))
            addView(charBox)
            addView(group(nameRow).apply { layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10) })
            addView(accordion("Kinézet", "szín, szem, száj, bajusz", looks))
            addView(accordion("Profil", "nem, kor, súly, magasság", profile))
            addView(accordion("Haladó", "értesítések, engedélyek, háttérben futás, teszt", advanced))
        }
        setContentView(Nav.screen(this, Nav.SETTINGS, content))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // "Mentve" jelzés rövid ideig
    private fun saved() {
        savedTv.animate().cancel()
        savedTv.alpha = 1f
        handler.removeCallbacks(hideSaved)
        handler.postDelayed(hideSaved, 1400L)
        handler.postDelayed({ savedTv.text = "Mentve" }, 1700L)
    }

    private fun switchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = Switch(this).apply {
            isChecked = checked
            contentDescription = title
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(8), dp(12), dp(8))
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, TypeScale.BODY))
                if (sub.isNotEmpty()) addView(label(sub, TypeScale.CAPTION, color = pal.sub))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw)
            setOnClickListener { sw.toggle() }
        }
    }

    private fun chipBlock(title: String, sub: String, options: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(13), dp(12))
            addView(label(title, TypeScale.BODY))
            addView(label(sub, TypeScale.CAPTION, color = pal.sub))
            addView(chipRow(options, selected, onPick = onPick))
        }

    private fun smallField(value: String, hint: String): EditText = field(value).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER
    }

    // Szerkeszthető sor: cím balra, rövid mező jobbra. Gépelés után rövid szünettel ment,
    // hibánál a sor alatt piros szöveg jelenik meg. A mező a sor tag-jében is elérhető.
    private fun fieldRow(
        title: String, value: String, unit: String, numericOnly: Boolean, numeric: Boolean = numericOnly,
        commit: (String) -> String?
    ): LinearLayout {
        val err = label("", TypeScale.CAPTION, color = 0xFFD64545.toInt()).apply {
            visibility = View.GONE
            setPadding(dp(16), 0, dp(16), dp(10))
        }
        val et = field(value).apply {
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            if (!numeric && title.contains("Emlékeztetők")) inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setSingleLine()
            tabular()
            contentDescription = title
            layoutParams = LinearLayout.LayoutParams(dp(if (numeric || title.contains("Emlékeztetők")) 96 else 150), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val pending = Runnable {
            val msg = commit(et.text.toString().trim())
            if (msg == null) {
                err.visibility = View.GONE
                saved()
            } else {
                err.text = msg
                err.visibility = View.VISIBLE
            }
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                handler.removeCallbacks(pending)
                handler.postDelayed(pending, 700L)
            }
        })
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(4), dp(12), dp(4))
            addView(label(title, TypeScale.BODY), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(et)
            if (unit.isNotEmpty()) addView(label(unit, TypeScale.BODY, color = pal.sub).apply { setPadding(dp(6), 0, 0, 0) })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = et
            addView(line)
            addView(err)
        }
    }
}
