package com.tpj.water

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

// Első indítás három lépésben:
// 1. karakter és név (élő előnézettel), 2. napi cél és ébrenléti idő, 3. engedélyek
class SetupActivity : Activity() {
    private var step = 0
    private var species = "cat"
    private var color = SpeciesList.byId("cat").color
    private var backCb: Any? = null
    private var eyes = 0
    private var mouth = 0
    private var whiskers = true
    private var sexSel = 0
    private var goal = 2500
    private var skipData = false

    private lateinit var cat: CatView
    private lateinit var scroll: ScrollView
    private lateinit var pages: List<View>
    private lateinit var dots: List<View>
    private lateinit var backBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var permBox: LinearLayout

    private lateinit var nameEt: EditText
    private lateinit var nameErr: TextView
    private lateinit var ageEt: EditText
    private lateinit var weightEt: EditText
    private lateinit var heightEt: EditText
    private lateinit var startEt: EditText
    private lateinit var endEt: EditText
    private lateinit var goalTv: TextView
    private lateinit var goalHint: TextView
    private lateinit var profileErr: TextView
    private lateinit var timeErr: TextView

    private val ERROR = 0xFFD64545.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this

        // a cica mindhárom lépésnél látszik, így a kinézet változása azonnal követhető
        cat = CatView(act).apply {
            offer = true
            applyLook(look())
        }

        pages = listOf(pageCharacter(), pageGoal(), pagePermissions())
        val pageBox = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(24))
            pages.forEach { addView(it) }
        }
        scroll = ScrollView(act).apply { addView(pageBox) }

        // lépésjelző pöttyök
        val dotRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        dots = List(3) {
            View(act).also { d -> dotRow.addView(d, LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(dp(4), 0, dp(4), 0) }) }
        }

        backBtn = button("Vissza", false) { showStep(step - 1) }
        nextBtn = button("Tovább", true) { next() }
        val nav = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(8), dp(20), dp(20))
            addView(backBtn, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(10) })
            addView(nextBtn, LinearLayout.LayoutParams(0, dp(52), 2f))
        }

        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.bg(act))
            setPadding(0, dp(8), 0, 0)
            applySystemBarPadding(top = true, bottom = true)
            addView(dotRow)
            addView(cat, LinearLayout.LayoutParams(dp(130), dp(130)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            })
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(nav)
        }
        themeWindow()
        setContentView(root)
        showStep(0)
    }

    override fun onResume() {
        super.onResume()
        // a beállításokból visszatérve frissül az engedélyek állapota
        if (::permBox.isInitialized) buildPerms()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        buildPerms()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step > 0) showStep(step - 1) else super.onBackPressed()
    }

    private fun look() = Look(color, eyes, mouth, whiskers, species)

    private fun preview() = cat.applyLook(look())

    private fun errorLabel(): TextView = label("", TypeScale.CAPTION, color = ERROR).apply {
        visibility = View.GONE
        setPadding(0, dp(4), 0, 0)
    }

    private fun showError(tv: TextView, text: String?) {
        tv.text = text ?: ""
        tv.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun heading(title: String, sub: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(label(title, TypeScale.TITLE, bold = true, center = true))
        addView(label(sub, TypeScale.BODY, center = true, color = Theme.sub(this@SetupActivity)))
    }

    private fun caption(text: String): TextView =
        label(text, TypeScale.BODY, bold = true).apply { setPadding(0, dp(14), 0, 0) }

    // ---- 1. lépés: karakter és név ----
    private fun pageCharacter(): View {
        val act = this
        nameEt = field("").apply { hint = "pl. Csepi" }
        nameErr = errorLabel()

        val colorHolder = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun buildColors() {
            colorHolder.removeAllViews()
            if (species == "panda") {
                colorHolder.addView(label("A panda mindig fekete-fehér.", TypeScale.CAPTION, color = Theme.sub(act)))
                return
            }
            val colors = colorChoices(species)
            colorHolder.addView(chipRow(List(colors.size) { "" }, colors.indexOf(color).coerceAtLeast(0), swatches = colors) {
                color = colors[it]
                preview()
            })
        }
        buildColors()

        // A tíz karakter rácsban; koppintásra előnézet és hangpróba
        val p = Theme.p(act)
        val grid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun buildGrid() {
            grid.removeAllViews()
            SpeciesList.ALL.chunked(5).forEach { items ->
                val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                items.forEach { sp ->
                    val on = sp.id == species
                    val tile = LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, dp(6), 0, dp(6))
                        background = rippleOf(rect(if (on) p.soft else android.graphics.Color.TRANSPARENT, 14).apply {
                            if (on) setStroke(dp(2), p.primary)
                        })
                        isClickable = true
                        contentDescription = sp.name + if (on) ", kiválasztva" else ""
                        addView(android.widget.ImageView(act).apply { setImageBitmap(speciesBitmap(act, sp, dp(48))) },
                            LinearLayout.LayoutParams(dp(48), dp(48)))
                        addView(label(sp.name, 11f, center = true, bold = on, color = if (on) p.text else p.sub))
                        setOnClickListener {
                            species = sp.id
                            color = sp.color
                            playSound(act, sp.sound)
                            buildGrid()
                            buildColors()
                            preview()
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

        return LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading("Válassz egy kis barátot", "Ő szól majd, ha ideje inni"))
            addView(caption("Karakter"))
            addView(grid)
            addView(caption("Neve"))
            addView(nameEt)
            addView(nameErr)
            addView(caption("Szín"))
            addView(colorHolder)
            addView(caption("Szem"))
            addView(chipRow(listOf("Pöttyös", "Boldog", "Nagy"), eyes) {
                eyes = it
                preview()
            })
            addView(caption("Száj"))
            addView(chipRow(listOf(":3", "Mosoly", "Nyitott"), mouth) {
                mouth = it
                preview()
            })
            addView(Switch(act).apply {
                text = "Bajusz"
                isChecked = whiskers
                setTextColor(Theme.text(act))
                setPadding(0, dp(14), 0, 0)
                setOnCheckedChangeListener { _, v ->
                    whiskers = v
                    preview()
                }
            })
        }
    }

    // ---- 2. lépés: napi cél és ébrenléti idő ----
    private fun pageGoal(): View {
        val act = this
        ageEt = field("").apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "év" }
        weightEt = field("").apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "kg" }
        heightEt = field("").apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "cm (nem kötelező)" }
        startEt = field(fmtHm(6 * 60))
        endEt = field(fmtHm(22 * 60))
        profileErr = errorLabel()
        timeErr = errorLabel()
        goalTv = label("", 34f, bold = true, center = true)
        goalHint = label("Alapértelmezett cél", TypeScale.CAPTION, center = true, color = Theme.sub(act))

        val dataFields = listOf(ageEt, weightEt, heightEt)
        val calcBtn = button("Kiszámolom a profilom alapján", false) {
            val age = ageEt.text.toString().toIntOrNull() ?: -1
            val weight = weightEt.text.toString().toIntOrNull() ?: -1
            val height = heightEt.text.toString().toIntOrNull()
            when {
                age !in 1..120 -> showError(profileErr, "Az életkor 1 és 120 év között legyen.")
                weight !in 30..250 -> showError(profileErr, "A súly 30 és 250 kg között legyen.")
                height != null && height !in 100..230 -> showError(profileErr, "A magasság 100 és 230 cm között legyen.")
                else -> {
                    showError(profileErr, null)
                    setGoal(Water.suggestedGoal(sexSel, age, weight))
                    goalHint.text = "Javaslat a profilod alapján"
                }
            }
        }
        val skipSwitch = Switch(act).apply {
            text = "Nem adom meg az adataimat"
            setTextColor(Theme.text(act))
            setPadding(0, dp(12), 0, 0)
            setOnCheckedChangeListener { _, v ->
                skipData = v
                dataFields.forEach { it.isEnabled = !v }
                calcBtn.isEnabled = !v
                showError(profileErr, null)
                if (v) {
                    setGoal(2500)
                    goalHint.text = "Alapértelmezett cél"
                }
            }
        }

        // − cél + léptető 250 ml-enként
        val stepper = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(roundBtn("−") { setGoal(goal - 250) }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(goalTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(roundBtn("+") { setGoal(goal + 250) }, LinearLayout.LayoutParams(dp(52), dp(52)))
        }
        val goalCard = card().apply {
            addView(label("Napi cél", TypeScale.HEADLINE, bold = true, center = true))
            addView(stepper)
            addView(goalHint)
        }
        setGoal(goal)

        val timeRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Kezdete", TypeScale.CAPTION, color = Theme.sub(act)))
                addView(startEt)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
            addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Vége", TypeScale.CAPTION, color = Theme.sub(act)))
                addView(endEt)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        return LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading("Mennyit igyál naponta?", "Kiszámolom, vagy maradhat az alap 2,5 liter"))
            addView(goalCard)
            addView(caption("Nem"))
            addView(chipRow(listOf("Férfi", "Nő", "Egyéb"), sexSel) { sexSel = it })
            addView(caption("Kor"))
            addView(ageEt)
            addView(caption("Súly"))
            addView(weightEt)
            addView(caption("Magasság"))
            addView(heightEt)
            addView(profileErr)
            addView(calcBtn)
            addView(skipSwitch)
            addView(caption("Mikor igyunk? (Emlékeztetők)"))
            addView(label("Ebben az időszakban szólok. ÓÓ:PP formában, például 06:00.", TypeScale.CAPTION, color = Theme.sub(act)))
            addView(timeRow)
            addView(timeErr)
        }
    }

    private fun roundBtn(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 22f
        setTextColor(Theme.p(this@SetupActivity).onPrimary)
        background = rippleOf(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Theme.primary(this@SetupActivity))
        })
        setPadding(0, 0, 0, 0)
        setOnClickListener { onClick() }
    }

    private fun setGoal(v: Int) {
        goal = v.coerceIn(500, 5000)
        goalTv.text = "$goal ml"
    }

    // ---- 3. lépés: engedélyek, magyarázattal ----
    private fun pagePermissions(): View {
        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buildPerms()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading("Még néhány engedély", "Ezek nélkül nem tudok időben szólni"))
            addView(permBox)
        }
    }

    private fun notifOk(): Boolean = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun fullOk(): Boolean = Build.VERSION.SDK_INT < 34 ||
        getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun buildPerms() {
        permBox.removeAllViews()
        permBox.addView(permRow("Értesítések", "Enélkül egyáltalán nem tudok szólni.", notifOk()) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        permBox.addView(permRow("Pontos ébresztés", "Hogy percre pontosan jöjjön az emlékeztető.", Water.canExact(this)) {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        })
        permBox.addView(permRow("Akkumulátor", "Ne állítsa le a telefon az emlékeztetőket, ha napokig nem nyitod meg az appot.", KeepAlive.batteryOk(this)) {
            KeepAlive.requestBatteryExemption(this)
        })
        permBox.addView(permRow("Teljes képernyő", "Zárolt telefonon is megjelenek, nem csak egy kis értesítésként.", fullOk()) {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        })
    }

    private fun permRow(title: String, desc: String, ok: Boolean, onGrant: () -> Unit): View {
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, TypeScale.HEADLINE, bold = true))
            addView(label(desc, TypeScale.CAPTION, color = Theme.sub(this@SetupActivity)))
        }
        val action: View = if (ok) {
            label("Megvan", TypeScale.BODY, bold = true, color = 0xFF2E9E5B.toInt())
        } else {
            button("Engedélyezés", true, onGrant).apply {
                textSize = 13f
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            }
        }
        return card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
            addView(action)
        }
    }

    // ---- Lapozás ----
    private fun showStep(i: Int) {
        step = i.coerceIn(0, pages.size - 1)
        pages.forEachIndexed { idx, p -> p.visibility = if (idx == step) View.VISIBLE else View.GONE }
        dots.forEachIndexed { idx, d ->
            d.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (idx == step) Theme.primary(this@SetupActivity) else Theme.field(this@SetupActivity))
            }
        }
        backBtn.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        nextBtn.text = if (step == pages.size - 1) "Kész, indulhat!" else "Tovább"
        // a cica a lépéshez illő kelléket fogja
        cat.offer = step != 2
        cat.mood = HAPPY
        cat.invalidate()
        scroll.scrollTo(0, 0)
        // rendszer Vissza-gesztus (Android 13+): az előző lépésre visz, az első lépésen bezár
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            (backCb as? android.window.OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            backCb = null
            if (step > 0) {
                val cb = android.window.OnBackInvokedCallback { showStep(step - 1) }
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
                backCb = cb
            }
        }
    }

    private fun next() {
        when (step) {
            0 -> {
                if (nameEt.text.toString().trim().isEmpty()) {
                    showError(nameErr, "Adj nevet a karakternek.")
                    return
                }
                showError(nameErr, null)
                showStep(1)
            }
            1 -> {
                val start = parseHm(startEt.text.toString())
                val end = parseHm(endEt.text.toString())
                if (start == null || end == null || start >= end) {
                    showError(timeErr, "Adj meg két időpontot ÓÓ:PP formában, a lefekvés legyen későbbi.")
                    return
                }
                showError(timeErr, null)
                showStep(2)
            }
            else -> save()
        }
    }

    private fun save() {
        val start = parseHm(startEt.text.toString()) ?: 6 * 60
        val end = parseHm(endEt.text.toString()) ?: 22 * 60
        Water.prefs(this).edit()
            .putString("name", nameEt.text.toString().trim())
            .putString("species", species)
            .putInt("catColor", color)
            .putInt("eyes", eyes)
            .putInt("mouth", mouth)
            .putBoolean("whiskers", whiskers)
            .putInt("sex", sexSel)
            .putBoolean("skipData", skipData)
            .putInt("age", ageEt.text.toString().toIntOrNull() ?: 0)
            .putInt("weight", weightEt.text.toString().toIntOrNull() ?: 0)
            .putInt("height", heightEt.text.toString().toIntOrNull() ?: 0)
            .putInt("goal", goal)
            .putInt("start", start)
            .putInt("end", end)
            .putBoolean("setupDone", true)
            .apply()
        Water.scheduleNext(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
