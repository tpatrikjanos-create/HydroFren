package com.tpj.water

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

// Biztató üzenetek a cica szövegbuborékjában, egymás után nem ismétlődnek
private val ENCOURAGE = listOf(
    "Szuper, így tovább! 💧",
    "Ügyes vagy, miau! 🐾",
    "Minden korty számít!",
    "Büszke vagyok rád! 😸",
    "Ez a legjobb pillanat egy kis vízre!",
    "Egy újabb pohár, egy újabb pont!",
    "Közelebb vagy a céhez, folytasd!",
    "A szervezeted hálás ezért!",
    "Vízcsepp-hatalom aktiválva! 💪",
    "Már csak egy kicsit, és kész!",
    "Imádom, ahogy vigyázol magadra!",
    "Mancs-öt, ez szép munka! 🐾"
)

fun nextEncouragement(c: Context): String {
    val p = Water.prefs(c)
    val last = p.getInt("lastMsg", -1)
    var i = ENCOURAGE.indices.random()
    while (ENCOURAGE.size > 1 && i == last) i = ENCOURAGE.indices.random()
    p.edit().putInt("lastMsg", i).apply()
    return ENCOURAGE[i]
}

// Rövid hangeffekt lejátszása (a lejátszó a végén felszabadul)
fun playSound(c: Context, resId: Int) {
    val mp = MediaPlayer.create(c, resId) ?: return
    mp.setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    )
    mp.setOnCompletionListener { it.release() }
    mp.start()
}

// Játék-hangok: a cicák simogatásra dorombolnak (ismétlődő hang), a többi állat a saját hangját adja.
object PetSounds {
    private var purr: MediaPlayer? = null
    private var last = 0L

    private fun isCat(c: Context) = c.currentSpecies().id.let { it == "cat" || it == "tabby" }

    // egy rövid megszólalás, legfeljebb ilyen sűrűn
    fun voice(c: Context, minGapMs: Long = 600L) {
        val now = System.currentTimeMillis()
        if (now - last < minGapMs) return
        last = now
        playSound(c, c.currentSpecies().sound)
    }

    fun petStart(c: Context) {
        if (isCat(c)) {
            stop()
            purr = MediaPlayer.create(c, R.raw.purr)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                start()
            }
        } else voice(c, 0L)
    }

    // simogatás közben a nem-cica állatok időnként újra megszólalnak
    fun petting(c: Context) {
        if (!isCat(c)) voice(c, 1600L)
    }

    fun stop() {
        purr?.let {
            runCatching { it.stop() }
            it.release()
        }
        purr = null
    }
}
