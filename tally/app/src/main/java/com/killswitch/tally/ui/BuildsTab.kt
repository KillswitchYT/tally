package com.killswitch.tally.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.tally.BuildService
import com.killswitch.tally.BuildState
import com.killswitch.tally.Job
import com.killswitch.tally.Rules
import com.killswitch.tally.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every build with where it stands. Failed ones say why in plain words and can be tried again. */
@Composable
fun BuildsTab(pad: PaddingValues, seen: MutableSet<Any>, onNew: () -> Unit, onNotice: (String, String) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { if (!BuildState.loaded) withContext(Dispatchers.IO) { BuildState.load(ctx) } }
    val jobs = BuildState.jobs
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageTitle("Builds", "Your apps, newest first", Modifier.staggerIn(0, seen.add("bs-title")))
        when {
            BuildState.damaged -> FailCard(
                "Can't read your build list", "The saved list is damaged. Apps you already built are still in your Downloads folder.",
                onRetry = { BuildState.load(ctx) }, onReset = { BuildState.reset(ctx) },
            )
            !BuildState.loaded -> { SkeletonCard(); SkeletonCard() }
            jobs.isEmpty() -> EmptyState(
                Ic.Build, "No builds yet", "Apps you build show up here, each with your name, icon and package id.", "Build an app", onNew,
            )
            else -> jobs.forEachIndexed { i, j -> JobRow(j, i, seen, onNotice) }
        }
    }
}

@Composable
private fun JobRow(j: Job, index: Int, seen: MutableSet<Any>, onNotice: (String, String) -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().staggerIn(index, seen.add("job-" + j.id)).topHairline().padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(j.name, style = ts(18f, weight = FontWeight.ExtraBold))
        BasicText(j.pkg, style = ts(13f, Aura.Muted))
        when (j.stage) {
            Stage.Done -> BasicText("Saved in Downloads as " + Rules.fileName(j.name), style = ts(14f, Aura.Mint, FontWeight.Bold))
            Stage.Failed -> BasicText(j.error.ifEmpty { "The build failed" }, style = ts(14f, Aura.Rose, FontWeight.Bold))
            else -> BasicText(j.detail.ifEmpty { "Working" }, style = ts(14f, Aura.AccentText, FontWeight.Bold))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (j.stage == Stage.Done) {
                SoftButton("Install", {
                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(j.saved), "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        ctx.startActivity(i)
                    } catch (e: Exception) {
                        onNotice("Can't open the installer", "Open the Downloads app and tap " + Rules.fileName(j.name) + " to install it.")
                    }
                })
            }
            if (j.stage == Stage.Failed) {
                SoftButton("Try again", {
                    if (BuildState.retry(ctx, j.id) != null) BuildService.kick(ctx)
                })
            }
            TextAction(if (j.stage.working) "Stop watching" else "Remove", { BuildState.remove(ctx, j.id) }, Aura.Muted)
        }
    }
}
