package com.tpj.water

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast

// Vitamin- és gyógyszer-emlékeztetők: időpont és név megadása, lista, törlés
class MedsActivity : Activity() {
    private lateinit var listBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this

        val timeEt = field("08:00")
        val nameEt = field("")
        listBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }

        fun rebuild() {
            listBox.removeAllViews()
            val meds = Meds.list(act)
            if (meds.isEmpty()) {
                listBox.addView(label("Még nincs beállított emlékeztető.", 14f, color = Theme.sub(act)))
            }
            meds.forEach { m ->
                val row = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                row.addView(
                    label(fmtHm(m.minute), 15f, bold = true),
                    LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT)
                )
                row.addView(
                    label(m.name, 15f),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                row.addView(button("Törlés", false) {
                    Meds.remove(act, m.id)
                    rebuild()
                })
                listBox.addView(row)
            }
        }

        val addBtn = button("Hozzáadás", true) {
            val minute = parseHm(timeEt.text.toString())
            val name = nameEt.text.toString().trim()
            when {
                minute == null -> Toast.makeText(act, "Az időpont ÓÓ:PP formátumban legyen.", Toast.LENGTH_LONG).show()
                name.isEmpty() -> Toast.makeText(act, "Írd be, mit kell bevenni.", Toast.LENGTH_LONG).show()
                else -> {
                    Meds.add(act, minute, name)
                    nameEt.setText("")
                    rebuild()
                }
            }
        }

        val addCard = card().apply {
            addView(label("Új emlékeztető", 17f, bold = true))
            addView(label("Időpont (ÓÓ:PP)", 13f))
            addView(timeEt)
            addView(label("Mit kell bevenni?", 13f))
            addView(nameEt)
            addView(addBtn)
        }

        val listCard = card().apply {
            addView(label("Ütemezett emlékeztetők", 17f, bold = true))
            addView(listBox)
        }

        val content = column().apply {
            addView(label("Vitaminok és gyógyszerek", 26f, bold = true))
            addView(
                label(
                    "Ha nem jelzed be, hogy bevetted, az ismétlések addig jönnek, amíg meg nem nyomod a „Bevettem” gombot.",
                    13f,
                    color = Theme.sub(act)
                )
            )
            addView(addCard)
            addView(listCard)
        }
        setContentView(screen(content))
        rebuild()
    }
}
