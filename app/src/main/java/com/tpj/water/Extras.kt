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
