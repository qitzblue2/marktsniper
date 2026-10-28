package nl.marktsniper

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.marktsniper.ui.App
import nl.marktsniper.ui.SniperTheme
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

class DesktopPlatform(private val tray: TrayState) : Platform {
    override fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    override fun notifyDeal(w: Watch, h: Hit) = tray.sendNotification(
        Notification("🎯 €${"%.0f".format(h.price)} · ${w.name}", "${h.title} · ${h.platform}", Notification.Type.Info))

    // De zoeklus kijkt zelf naar Store.running
    override fun setBackground(on: Boolean) {}

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun checkSoon(w: Watch) {
        GlobalScope.launch(Dispatchers.IO) { Sniper.checkOne(w, ::notifyDeal) }
    }

    @Composable
    override fun HitImage(url: String?, modifier: Modifier) {
        if (url == null) return
        val bmp by produceState(imageCache[url], url) {
            if (value == null) value = withContext(Dispatchers.IO) {
                runCatching {
                    org.jetbrains.skia.Image.makeFromEncoded(URL(url).readBytes()).toComposeImageBitmap()
                        .also { imageCache[url] = it }
                }.getOrNull()
            }
        }
        bmp?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) }
    }
}

fun main() {
    Store.init(File(System.getProperty("user.home"), ".marktsniper"))
    application {
        val tray = rememberTrayState()
        val platform = remember { DesktopPlatform(tray) }
        var visible by remember { mutableStateOf(true) }
        val running by Store.running.collectAsState()
        val icon = painterResource("icon.png")

        LaunchedEffect(running) {
            while (running) {
                withContext(Dispatchers.IO) { Sniper.checkAll(platform::notifyDeal) }
                delay(Store.intervalMinutes.value * 60_000L)
            }
        }

        if (isTraySupported) Tray(icon, tray, tooltip = "MarktSniper", onAction = { visible = true }, menu = {
            Item("Openen", onClick = { visible = true })
            Item("Afsluiten", onClick = ::exitApplication)
        })

        // Sluiten = naar het systeemvak, zodat er doorgezocht wordt
        Window(onCloseRequest = { if (running && isTraySupported) visible = false else exitApplication() }, visible = visible,
            title = "MarktSniper", icon = icon, state = rememberWindowState(width = 1100.dp, height = 780.dp)) {
            CompositionLocalProvider(LocalPlatform provides platform) { SniperTheme { App() } }
        }
    }
}
