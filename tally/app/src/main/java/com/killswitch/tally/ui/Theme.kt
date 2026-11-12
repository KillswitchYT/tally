package com.killswitch.tally.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killswitch.tally.Palettes
import com.killswitch.tally.R
import com.killswitch.tally.glass.LiquidButton
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay

/** One accent colour, used for the main action and live state only. Everything else is neutral. */
object Aura {
    private var mode by mutableIntStateOf(0)         // the person's choice: 0 follows the phone, 1 dark, 2 light
    private var systemDark by mutableStateOf(true)

    val isDark: Boolean get() = Palettes.useDark(mode, systemDark)
    private val p: Palettes.P get() = if (isDark) Palettes.DARK else Palettes.LIGHT

    /** Every colour below is read from the state above, so changing the theme repaints the whole app by itself. */
    fun apply(mode: Int, systemDark: Boolean) { this.mode = mode; this.systemDark = systemDark }

    val Ink: Color get() = Color(p.ink)
    val Lift: Color get() = Color(p.lift)
    val Lift2: Color get() = Color(p.lift2)
    val Accent: Color get() = Color(p.accent)
    val AccentText: Color get() = Color(p.accentText)
    val OnAccent: Color get() = Color(p.onAccent)
    val Text: Color get() = Color(p.text)
    val Muted: Color get() = Color(p.muted)
    val Mint: Color get() = Color(p.mint)
    val Rose: Color get() = Color(p.rose)
    /** Flat colours the small glass controls refract when they sit inside a card or sheet. */
    val Card: Color get() = Color(p.card)
    val Sheet: Color get() = Color(p.sheet)

    /** A wash of the "on surface" colour: white on dark, black on light. For hairlines, tracks and selections. */
    fun overlay(alpha: Float): Color = if (isDark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha * .8f)
}

val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/** Text style shorthand. Tabular numerals keep the clock digits from jiggling as they tick. */
fun ts(size: Float, color: Color = Aura.Text, weight: FontWeight = FontWeight.Medium, tracking: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, fontWeight = weight, fontFamily = Jakarta,
    letterSpacing = tracking.sp, fontFeatureSettings = "tnum",
)

fun centered(s: TextStyle) = s.copy(textAlign = TextAlign.Center)

/** The scene behind everything, which the glass refracts. One cool graphite hue, only lighter and darker. */
fun DrawScope.drawAuraScene() {
    val w = size.width
    val h = size.height
    val big = maxOf(w, h)
    drawRect(Aura.Ink)
    fun lift(c: Color, x: Float, y: Float, r: Float, a: Float) = drawCircle(
        Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(x, y), radius = r),
        radius = r, center = Offset(x, y),
    )
    lift(Aura.Lift, w * .15f, h * .08f, big * .80f, .95f)
    lift(Aura.Lift2, w * .90f, h * .75f, big * .70f, .90f)
}

@Composable
fun rememberTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}

/** Tap target with a spring press-scale and a haptic tick. No ripple, the scale is the feedback. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    val tick = rememberTick()
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null) { tick(); onClick() }
}

/** Fade and rise in, [index] steps after the first. Pass play = false to skip it (already shown once). 260 ms, 55 ms apart. */
@Composable
fun Modifier.staggerIn(index: Int, play: Boolean = true): Modifier {
    val arrival = LocalArrival.current
    val first = remember { play }                       // true only if this very item was built before it was ever shown
    val p = remember { Animatable(if (play) 0f else 1f) }
    var handled by remember { mutableIntStateOf(-1) }
    LaunchedEffect(arrival) {
        val again = handled != -1                       // an arrival after the first one: greet the person again
        handled = arrival
        if (first || again) { p.snapTo(0f); delay(index * 55L); p.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    }
    return this.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 24.dp.toPx() }
}

/** Counts up each time the tab this content lives on becomes the visible one, so lists can fade in again without being rebuilt. */
val LocalArrival = compositionLocalOf { 0 }

/** The one filled button style in the app. */
@Composable
fun AccentPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidButton(onClick, glassBackdrop(), modifier, tint = Aura.Accent) {
        BasicText(text, style = ts(15f, Aura.OnAccent, FontWeight.ExtraBold))
    }
}
