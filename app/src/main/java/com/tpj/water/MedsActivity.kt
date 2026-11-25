package com.tpj.water

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

// Vitamin- és gyógyszer-emlékeztetők: felül a mai lista állapottal, alatta az új emlékeztető lenyílóban
class MedsActivity : Activity() {
    private lateinit var pal: Palette
    private lateinit var listBox: LinearLayout
    private lateinit var undoBar: LinearLayout
    private lateinit var undoText: TextView
    private var removed: Med? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideUndo = Runnable {
        removed = null
        undoBar.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this
        pal = Theme.p(this)
        themeWindow()

        listBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }

        // Új emlékeztető: időpont és név, hiba a mező alatt
        val timeEt = field("08:00").apply { hint = "08:00" }
        val nameEt = field("").apply { hint = "C-vitamin" }
        val err = label("", TypeScale.CAPTION, color = 0xFFD64545.toInt()).apply { visibility = View.GONE }
        val addBtn = button("Hozzáadás", true) {
            val minute = parseHm(timeEt.text.toString())
            val name = nameEt.text.toString().trim()
            val msg = when {
                minute == null -> "Az időpont ÓÓ:PP formában legyen, például 08:00."
                name.isEmpty() -> "Írd be, mit kell bevenni."
                else -> null
            }
            if (msg != null) {
                err.text = msg
                err.visibility = View.VISIBLE
            } else {
                err.visibility = View.GONE
                Meds.add(act, minute!!, name)
                nameEt.setText("")
                rebuild()
            }
        }
        val form = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(14))
            addView(label("Időpont", TypeScale.CAPTION, color = pal.sub))
            addView(timeEt)
            addView(label("Mit kell bevenni?", TypeScale.CAPTION, color = pal.sub), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10))
            addView(nameEt)
            addView(err)
            addView(addBtn)
        }

        undoText = label("", TypeScale.BODY, color = pal.bg)
        undoBar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rect(pal.text, 14)
            setPadding(dp(16), 0, dp(12), 0)
            visibility = View.GONE
            addView(undoText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label("Visszavonás", TypeScale.BODY, bold = true, color = if (pal.dark) pal.onSoft else pal.soft).apply {
                minHeight = dp(48)
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener {
                    removed?.let { Meds.add(act, it.minute, it.name) }
                    handler.removeCallbacks(hideUndo)
                    hideUndo.run()
                    rebuild()
                }
            })
        }

        val content = column().apply {
            addView(screenTitle("Gyógyszer"))
            addView(label("Ha nem jelzed, hogy bevetted, 10 percenként újra szólok.", TypeScale.CAPTION, color = pal.sub).apply {
                setPadding(dp(4), 0, 0, dp(6))
            })
            addView(listBox)
            addView(accordion("Új emlékeztető", "időpont és név", form, open = Meds.list(act).isEmpty()))
            addView(undoBar, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), top = 12))
        }
        setContentView(Nav.screen(this, Nav.MEDS, content))
        rebuild()
    }

    private fun rebuild() {
        listBox.removeAllViews()
        val meds = Meds.list(this).sortedBy { it.minute }
        if (meds.isEmpty()) {
            listBox.addView(card().apply {
                addView(label("Még nincs emlékeztető", TypeScale.BODY, bold = true))
                addView(label("Add meg alább, mit és mikor kell bevenni, és a megadott időben szólok.", TypeScale.CAPTION, color = pal.sub))
            })
            return
        }
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val rows = meds.map { m ->
            val taken = Meds.isTaken(this, m.id)
            // állapot szöveggel és színnel is
            val status = when {
                taken -> "Bevéve"
                m.minute <= now -> "Esedékes"
                else -> "Ma ${fmtHm(m.minute)}-kor"
            }
            val statusColor = when {
                taken -> 0xFF2E9E5B.toInt()
                m.minute <= now -> 0xFFC77700.toInt()
                else -> pal.sub
            }
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPadding(dp(16), dp(8), dp(4), dp(8))
                addView(label(fmtHm(m.minute), TypeScale.BODY, bold = true).tabular(),
                    LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(LinearLayout(this@MedsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(m.name, TypeScale.BODY))
                    addView(label(status, TypeScale.CAPTION, bold = true, color = statusColor))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ImageView(this@MedsActivity).apply {
                    setImageDrawable(tinted(R.drawable.ic_close, pal.sub, 20))
                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = "${m.name} törlése"
                    isClickable = true
                    background = rippleOf(rect(android.graphics.Color.TRANSPARENT, 24))
                    setOnClickListener { remove(m) }
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
            }
        }
        listBox.addView(group(*rows.toTypedArray()).apply {
            layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 6)
        })
    }

    // Törlés "Biztos?" ablak helyett visszavonással
    private fun remove(m: Med) {
        Meds.remove(this, m.id)
        removed = m
        undoText.text = "Törölve: ${m.name}"
        undoBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideUndo)
        handler.postDelayed(hideUndo, 5000L)
        rebuild()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
