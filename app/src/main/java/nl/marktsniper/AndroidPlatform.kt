package nl.marktsniper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.marktsniper.ui.Mute
import nl.marktsniper.ui.SettingsCard

class AndroidPlatform(private val ctx: Context) : Platform {
    override fun openUrl(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun notifyDeal(w: Watch, h: Hit) = Notifier.deal(ctx, w, h)

    override fun setBackground(on: Boolean) {
        if (on) SnipeService.start(ctx) else SnipeService.stop(ctx)
    }

    override fun checkSoon(w: Watch) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { Sniper.checkOne(w) { ww, h -> Notifier.deal(app, ww, h) } }
        if (Store.running.value) SnipeService.start(ctx)
    }

    @Composable
    override fun HitImage(url: String?, modifier: Modifier) =
        AsyncImage(url, null, modifier, contentScale = ContentScale.Crop)

    @Composable
    override fun ExtraSettings() = SettingsCard {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val off = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        Text("Batterijbesparing uitzetten", fontWeight = FontWeight.Bold)
        Text("Aanbevolen: anders kan Android de app stoppen en mis je meldingen.", color = Mute, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedButton({
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
        }, enabled = !off) { Text(if (off) "✓ Staat al uit" else "Uitzetten") }
    }
}
