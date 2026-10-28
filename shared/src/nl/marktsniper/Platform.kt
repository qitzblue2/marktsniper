package nl.marktsniper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** Wat Android en de laptop-app elk op hun eigen manier doen. */
interface Platform {
    fun openUrl(url: String)
    fun notifyDeal(w: Watch, h: Hit)
    /** Achtergrond zoeken aan/uit (Android: dienst, laptop: zoeklus in de app). */
    fun setBackground(on: Boolean)
    /** Direct één snipe checken, bv. net na opslaan. */
    fun checkSoon(w: Watch)

    @Composable
    fun HitImage(url: String?, modifier: Modifier)

    /** Extra kaartjes op het instellingenscherm. */
    @Composable
    fun ExtraSettings() {}
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("geen Platform") }

object Sniper {
    suspend fun checkAll(notify: (Watch, Hit) -> Unit) {
        for (w in Store.watches.value.filter { it.active }) checkOne(w, notify)
    }

    suspend fun checkOne(w: Watch, notify: (Watch, Hit) -> Unit) {
        val (found, err) = Scrapers.search(w)
        Store.addHits(w, found, err).take(5).forEach { notify(w, it) }
        delay(1500)
    }
}
