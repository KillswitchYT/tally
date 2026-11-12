package com.killswitch.tally.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.tally.BuildService
import com.killswitch.tally.BuildState
import com.killswitch.tally.IconPrep
import com.killswitch.tally.Installed
import com.killswitch.tally.Job
import com.killswitch.tally.Rules
import com.killswitch.tally.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun FieldNote(text: String?, bad: Boolean) {
    if (text != null) BasicText(text, Modifier.padding(start = 2.dp, top = 6.dp), style = ts(13f, if (bad) Aura.Rose else Aura.Muted))
}

/** The form: name, package id, icon. Build is enabled only when every rule passes and a token is saved. */
@Composable
fun BuildTab(pad: PaddingValues, seen: MutableSet<Any>, rev: Int, onGoSetup: () -> Unit, onStarted: () -> Unit, onNotice: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var pkg by rememberSaveable { mutableStateOf("") }
    var pkgEdited by rememberSaveable { mutableStateOf(false) }
    var fit by rememberSaveable { mutableStateOf("fill") }
    var iconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val hasToken = remember(rev) { Settings.hasToken(ctx) }

    LaunchedEffect(iconUri) {
        val u = iconUri
        preview = if (u == null) null else withContext(Dispatchers.IO) { IconPrep.load(ctx, Uri.parse(u), 256) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) iconUri = uri.toString() }

    val nameErr = if (name.isNotEmpty()) Rules.nameError(name) else null
    val pkgBase = if (pkg.isNotEmpty()) Rules.packageError(pkg) else null
    val installed = remember(pkg, rev) { if (pkg.isNotEmpty() && Rules.packageError(pkg) == null) Installed.status(ctx, pkg.trim()) else Installed.NOT }
    val pkgErr = pkgBase ?: if (installed == Installed.OTHER) "This phone already has a different app with that id, so the new one could not install. Pick another id." else null
    val pkgNote = if (installed == Installed.OURS) "You already built this id. The new build installs over it as an update." else null
    val canBuild = Rules.nameError(name) == null && Rules.packageError(pkg) == null && installed != Installed.OTHER && hasToken && !busy

    fun startBuild() {
        busy = true
        val id = Rules.newBuildId()
        val n = name.trim()
        val p = pkg.trim()
        val f = fit
        val u = iconUri
        scope.launch {
            val saved = if (u == null) true else withContext(Dispatchers.IO) { IconPrep.saveForBuild(ctx, Uri.parse(u), id) }
            if (!saved) {
                busy = false
                onNotice("Can't use that image", "Tally could not read it. Pick a PNG, JPG or WebP image.")
                return@launch
            }
            BuildState.add(ctx, Job(id, n, p, f, System.currentTimeMillis()))
            BuildService.kick(ctx)
            busy = false
            name = ""; pkg = ""; pkgEdited = false; iconUri = null; fit = "fill"
            onStarted()
        }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _: Boolean -> startBuild() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageTitle("New app", "Pick a name, a package id and an icon. Tally builds the real app for you.", Modifier.staggerIn(0, seen.add("b-title")))

        Column(Modifier.fillMaxWidth().staggerIn(1, seen.add("b-name"))) {
            SectionLabel("App name")
            LineField(name, { name = it; if (!pkgEdited) pkg = if (it.isBlank()) "" else Rules.suggestPackage(it) }, "My app")
            FieldNote(nameErr, true)
        }

        Column(Modifier.fillMaxWidth().staggerIn(2, seen.add("b-pkg"))) {
            SectionLabel("Package id")
            LineField(pkg, { pkg = it.trim(); pkgEdited = true }, "com.me.myapp")
            FieldNote(pkgErr ?: pkgNote, pkgErr != null)
        }

        Row(
            Modifier.fillMaxWidth().staggerIn(3, seen.add("b-icon")).pressable { pick.launch("image/*") }.topHairline().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Aura.overlay(.10f)), contentAlignment = Alignment.Center) {
                val b = preview
                if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Ic.Image, Aura.Muted, Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText("Icon", style = ts(16f, weight = FontWeight.Bold))
                BasicText(if (iconUri == null) "Tap to choose an image (optional)" else "Tap to change the image", style = ts(13f, Aura.Muted))
            }
            Icon(Ic.Chevron, Aura.Muted, Modifier.size(20.dp))
        }

        if (iconUri != null) {
            Column(Modifier.fillMaxWidth().staggerIn(4, seen.add("b-fit")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Icon style")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipButton("Fill", fit == "fill") { fit = "fill" }
                    ChipButton("Fit", fit == "fit") { fit = "fit" }
                }
                BasicText(
                    if (fit == "fill") "The image fills the whole icon. Your phone's icon shape may trim the edges." else "The whole image shows, centred on a plain colour.",
                    Modifier.padding(start = 4.dp), style = ts(13f, Aura.Muted),
                )
            }
        }

        if (!hasToken) {
            Row(Modifier.fillMaxWidth().topHairline().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Key, Aura.Rose, Modifier.size(22.dp))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText("Add your GitHub token first", style = ts(16f, weight = FontWeight.Bold))
                    BasicText("Tally builds on GitHub and needs your token to start it.", style = ts(13f, Aura.Muted))
                }
                TextAction("Open Setup", onGoSetup, Aura.AccentText)
            }
        }

        BigButton(
            if (busy) "Starting" else "Build app",
            {
                if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else startBuild()
            },
            Modifier.fillMaxWidth(), accent = true, enabled = canBuild,
        )
        BasicText("It takes about 5 minutes. You can leave the app while it builds, a notification shows the progress.", Modifier.padding(start = 4.dp), style = ts(13f, Aura.Muted))
    }
}
