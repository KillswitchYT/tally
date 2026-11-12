package com.killswitch.tally.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class Ic { Alarm, World, Timer, Stopwatch, Setup, Plus, Chevron, Trash, Play, Alert, Search, Close, Pause, Check, Copy, Backspace, Shield, Volume, Sleep, Moon, Sun, Backup, Info, Build, Builds, Image, Key, Download, Refresh }

/** Tiny stroke icons drawn on a canvas, so the app needs no icon library. */
@Composable
fun Icon(kind: Ic, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s * .09f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val c = center
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), sw, StrokeCap.Round)
        when (kind) {
            Ic.Alarm -> {
                drawCircle(color, s * .34f, Offset(c.x, s * .56f), style = st)
                line(.5f, .56f, .5f, .40f); line(.5f, .56f, .62f, .56f)
                line(.14f, .22f, .28f, .12f); line(.86f, .22f, .72f, .12f)
            }
            Ic.World -> {
                drawCircle(color, s * .38f, c, style = st)
                drawOval(color, Offset(s * .31f, s * .12f), Size(s * .38f, s * .76f), style = st)
                line(.12f, .5f, .88f, .5f)
            }
            Ic.Timer -> {
                val p = Path().apply {
                    moveTo(s * .28f, s * .14f); lineTo(s * .72f, s * .14f); lineTo(s * .5f, s * .5f); close()
                    moveTo(s * .5f, s * .5f); lineTo(s * .28f, s * .86f); lineTo(s * .72f, s * .86f); close()
                }
                drawPath(p, color, style = st)
            }
            Ic.Stopwatch -> {
                drawCircle(color, s * .34f, Offset(c.x, s * .58f), style = st)
                line(.4f, .1f, .6f, .1f)
                line(.5f, .58f, .64f, .44f)
            }
            Ic.Setup -> {
                for (i in 0..2) line(.14f, .25f + .25f * i, .86f, .25f + .25f * i)
                val xs = listOf(.34f, .66f, .46f)
                for (i in 0..2) drawCircle(color, s * .075f, Offset(xs[i] * s, (.25f + .25f * i) * s))
            }
            Ic.Plus -> { line(.5f, .2f, .5f, .8f); line(.2f, .5f, .8f, .5f) }
            Ic.Chevron -> { line(.38f, .2f, .66f, .5f); line(.66f, .5f, .38f, .8f) }
            Ic.Trash -> {
                drawRoundRect(color, Offset(s * .28f, s * .34f), Size(s * .44f, s * .50f), CornerRadius(s * .06f), style = st)
                line(.18f, .26f, .82f, .26f); line(.42f, .15f, .58f, .15f)
            }
            Ic.Play -> {
                val p = Path().apply { moveTo(s * .34f, s * .2f); lineTo(s * .76f, s * .5f); lineTo(s * .34f, s * .8f); close() }
                drawPath(p, color, style = st)
            }
            Ic.Alert -> {
                val p = Path().apply { moveTo(s * .5f, s * .14f); lineTo(s * .9f, s * .84f); lineTo(s * .1f, s * .84f); close() }
                drawPath(p, color, style = st)
                line(.5f, .42f, .5f, .62f); drawCircle(color, s * .04f, Offset(s * .5f, s * .74f))
            }
            Ic.Close -> { line(.25f, .25f, .75f, .75f); line(.75f, .25f, .25f, .75f) }
            Ic.Backspace -> {
                line(.14f, .5f, .34f, .27f); line(.34f, .27f, .86f, .27f); line(.86f, .27f, .86f, .73f)
                line(.86f, .73f, .34f, .73f); line(.34f, .73f, .14f, .5f)
                line(.48f, .41f, .68f, .59f); line(.68f, .41f, .48f, .59f)
            }
            Ic.Pause -> { line(.36f, .2f, .36f, .8f); line(.64f, .2f, .64f, .8f) }
            Ic.Check -> { line(.2f, .52f, .42f, .74f); line(.42f, .74f, .8f, .28f) }
            Ic.Copy -> {
                drawRoundRect(color, Offset(s * .18f, s * .14f), Size(s * .46f, s * .52f), CornerRadius(s * .07f), style = st)
                drawRoundRect(color, Offset(s * .36f, s * .34f), Size(s * .46f, s * .52f), CornerRadius(s * .07f), style = st)
            }
            Ic.Search -> { drawCircle(color, s * .26f, Offset(s * .44f, s * .44f), style = st); line(.64f, .64f, .86f, .86f) }
            Ic.Shield -> {
                val p = Path().apply {
                    moveTo(s * .5f, s * .12f); lineTo(s * .84f, s * .26f); lineTo(s * .84f, s * .52f)
                    lineTo(s * .5f, s * .88f); lineTo(s * .16f, s * .52f); lineTo(s * .16f, s * .26f); close()
                }
                drawPath(p, color, style = st)
                line(.35f, .5f, .46f, .61f); line(.46f, .61f, .66f, .39f)
            }
            Ic.Volume -> {
                val p = Path().apply {
                    moveTo(s * .14f, s * .4f); lineTo(s * .32f, s * .4f); lineTo(s * .54f, s * .2f)
                    lineTo(s * .54f, s * .8f); lineTo(s * .32f, s * .6f); lineTo(s * .14f, s * .6f); close()
                }
                drawPath(p, color, style = st)
                drawArc(color = color, startAngle = -50f, sweepAngle = 100f, useCenter = false, topLeft = Offset(s * .38f, s * .3f), size = Size(s * .4f, s * .4f), style = st)
                drawArc(color = color, startAngle = -50f, sweepAngle = 100f, useCenter = false, topLeft = Offset(s * .38f, s * .14f), size = Size(s * .72f, s * .72f), style = st)
            }
            Ic.Sleep -> {
                line(.2f, .32f, .5f, .32f); line(.5f, .32f, .2f, .66f); line(.2f, .66f, .5f, .66f)
                line(.6f, .16f, .82f, .16f); line(.82f, .16f, .6f, .4f); line(.6f, .4f, .82f, .4f)
            }
            Ic.Moon -> {
                val p = Path().apply {
                    moveTo(s * .62f, s * .14f)
                    cubicTo(s * .30f, s * .18f, s * .16f, s * .46f, s * .28f, s * .68f)
                    cubicTo(s * .40f, s * .88f, s * .68f, s * .90f, s * .86f, s * .70f)
                    cubicTo(s * .58f, s * .72f, s * .46f, s * .42f, s * .62f, s * .14f)
                    close()
                }
                drawPath(p, color, style = st)
            }
            Ic.Sun -> {
                drawCircle(color, s * .17f, c, style = st)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    val ca = Math.cos(a).toFloat(); val sa = Math.sin(a).toFloat()
                    line(.5f + .28f * ca, .5f + .28f * sa, .5f + .4f * ca, .5f + .4f * sa)
                }
            }
            Ic.Backup -> {
                drawRoundRect(color, Offset(s * .2f, s * .12f), Size(s * .6f, s * .76f), CornerRadius(s * .08f), style = st)
                line(.5f, .32f, .5f, .66f); line(.37f, .54f, .5f, .67f); line(.63f, .54f, .5f, .67f)
            }
            Ic.Info -> {
                drawCircle(color, s * .38f, c, style = st)
                line(.5f, .46f, .5f, .68f); drawCircle(color, s * .035f, Offset(s * .5f, s * .31f))
            }
            Ic.Build -> {
                val p = Path().apply {
                    moveTo(s * .5f, s * .12f); lineTo(s * .84f, s * .31f); lineTo(s * .84f, s * .69f)
                    lineTo(s * .5f, s * .88f); lineTo(s * .16f, s * .69f); lineTo(s * .16f, s * .31f); close()
                }
                drawPath(p, color, style = st)
                line(.16f, .31f, .5f, .5f); line(.84f, .31f, .5f, .5f); line(.5f, .5f, .5f, .88f)
            }
            Ic.Builds -> {
                for (i in 0..2) {
                    val y = .28f + .22f * i
                    drawCircle(color, s * .04f, Offset(s * .2f, s * y))
                    line(.34f, y, .84f, y)
                }
            }
            Ic.Image -> {
                drawRoundRect(color, Offset(s * .14f, s * .2f), Size(s * .72f, s * .6f), CornerRadius(s * .08f), style = st)
                drawCircle(color, s * .06f, Offset(s * .36f, s * .4f))
                line(.2f, .72f, .45f, .5f); line(.45f, .5f, .62f, .66f); line(.62f, .66f, .72f, .58f); line(.72f, .58f, .8f, .7f)
            }
            Ic.Key -> {
                drawCircle(color, s * .17f, Offset(s * .32f, s * .5f), style = st)
                line(.49f, .5f, .88f, .5f); line(.74f, .5f, .74f, .65f); line(.84f, .5f, .84f, .6f)
            }
            Ic.Download -> {
                line(.5f, .14f, .5f, .62f); line(.32f, .44f, .5f, .62f); line(.68f, .44f, .5f, .62f)
                line(.2f, .84f, .8f, .84f); line(.2f, .7f, .2f, .84f); line(.8f, .7f, .8f, .84f)
            }
            Ic.Refresh -> {
                drawArc(color = color, startAngle = -40f, sweepAngle = 280f, useCenter = false, topLeft = Offset(s * .2f, s * .2f), size = Size(s * .6f, s * .6f), style = st)
                line(.74f, .12f, .78f, .32f); line(.78f, .32f, .58f, .3f)
            }
        }
    }
}
