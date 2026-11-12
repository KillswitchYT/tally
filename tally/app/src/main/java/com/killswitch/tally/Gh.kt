package com.killswitch.tally

import org.json.JSONObject
import java.io.IOException
import java.net.HttpRetryException
import java.net.HttpURLConnection
import java.net.URL

class GhError(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { Network, Auth, Permission, NotFound, Other }
}

/** A small GitHub client on the phone's own HTTP stack, so the app needs no extra libraries. The token only ever goes to api.github.com. */
object Gh {
    class Resp(val code: Int, val body: String, val expiry: String?)

    fun api(repo: String) = "https://api.github.com/repos/$repo"

    fun call(token: String?, method: String, url: String, body: String? = null, accept: String = "application/vnd.github+json"): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "Tally")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return Resp(code, text, c.getHeaderField("github-authentication-token-expiration"))
        } catch (e: HttpRetryException) {
            throw failFor(e.responseCode(), "", "reaching GitHub")
        } catch (e: IOException) {
            throw GhError(GhError.Kind.Network, "No connection to GitHub")
        } finally {
            c.disconnect()
        }
    }

    private fun message(body: String): String = try { JSONObject(body).optString("message") } catch (e: Exception) { "" }

    fun failFor(code: Int, body: String, what: String): GhError {
        val msg = message(body)
        return when {
            code == 401 -> GhError(GhError.Kind.Auth, "GitHub did not accept the token. It may have expired. Make a new one and paste it in Setup.")
            code == 403 && msg.contains("rate limit", ignoreCase = true) ->
                GhError(GhError.Kind.Permission, "GitHub is limiting requests right now. Wait a few minutes and try again.")
            code == 403 -> GhError(GhError.Kind.Permission, "GitHub says the token is not allowed to do this ($what). It needs Actions and Contents set to Read and write on your repository.")
            code == 404 -> GhError(GhError.Kind.NotFound, "GitHub could not find what Tally asked for ($what). Check the repository name in Setup, and that the token covers that repository.")
            else -> GhError(GhError.Kind.Other, "GitHub refused the request ($what, code $code). $msg".trim())
        }
    }

    /** Returns the answer when its code is one of [good] (any 2xx when none are given), otherwise throws a plain-words error. */
    fun need(r: Resp, what: String, vararg good: Int): Resp {
        val ok = if (good.isEmpty()) r.code in 200..299 else r.code in good
        if (ok) return r
        throw failFor(r.code, r.body, what)
    }

    /** What the "Check connection" screen shows. */
    fun check(token: String, repo: String): List<Pair<String, String>> {
        val api = api(repo)
        val r = need(call(token, "GET", api), "looking up the repository")
        val o = JSONObject(r.body)
        val rows = ArrayList<Pair<String, String>>()
        rows.add("Repository" to o.optString("full_name"))
        rows.add("Visibility" to if (o.optBoolean("private")) "Private" else "Public")
        rows.add("Push access" to if (o.optJSONObject("permissions")?.optBoolean("push") == true) "Yes" else "No")
        rows.add("Token expires" to (r.expiry ?: "Not shown"))
        val wf = call(token, "GET", "$api/actions/workflows/custom.yml")
        rows.add("Build workflow" to if (wf.code == 200) "Found" else "Missing (custom.yml)")
        val br = call(token, "GET", "$api/git/ref/heads/build-inputs")
        rows.add("Icon branch" to if (br.code == 200) "Ready" else "Created on the first build")
        return rows
    }
}
