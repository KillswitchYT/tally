package com.killswitch.tally

import android.content.Context

object Settings {
    const val DEFAULT_REPO = "KillswitchYT/tally"

    private fun p(c: Context) = c.getSharedPreferences("tally", Context.MODE_PRIVATE)

    /** 0 follows the phone, 1 dark, 2 light. */
    fun theme(c: Context): Int = p(c).getInt("th", 0).coerceIn(0, 2)
    fun setTheme(c: Context, v: Int) { p(c).edit().putInt("th", v).commit() }

    fun repo(c: Context): String = p(c).getString("repo", null)?.takeIf { Rules.repoError(it) == null } ?: DEFAULT_REPO
    fun setRepo(c: Context, v: String) { p(c).edit().putString("repo", v.trim()).commit() }

    fun token(c: Context): String? = p(c).getString("tok", null)?.let { Secret.open(it) }
    fun hasToken(c: Context): Boolean = token(c) != null
    fun setToken(c: Context, t: String): Boolean = try {
        p(c).edit().putString("tok", Secret.seal(t.trim())).commit()
    } catch (e: Exception) {
        false
    }
    fun clearToken(c: Context) { p(c).edit().remove("tok").commit() }
}
