package com.killswitch.tally.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.killswitch.tally.glass.LiquidButton
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

/** Shared building blocks for the tabs, so every screen speaks the same visual language. */

/** A faint line along the top edge. Rows are told apart by this and by spacing, never by a box around each one. */
fun Modifier.topHairline(): Modifier = drawBehind {
    drawLine(Aura.overlay(.07f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

/** 0 to 1 and back, 200 ms, for anything that can be selected. Selection never snaps. */
@Composable
fun selectAnim(on: Boolean): State<Float> = animateFloatAsState(if (on) 1f else 0f, tween(200, easing = FastOutSlowInEasing), label = "select")

@Composable
fun PageTitle(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(title, style = ts(26f, weight = FontWeight.ExtraBold))
        if (sub != null) BasicText(sub, style = ts(14f, Aura.Muted))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    BasicText(text, modifier.padding(start = 4.dp, top = 8.dp), style = ts(13f, Aura.Muted, FontWeight.Bold))

/**
 * The layer glass controls on a page refract: the real scene behind the page, the same way the library's own list demo does it.
 * Null inside sheets, where controls refract a flat sheet colour instead.
 */
val LocalScene = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun glassBackdrop(): Backdrop {
    val flat = rememberCanvasBackdrop { drawRect(Aura.Lift2) }
    return LocalScene.current ?: flat
}

/** The quiet secondary button: the library's glass, untinted. The accent is kept for the one main action on a screen. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Aura.Text, enabled: Boolean = true) {
    LiquidButton(
        if (enabled) onClick else ({}), glassBackdrop(), modifier.graphicsLayer { alpha = if (enabled) 1f else .45f },
        isInteractive = enabled, height = 44.dp,
    ) { BasicText(text, style = ts(15f, color, FontWeight.Bold)) }
}

/** A wide button. [accent] is the main action of the screen and is tinted with the accent, like the New alarm button. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true) {
    LiquidButton(
        if (enabled) onClick else ({}), glassBackdrop(), modifier.graphicsLayer { alpha = if (enabled) 1f else .45f },
        isInteractive = enabled, tint = if (accent) Aura.Accent else Color.Unspecified, height = 56.dp,
    ) { BasicText(text, style = ts(17f, if (accent) Aura.OnAccent else Aura.Text, FontWeight.ExtraBold)) }
}

/** A choice you can switch on. The selected one is a brighter glass, not a different colour. */
@Composable
fun ChipButton(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val sel by selectAnim(selected)
    LiquidButton(onClick, glassBackdrop(), surfaceColor = Aura.overlay(.22f * sel), height = 38.dp, horizontalPadding = 14.dp) {
        BasicText(text, style = ts(14f, Aura.Text, FontWeight.Bold))
    }
}

@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidButton(onClick, glassBackdrop(), modifier.size(36.dp), height = 36.dp, horizontalPadding = 0.dp) {
        Icon(Ic.Close, Aura.Muted, Modifier.size(16.dp))
    }
}

/** A text field that is just a line, not a box. */
@Composable
fun LineField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: () -> Unit = {}, visual: VisualTransformation = VisualTransformation.None, keyboardType: KeyboardType = KeyboardType.Text) {
    Column(modifier) {
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp), singleLine = true, textStyle = ts(16f),
            cursorBrush = SolidColor(Aura.Accent), keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done), visualTransformation = visual,
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner -> if (value.isEmpty()) BasicText(hint, style = ts(16f, Aura.Muted)); inner() },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Aura.overlay(.14f)))
    }
}

/** A quiet action that is only words. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, color: Color = Aura.Muted, modifier: Modifier = Modifier) {
    Box(modifier.pressable(onClick).padding(horizontal = 8.dp, vertical = 10.dp)) { BasicText(text, style = ts(14f, color, FontWeight.Bold)) }
}

/** Empty state: says what this screen is for and offers the next step right there. Left aligned, no box. */
@Composable
fun EmptyState(icon: Ic, title: String, body: String, actionText: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, Aura.Muted, Modifier.size(36.dp))
        BasicText(title, style = ts(22f, weight = FontWeight.ExtraBold))
        BasicText(body, style = ts(15f, Aura.Muted))
        if (actionText != null) { Spacer(Modifier.height(6.dp)); AccentPill(actionText, onAction) }
    }
}

/** Error state: nothing is changed unless the person chooses Reset, and Reset asks twice. */
@Composable
fun FailCard(title: String, body: String, onRetry: () -> Unit, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Ic.Alert, Aura.Rose, Modifier.size(36.dp))
        BasicText(title, style = ts(22f, weight = FontWeight.ExtraBold))
        BasicText(body, style = ts(15f, Aura.Muted))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AccentPill("Try again", onRetry)
            Box(Modifier.pressable { if (confirm) onReset() else confirm = true }.padding(8.dp)) {
                BasicText(if (confirm) "Tap again to erase" else "Reset", style = ts(15f, Aura.Rose, FontWeight.Bold))
            }
        }
    }
}

/** No match: a search or filter found nothing. */
@Composable
fun NoResults(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.Search, Aura.Muted, Modifier.size(24.dp))
        Spacer(Modifier.size(12.dp))
        BasicText(text, style = ts(15f, Aura.Muted, FontWeight.Bold))
    }
}

/** Loading: placeholder shapes that breathe while data is read. */
@Composable
fun SkeletonCard(lines: Int = 2, height: Int = 20) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(.35f, .85f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = height.dp).graphicsLayer { alpha = a },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 130.dp, height = 26.dp).clip(RoundedCornerShape(8.dp)).background(Aura.overlay(.14f)))
        for (i in 1 until lines) Box(Modifier.size(width = 190.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(Aura.overlay(.10f)))
    }
}
