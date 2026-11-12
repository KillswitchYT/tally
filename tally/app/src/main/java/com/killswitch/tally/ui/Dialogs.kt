package com.killswitch.tally.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.tally.glass.LiquidPanel
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

/**
 * A message over the whole app, in library glass (the panel is the library's own dialog recipe). It sits at the top level so the glass
 * refracts the screen behind it. Tapping outside it, the button, or the back gesture closes it.
 */
@Composable
fun NoticeDialog(backdrop: Backdrop, title: String, message: String, onClose: () -> Unit) {
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    fun leave() { scope.launch { enter.animateTo(0f, tween(160, easing = FastOutSlowInEasing)); onClose() } }
    BackHandler { leave() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(Color.Black.copy(alpha = .45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { leave() },
        )
        LiquidPanel(
            backdrop,
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp)
                .graphicsLayer { alpha = enter.value; scaleX = .92f + .08f * enter.value; scaleY = .92f + .08f * enter.value }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            corner = 36.dp,
            contentPadding = PaddingValues(24.dp),
        ) {
            BasicText(title, style = ts(20f, weight = FontWeight.ExtraBold))
            Spacer(Modifier.size(8.dp))
            BasicText(message, style = ts(15f, Aura.Muted))
            Spacer(Modifier.size(20.dp))
            BigButton("OK", { leave() }, Modifier.fillMaxWidth(), accent = true)
        }
    }
}
