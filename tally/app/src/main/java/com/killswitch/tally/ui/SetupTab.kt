package com.killswitch.tally.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.killswitch.tally.Gh
import com.killswitch.tally.GhError
import com.killswitch.tally.Rules
import com.killswitch.tally.Settings
import com.killswitch.tally.glass.LiquidPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val THEMES = listOf("Match phone", "Dark", "Light")

/** One grouped block of library glass behind a few rows, rows split by hairlines. */
@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    LiquidPanel(glassBackdrop(), Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) { content() }
}

@Composable
private fun Section(index: Int, seen: MutableSet<Any>, title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().staggerIn(index, seen.add("setup-sec-$index")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(start = 4.dp, top = 6.dp)) {
            BasicText(title, style = ts(19f, weight = FontWeight.ExtraBold))
            BasicText(sub, style = ts(13f, Aura.Muted))
        }
        Block(content)
    }
}

@Composable
private fun NavRow(icon: Ic, title: String, value: String?, valueColor: Color, first: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, Aura.Text, Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        BasicText(title, Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
        if (value != null) {
            BasicText(value, style = ts(14f, valueColor))
            Spacer(Modifier.size(6.dp))
        }
        Icon(Ic.Chevron, Aura.Muted, Modifier.size(20.dp))
    }
}

@Composable
private fun ValueRow(label: String, value: String, first: Boolean) {
    Row(Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
        BasicText(value, style = ts(15f, Aura.Muted, FontWeight.Bold))
    }
}

@Composable
private fun Choice(title: String, selected: Boolean, first: Boolean, onClick: () -> Unit) {
    val sel by selectAnim(selected)
    Row(
        Modifier.fillMaxWidth().pressable(onClick).then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(title, Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
        Icon(Ic.Check, Aura.AccentText, Modifier.size(22.dp).graphicsLayer { alpha = sel; scaleX = .6f + .4f * sel; scaleY = .6f + .4f * sel })
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    com.killswitch.tally.glass.LiquidButton(onClick, glassBackdrop(), Modifier.size(40.dp), height = 40.dp, horizontalPadding = 0.dp) {
        Icon(Ic.Chevron, Aura.Text, Modifier.size(18.dp).graphicsLayer { rotationZ = 180f })
    }
}

@Composable
private fun ScreenHeader(title: String, sub: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackButton(onBack)
        PageTitle(title, sub, Modifier.padding(start = 2.dp))
    }
}

/** A hub of rows, each opening a screen of its own with a back button. */
@Composable
fun SetupTab(pad: PaddingValues, seen: MutableSet<Any>, rev: Int, onNotice: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bump by remember { mutableIntStateOf(0) }
    val hasToken = remember(rev, bump) { Settings.hasToken(ctx) }
    val repo = remember(rev, bump) { Settings.repo(ctx) }
    var themeMode by remember(rev, bump) { mutableIntStateOf(Settings.theme(ctx)) }
    val sysDark = isSystemInDarkTheme()

    val scroll = rememberScrollState()
    val prog = remember { Animatable(1f) }
    var dir by remember { mutableIntStateOf(1) }
    var leaving by remember { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf("") }   // "" is the hub
    fun go(to: String, forward: Boolean) {
        if (to == shown || prog.isRunning) return
        scope.launch {
            dir = if (forward) 1 else -1
            leaving = true
            prog.animateTo(0f, tween(130))
            shown = to
            scroll.scrollTo(0)
            leaving = false
            prog.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        }
    }
    BackHandler(enabled = shown.isNotEmpty()) { go("", false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(pad)
            .graphicsLayer { alpha = prog.value; translationX = (if (leaving) -dir else dir) * (1f - prog.value) * 28.dp.toPx() },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (shown) {
            "" -> {
                BasicText("Setup", Modifier.padding(start = 4.dp), style = ts(26f, weight = FontWeight.ExtraBold))
                Section(0, seen, "GitHub", "Where your apps get built") {
                    NavRow(Ic.Key, "Token", if (hasToken) "Saved" else "Not set", if (hasToken) Aura.Mint else Aura.Rose, true) { go("token", true) }
                    NavRow(Ic.Build, "Repository", repo, Aura.Muted, false) { go("repo", true) }
                    NavRow(Ic.Shield, "Check connection", null, Aura.Muted, false) { go("check", true) }
                }
                Section(1, seen, "App", "Look and privacy") {
                    NavRow(Ic.Sun, "Appearance", THEMES[themeMode.coerceIn(0, 2)], Aura.Muted, true) { go("display", true) }
                    NavRow(Ic.Info, "About", null, Aura.Muted, false) { go("about", true) }
                }
            }

            "token" -> {
                ScreenHeader("GitHub token", "Kept encrypted on this phone") { go("", false) }
                var text by remember { mutableStateOf("") }
                var msg by remember { mutableStateOf<String?>(null) }
                BasicText(
                    "Tally builds your apps on GitHub, in your own repository ($repo). The token lets it start a build and fetch the result. " +
                        "Make one at GitHub, Settings, Developer settings, Fine-grained tokens: only your repository, with Actions and Contents set to Read and write.",
                    Modifier.padding(start = 4.dp), style = ts(14f, Aura.Muted),
                )
                LineField(text, { text = it; msg = null }, if (hasToken) "Paste a new token to replace the saved one" else "github_pat_...", visual = PasswordVisualTransformation(), keyboardType = KeyboardType.Password)
                msg?.let { BasicText(it, Modifier.padding(start = 4.dp), style = ts(13f, Aura.Rose)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftButton("Paste", {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip
                        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx)?.toString()?.trim() else null
                        if (t.isNullOrEmpty()) msg = "Nothing to paste. Copy the token first." else { text = t; msg = null }
                    }, Modifier.weight(1f))
                    BigButton("Save token", {
                        val err = Rules.tokenError(text)
                        if (err != null) msg = err
                        else if (Settings.setToken(ctx, text)) {
                            text = ""
                            bump++
                            onNotice("Token saved", "Tally can now build your apps. Run Check connection to make sure GitHub accepts it.")
                        } else msg = "Could not store the token on this phone."
                    }, Modifier.weight(2f), accent = true)
                }
                if (hasToken) TextAction("Remove the saved token", { Settings.clearToken(ctx); bump++ }, Aura.Rose)
            }

            "repo" -> {
                ScreenHeader("Repository", "Where the builds run") { go("", false) }
                var text by remember { mutableStateOf(repo) }
                var msg by remember { mutableStateOf<String?>(null) }
                BasicText(
                    "The repository that holds Tally's build workflow. It must be the one your token covers. The default is the one Tally was set up with.",
                    Modifier.padding(start = 4.dp), style = ts(14f, Aura.Muted),
                )
                LineField(text, { text = it; msg = null }, Settings.DEFAULT_REPO)
                msg?.let { BasicText(it, Modifier.padding(start = 4.dp), style = ts(13f, Aura.Rose)) }
                BigButton("Save repository", {
                    val err = Rules.repoError(text)
                    if (err != null) msg = err else { Settings.setRepo(ctx, text); bump++; onNotice("Saved", "Builds will run in " + text.trim() + ".") }
                }, Modifier.fillMaxWidth(), accent = true)
                TextAction("Use the default", { text = Settings.DEFAULT_REPO; Settings.setRepo(ctx, Settings.DEFAULT_REPO); bump++ })
            }

            "check" -> {
                ScreenHeader("Check connection", "Makes sure GitHub accepts your token") { go("", false) }
                var rows by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
                var err by remember { mutableStateOf<String?>(null) }
                var running by remember { mutableStateOf(false) }
                BigButton(if (running) "Checking" else "Run check", {
                    if (!running) scope.launch {
                        running = true; err = null; rows = null
                        val t = Settings.token(ctx)
                        if (t == null) err = "Add your token first."
                        else try {
                            rows = withContext(Dispatchers.IO) { Gh.check(t, repo) }
                        } catch (e: GhError) {
                            err = e.message
                        } catch (e: Exception) {
                            err = "Something went wrong while checking."
                        }
                        running = false
                    }
                }, Modifier.fillMaxWidth(), accent = true, enabled = !running)
                err?.let { BasicText(it, Modifier.padding(start = 4.dp), style = ts(14f, Aura.Rose, FontWeight.Bold)) }
                rows?.let { r -> Block { r.forEachIndexed { i, kv -> ValueRow(kv.first, kv.second, i == 0) } } }
            }

            "display" -> {
                ScreenHeader("Appearance", "Light, dark, or follow the phone") { go("", false) }
                Block {
                    THEMES.forEachIndexed { i, t ->
                        Choice(t, themeMode == i, i == 0) { themeMode = i; Settings.setTheme(ctx, i); Aura.apply(i, sysDark) }
                    }
                }
            }

            else -> {
                ScreenHeader("About", null) { go("", false) }
                val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
                Block {
                    ValueRow("Version", version, true)
                    ValueRow("Internet", "GitHub only", false)
                    ValueRow("Analytics and ads", "None", false)
                    ValueRow("Builds run in", repo, false)
                }
                BasicText(
                    "Tally sends your app name, package id and icon to your own GitHub repository, builds the app there, and downloads the result. Nothing goes anywhere else.",
                    Modifier.padding(start = 4.dp), style = ts(14f, Aura.Muted),
                )
            }
        }
    }
}
