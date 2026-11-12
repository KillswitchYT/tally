package com.killswitch.tally.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.killswitch.tally.Settings
import com.killswitch.tally.glass.LiquidBottomTab
import com.killswitch.tally.glass.LiquidBottomTabs
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private class TabInfo(val icon: Ic, val label: String)

private val TABS = listOf(TabInfo(Ic.Build, "Build"), TabInfo(Ic.Builds, "Builds"), TabInfo(Ic.Setup, "Setup"))

/**
 * The shell: the scene the glass refracts, one page at a time, and the glass tab bar. Each tab keeps what you typed while another tab
 * is open (rememberSaveableStateHolder). The reference's slide-between-tabs animation is not in yet, the pages fade in instead.
 */
@Composable
fun AuraApp(reload: Int, startTab: Int) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity

    val sysDark = isSystemInDarkTheme()
    LaunchedEffect(sysDark) { Aura.apply(Settings.theme(ctx), sysDark) }
    val dark = Aura.isDark
    LaunchedEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        activity.window.decorView.setBackgroundColor(Aura.Ink.toArgb())
    }

    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    LaunchedEffect(reload) { if (reload > 0) tab = startTab }
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rev by remember { mutableIntStateOf(0) }   // goes up each time the app comes back to the front, so pages re-read what they show
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) rev++ }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }
    val seenFor = remember { Array(TABS.size) { mutableSetOf<Any>() } }
    val holder = rememberSaveableStateHolder()
    val backdrop = rememberLayerBackdrop()
    val scene = rememberLayerBackdrop()

    fun switchTo(i: Int) {
        seenFor[i].clear()
        tab = i
    }

    val bars = WindowInsets.systemBars.asPaddingValues()
    val pad = PaddingValues(16.dp, bars.calculateTopPadding() + 12.dp, 16.dp, bars.calculateBottomPadding() + 120.dp)
    val notice: (String, String) -> Unit = { t, m -> dialog = t to m }

    Box(Modifier.fillMaxSize()) {
        // Everything inside this box is what the glass refracts.
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            Box(Modifier.layerBackdrop(scene).fillMaxSize()) { Canvas(Modifier.fillMaxSize()) { drawAuraScene() } }
            CompositionLocalProvider(LocalScene provides scene) {
                holder.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> BuildTab(pad, seenFor[0], rev + reload * 1000, onGoSetup = { switchTo(2) }, onStarted = { switchTo(1) }, onNotice = notice)
                        1 -> BuildsTab(pad, seenFor[1], onNew = { switchTo(0) }, onNotice = notice)
                        else -> SetupTab(pad, seenFor[2], rev + reload * 1000, onNotice = notice)
                    }
                }
            }
        }

        LiquidBottomTabs(
            selectedTabIndex = { tab }, onTabSelected = { switchTo(it) }, backdrop = backdrop, tabsCount = TABS.size,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TABS.forEachIndexed { i, t ->
                LiquidBottomTab({ switchTo(i) }) {
                    val on by selectAnim(tab == i)
                    val hop by animateFloatAsState(if (tab == i) 1f else 0f, spring(dampingRatio = .45f, stiffness = 380f), label = "tabHop")
                    Icon(t.icon, lerp(Aura.Muted, Aura.Text, on), Modifier.size(24.dp).graphicsLayer {
                        scaleX = 1f + .14f * hop; scaleY = scaleX; translationY = -3.dp.toPx() * hop
                    })
                    BasicText(t.label, style = ts(11f, lerp(Aura.Muted, Aura.Text, on), FontWeight.Bold))
                }
            }
        }

        dialog?.let { (t, m) -> NoticeDialog(backdrop, t, m) { dialog = null } }
    }
}
