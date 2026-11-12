package com.killswitch.tally

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toArgb
import com.killswitch.tally.ui.Aura
import com.killswitch.tally.ui.AuraApp

class MainActivity : ComponentActivity() {
    private val reload = mutableIntStateOf(0)
    private val startTab = mutableIntStateOf(0)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // The saved theme is applied here, in every activity, before anything draws (the theme is one shared object for the whole process).
        Aura.apply(Settings.theme(this), (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        val bars = if (Aura.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        window.decorView.setBackgroundColor(Aura.Ink.toArgb())
        startTab.intValue = intent?.getIntExtra("tab", 0) ?: 0
        BuildState.load(this)
        BuildService.resume(this)
        setContent { AuraApp(reload.intValue, startTab.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startTab.intValue = intent.getIntExtra("tab", startTab.intValue)
        reload.intValue++
    }
}
