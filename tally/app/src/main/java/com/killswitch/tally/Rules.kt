package com.killswitch.tally

import java.security.SecureRandom
import java.util.Random

/**
 * Everything that decides whether an input is acceptable. Plain Kotlin with no Android in it, so it is unit tested (tools/RulesTest.kt).
 * The build script on GitHub (tools/apply_custom.py) checks the same rules again, so a changed app cannot slip a bad value through.
 */
object Rules {
    const val OWN_PACKAGE = "com.killswitch.tally"
    private val BAD_PREFIXES = listOf("android.", "com.android.", "com.google.android.", "com.sec.android.", "com.samsung.")
    private val PACKAGE = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
    private val REPO = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")

    fun packageError(raw: String): String? {
        val v = raw.trim()
        return when {
            v.isEmpty() -> "Enter a package id, like com.me.myapp"
            v.length > 150 -> "That package id is too long"
            !PACKAGE.matches(v) -> "Use lowercase letters, digits and underscores, in at least two parts like com.me.myapp. Each part starts with a letter."
            v == OWN_PACKAGE || BAD_PREFIXES.any { v.startsWith(it) } -> "That package id is reserved. Pick another one."
            else -> null
        }
    }

    private fun nameChar(c: Char) = c.isLetterOrDigit() || c == ' ' || c == '-' || c == '_' || c == '.'

    fun nameError(raw: String): String? {
        val v = raw.trim()
        return when {
            v.isEmpty() -> "Enter a name"
            v.length > 30 -> "Keep the name to 30 characters or fewer"
            v.any { !nameChar(it) } -> "Use letters, digits, space, dash, underscore or dot"
            else -> null
        }
    }

    /** com.killswitch.<name> in plain lowercase letters and digits. A name with none (an Arabic name, say) falls back to "app". */
    fun suggestPackage(name: String): String {
        val slug = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.take(30)
        val tail = when {
            slug.isEmpty() -> "app"
            slug[0] in '0'..'9' -> "app$slug"
            else -> slug
        }
        return "com.killswitch.$tail"
    }

    fun tokenError(raw: String): String? {
        val v = raw.trim()
        return when {
            v.isEmpty() -> "Paste your token first"
            v.any { it.isWhitespace() } -> "A token has no spaces in it"
            !(v.startsWith("github_pat_") || v.startsWith("ghp_")) -> "That does not look like a GitHub token. It starts with github_pat_"
            v.length < 40 -> "That token looks too short"
            else -> null
        }
    }

    fun repoError(raw: String): String? = if (REPO.matches(raw.trim())) null else "Use the form owner/name, like KillswitchYT/tally"

    /** 3 to 40 characters of a to z, 0 to 9 and dashes, which is what the build script accepts. */
    fun newBuildId(random: Random = SecureRandom()): String {
        val a = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder("b")
        repeat(10) { sb.append(a[random.nextInt(a.length)]) }
        return sb.toString()
    }

    /** The name the finished file gets in Downloads. */
    fun fileName(name: String): String {
        val clean = name.trim().map { if (nameChar(it)) it else '_' }.joinToString("").trim().trim('.')
        return (if (clean.isEmpty()) "app" else clean) + ".apk"
    }
}
