package com.tpj.water

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Switch
import android.widget.Toast

// Beállítások: profil, értesítések, karakter, megjelenés, teszt, engedélyek, gyógyszerek
class SettingsActivity : Activity() {
    private lateinit var cat: CatView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this
        val p = Water.prefs(act)

        var sexSel = p.getInt("sex", 0)
        var colorIdx = BODY_COLORS.indexOf(p.getInt("catColor", BODY_COLORS[0])).coerceAtLeast(0)
        // ivásköz: 0 = 60 perc, 1 = 45 perc
        var gapIdx = if (p.getInt("gapMin", 60) == 45) 1 else 0

        cat = CatView(act).apply {
            layoutParams = lp(dp(150), dp(150)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            offer = true
        }
        fun pushLook() {
            cat.applyLook(act.currentLook())
        }

        fun optionalNumber(key: String): String =
            p.getInt(key, 0).takeIf { it > 0 }?.toString() ?: ""

        // Profil
        val nameEt = field(Water.name(act))
        val ageEt = field(optionalNumber("age")).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val weightEt = field(optionalNumber("weight")).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val heightEt = field(optionalNumber("height")).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val goalEt = field("${Water.goal(act)}").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val profil = card().apply {
            addView(label("Profil", 17f, bold = true))
            addView(label("Cica neve", 13f))
            addView(nameEt)
            addView(label("Nem", 13f))
            addView(chipRow(listOf("Férfi", "Nő", "Egyéb"), sexSel) {
                sexSel = it
                p.edit().putInt("sex", it).apply()
            })
            addView(label("Kor (év)", 13f))
            addView(ageEt)
            addView(label("Súly (kg)", 13f))
            addView(weightEt)
            addView(label("Magasság (cm)", 13f))
            addView(heightEt)
            addView(button("Javaslat a profil alapján", false) {
                val age = ageEt.text.toString().toIntOrNull() ?: 0
                val w = weightEt.text.toString().toIntOrNull() ?: 0
                if (age in 1..120 && w in 30..250) {
                    goalEt.setText("${Water.suggestedGoal(sexSel, age, w)}")
                } else {
                    Toast.makeText(act, "Adj meg életkort és súlyt.", Toast.LENGTH_LONG).show()
                }
            })
            addView(label("Napi cél (ml)", 13f))
            addView(goalEt)
        }

        // Értesítések
        val startEt = field(fmtHm(Water.startMin(act)))
        val endEt = field(fmtHm(Water.endMin(act)))
        val cupEt = field("${Water.cup(act)}").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val repeatSwitch = Switch(act).apply {
            text = "Ismétlő értesítések"
            isChecked = Water.repeatOn(act)
            setOnCheckedChangeListener { _, v -> p.edit().putBoolean("repeatOn", v).apply() }
        }
        val notifs = card().apply {
            addView(label("Értesítések", 17f, bold = true))
            addView(label("Kezdet (ÓÓ:PP)", 13f))
            addView(startEt)
            addView(label("Vége (ÓÓ:PP)", 13f))
            addView(endEt)
            addView(label("Pohár mérete (ml)", 13f))
            addView(cupEt)
            addView(label("Alap ivásköz (az app ettől gyorsíthat vagy lassíthat)", 13f))
            addView(chipRow(listOf("60 perc", "45 perc"), gapIdx) { gapIdx = it })
            addView(repeatSwitch)
            addView(label("Ismétlések száma", 13f))
            addView(chipRow(listOf("1", "2", "3"), (Water.repeatMax(act) - 1).coerceIn(0, 2)) {
                p.edit().putInt("repeatMax", it + 1).apply()
            })
        }

        // Cica kinézete; a színsor újraépül, ha karaktert váltunk
        val colorHolder = android.widget.LinearLayout(act).apply { orientation = android.widget.LinearLayout.VERTICAL }
        fun buildColors() {
            colorHolder.removeAllViews()
            colorHolder.addView(chipRow(List(BODY_COLORS.size) { "" }, colorIdx, swatches = BODY_COLORS) {
                colorIdx = it
                p.edit().putInt("catColor", BODY_COLORS[it]).apply()
                pushLook()
            })
        }
        buildColors()
        val catCard = card().apply {
            addView(label("Karakter", 17f, bold = true))
            addView(chipRow(listOf("Kék cica", "Cirmos cica"), if (act.currentLook().tabby) 1 else 0) {
                val tabby = it == 1
                colorIdx = if (tabby) ORANGE_INDEX else BLUE_INDEX
                p.edit()
                    .putString("species", if (tabby) "tabby" else "cat")
                    .putInt("catColor", BODY_COLORS[colorIdx])
                    .apply()
                buildColors()
                pushLook()
            })
            addView(cat)
            addView(label("Szín", 13f))
            addView(colorHolder)
            addView(label("Szem", 13f))
            addView(chipRow(listOf("Pöttyös", "Boldog", "Nagy"), p.getInt("eyes", 0)) {
                p.edit().putInt("eyes", it).apply()
                pushLook()
            })
            addView(label("Száj", 13f))
            addView(chipRow(listOf(":3", "Mosoly", "Nyitott"), p.getInt("mouth", 0)) {
                p.edit().putInt("mouth", it).apply()
                pushLook()
            })
            addView(Switch(act).apply {
                text = "Bajusz"
                isChecked = p.getBoolean("whiskers", true)
                setOnCheckedChangeListener { _, v ->
                    p.edit().putBoolean("whiskers", v).apply()
                    pushLook()
                }
            })
        }

        // Vitaminok és gyógyszerek
        val medsCard = card().apply {
            addView(label("Vitaminok és gyógyszerek", 17f, bold = true))
            addView(label("Időpontok és emlékeztetők beállítása", 13f))
            addView(button("Megnyitás", false) {
                act.startActivity(Intent(act, MedsActivity::class.java))
            })
        }

        // Megjelenés
        val darkSwitch = Switch(act).apply {
            text = "Sötét mód"
            isChecked = Theme.dark(act)
            setOnCheckedChangeListener { _, v ->
                p.edit().putBoolean("dark", v).apply()
                recreate()
            }
        }
        val display = card().apply {
            addView(label("Megjelenés", 17f, bold = true))
            addView(darkSwitch)
        }

        // Teszt: 5 másodperc múlva egy értesítés
        val test = card().apply {
            addView(label("Teszt", 17f, bold = true))
            addView(button("Teszt értesítés 5 mp múlva", true) {
                Water.scheduleTest(act)
                Toast.makeText(act, "Az értesítés 5 másodperc múlva jön", Toast.LENGTH_SHORT).show()
            })
        }

        // Engedélyek
        val perms = card().apply {
            addView(label("Engedélyek", 17f, bold = true))
            addView(button("Értesítések engedélyezése", false) {
                if (Build.VERSION.SDK_INT >= 33) {
                    act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            })
            addView(button("Pontos ébresztés", false) {
                act.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${act.packageName}")))
            })
            addView(button("Teljes képernyős értesítés", false) {
                act.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${act.packageName}")))
            })
        }

        // Háttérben futás: hogy napokig megnyitás nélkül is szóljon
        val background = card().apply {
            addView(label("Háttérben futás", 17f, bold = true))
            addView(label(
                if (KeepAlive.batteryOk(act)) "Akkumulátor-optimalizálás: kikapcsolva (jó)"
                else "Akkumulátor-optimalizálás: bekapcsolva, a telefon leállíthatja az emlékeztetőket",
                13f
            ))
            addView(button("Akkumulátor-optimalizálás kikapcsolása", false) { KeepAlive.requestBatteryExemption(act) })
            addView(button("Gyártói beállítások (automatikus indítás)", false) { KeepAlive.openAppDetails(act) })
            addView(Switch(act).apply {
                text = "Állandó értesítés a mai állással (életben tartja az appot)"
                isChecked = KeepAlive.statusOn(act)
                setOnCheckedChangeListener { _, v -> KeepAlive.setStatus(act, v) }
            })
        }

        val save = button("Mentés", true) {
            val name = nameEt.text.toString().trim().ifEmpty { Water.name(act) }
            val goal = goalEt.text.toString().toIntOrNull() ?: 0
            val cup = cupEt.text.toString().toIntOrNull() ?: 0
            val start = parseHm(startEt.text.toString())
            val end = parseHm(endEt.text.toString())
            val age = ageEt.text.toString().toIntOrNull() ?: 0
            val weight = weightEt.text.toString().toIntOrNull() ?: 0
            val height = heightEt.text.toString().toIntOrNull() ?: 0
            if (goal < 500 || cup < 50 || start == null || end == null || start >= end) {
                Toast.makeText(act, "Ellenőrizd a beállításokat.", Toast.LENGTH_LONG).show()
            } else {
                p.edit()
                    .putString("name", name)
                    .putInt("goal", goal)
                    .putInt("cup", cup)
                    .putInt("start", start)
                    .putInt("end", end)
                    .putInt("age", age)
                    .putInt("weight", weight)
                    .putInt("height", height)
                    .putInt("gapMin", listOf(60, 45)[gapIdx])
                    .apply()
                Water.scheduleNext(act)
                Toast.makeText(act, "Mentve", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        val content = column().apply {
            addView(label("Beállítások", 26f, bold = true))
            addView(profil)
            addView(notifs)
            addView(catCard)
            addView(medsCard)
            addView(display)
            addView(test)
            addView(perms)
            addView(background)
            addView(save)
        }
        setContentView(screen(content))
    }
}
