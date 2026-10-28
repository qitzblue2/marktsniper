package nl.marktsniper

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import nl.marktsniper.ui.App
import nl.marktsniper.ui.SniperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(filesDir)
        Notifier.channels(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        if (Store.running.value) SnipeService.start(this)
        val platform = AndroidPlatform(this)
        setContent { CompositionLocalProvider(LocalPlatform provides platform) { SniperTheme { App() } } }
    }
}
