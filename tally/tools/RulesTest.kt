import com.killswitch.tally.Palettes
import com.killswitch.tally.Rules
import java.io.File

var failed = 0
var passed = 0
fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

fun unescape(s: String) = s.replace("\\n", "\n").replace("\\t", "\t")

fun main(args: Array<String>) {
    // 1. Same verdicts as the server-side script (tools/apply_custom.py) on the same inputs.
    val lines = File(args[0]).readLines(Charsets.UTF_8).filter { it.isNotBlank() }
    for (l in lines) {
        val (kind, raw, want) = l.split("\t")
        val v = unescape(raw)
        val err = if (kind == "pkg") Rules.packageError(v) else Rules.nameError(v)
        check("$kind '$raw' should be $want (rules said: ${err ?: "ok"})", (err == null) == (want == "ok"))
    }
    println("compared " + lines.size + " inputs with the server script")

    // 2. Package suggestions are always acceptable.
    for (n in listOf("My Game", "123", "تطبيقي", "A-b_c.d", "x", "The Quick Brown Fox Jumps Over The Lazy Dog 123456")) {
        check("suggestion for '$n' passes the package rules", Rules.packageError(Rules.suggestPackage(n)) == null)
    }
    check("My Game suggests com.killswitch.mygame", Rules.suggestPackage("My Game") == "com.killswitch.mygame")
    check("a name starting with a digit gets an app prefix", Rules.suggestPackage("123") == "com.killswitch.app123")
    check("a name without latin letters falls back to app", Rules.suggestPackage("تطبيقي") == "com.killswitch.app")

    // 3. Token and repository checks.
    check("empty token refused", Rules.tokenError("") != null)
    check("token with a space refused", Rules.tokenError("github_pat_" + "a".repeat(40) + " x") != null)
    check("token with the wrong start refused", Rules.tokenError("abc" + "a".repeat(50)) != null)
    check("short token refused", Rules.tokenError("github_pat_abc") != null)
    check("fine-grained token accepted", Rules.tokenError("github_pat_" + "A1b2".repeat(20)) == null)
    check("repo owner/name accepted", Rules.repoError("KillswitchYT/tally") == null)
    check("repo without a slash refused", Rules.repoError("tally") != null)
    check("repo with a space refused", Rules.repoError("a b/c") != null)

    // 4. Build ids fit what the build script accepts, and do not repeat.
    val re = Regex("[a-z0-9][a-z0-9-]{2,39}")
    val ids = (1..500).map { Rules.newBuildId() }
    check("build ids match the server pattern", ids.all { re.matches(it) })
    check("500 build ids are all different", ids.toSet().size == 500)

    // 5. File names for Downloads.
    check("plain name", Rules.fileName("My App") == "My App.apk")
    check("slash replaced", Rules.fileName("a/b") == "a_b.apk")
    check("dots only falls back", Rules.fileName("..") == "app.apk")
    check("empty falls back", Rules.fileName("  ") == "app.apk")

    // 6. Every text and background pair is readable (same bars as the reference app).
    for ((pname, p) in listOf("dark" to Palettes.DARK, "light" to Palettes.LIGHT)) {
        for ((bname, bg) in listOf("ink" to p.ink, "lift" to p.lift, "lift2" to p.lift2, "card" to p.card, "sheet" to p.sheet)) {
            check("$pname: main text on $bname", Palettes.contrast(p.text, bg) >= 7.0)
            check("$pname: muted text on $bname", Palettes.contrast(p.muted, bg) >= 4.5)
            check("$pname: accent text on $bname", Palettes.contrast(p.accentText, bg) >= 4.5)
            check("$pname: mint and rose on $bname", Palettes.contrast(p.mint, bg) >= 4.5 && Palettes.contrast(p.rose, bg) >= 4.5)
        }
        check("$pname: accent shapes stand out from the scene", Palettes.contrast(p.accent, p.ink) >= 3.0 && Palettes.contrast(p.accent, p.lift) >= 3.0)
        check("$pname: text on an accent button", Palettes.contrast(p.onAccent, p.accent) >= (if (pname == "dark") 4.5 else 3.0))
    }

    println("passed $passed, failed $failed")
    if (failed > 0) kotlin.system.exitProcess(1)
}
