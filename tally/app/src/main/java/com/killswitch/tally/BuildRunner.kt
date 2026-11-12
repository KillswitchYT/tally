package com.killswitch.tally

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * One build from start to finish: send the icon, start the build on GitHub, watch it, fetch the finished app, put it in Downloads.
 * Every step is saved in [BuildState] as it happens, so a killed app or phone picks up where it stopped (see BuildService).
 */
object BuildRunner {
    private class Stop(val msg: String, val resume: Boolean = false) : Exception(msg)

    private const val WAIT_MINUTES = 45L

    fun run(ctx: Context, id: String, tick: (Job) -> Unit) {
        fun upd(f: (Job) -> Job) {
            BuildState.update(ctx, id, f)
            BuildState.get(id)?.let(tick)
        }
        val job = BuildState.get(id) ?: return
        var runId = job.runId
        try {
            val token = Settings.token(ctx) ?: throw Stop("Add your GitHub token in Setup, then try again.", job.runId != 0L)
            val api = Gh.api(Settings.repo(ctx))

            if (job.stage == Stage.Preparing || job.stage == Stage.Uploading) {
                upd { it.copy(stage = Stage.Uploading, detail = "Sending your build to GitHub", error = "") }
                val icon = File(ctx.filesDir, "icons/$id.png")
                val iconPath = if (icon.exists()) "builds/$id/icon.png" else ""
                if (iconPath.isNotEmpty()) {
                    ensureBranch(token, api)
                    upload(token, api, iconPath, icon)
                }
                dispatch(token, api, job, iconPath)
                upd { it.copy(stage = Stage.Queued, detail = "Waiting for GitHub to start") }
            }

            if (runId == 0L) {
                runId = findRun(token, api, id)
                upd { it.copy(runId = runId) }
            }

            if (!watch(ctx, token, api, id, runId) { f -> upd(f) }) return   // the build was removed from the list meanwhile
            finish(ctx, token, api, id, job.name) { f -> upd(f) }
        } catch (e: Stop) {
            fail(ctx, id, e.msg, e.resume, tick)
        } catch (e: GhError) {
            fail(ctx, id, e.message ?: "GitHub error", runId != 0L, tick)
        } catch (e: IOException) {
            fail(ctx, id, "The connection dropped: " + (e.message ?: "unknown error"), runId != 0L, tick)
        } catch (e: Exception) {
            fail(ctx, id, "Something went wrong inside Tally (" + e.javaClass.simpleName + ")", runId != 0L, tick)
        }
    }

    private fun fail(ctx: Context, id: String, msg: String, resume: Boolean, tick: (Job) -> Unit) {
        BuildState.update(ctx, id) { it.copy(stage = Stage.Failed, error = msg, detail = "", resume = resume) }
        BuildState.get(id)?.let(tick)
    }

    private fun ensureBranch(token: String, api: String) {
        val r = Gh.call(token, "GET", "$api/git/ref/heads/build-inputs")
        if (r.code == 200) return
        if (r.code != 404) throw Gh.failFor(r.code, r.body, "preparing the icon branch")
        val m = Gh.need(Gh.call(token, "GET", "$api/git/ref/heads/main"), "reading the main branch")
        val sha = JSONObject(m.body).getJSONObject("object").getString("sha")
        val body = JSONObject().put("ref", "refs/heads/build-inputs").put("sha", sha).toString()
        Gh.need(Gh.call(token, "POST", "$api/git/refs", body), "creating the icon branch", 201, 422)
    }

    private fun upload(token: String, api: String, path: String, file: File) {
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val body = JSONObject().put("message", "icon").put("content", b64).put("branch", "build-inputs").toString()
        // 422 means the file is already there from an earlier try, which is fine.
        Gh.need(Gh.call(token, "PUT", "$api/contents/$path", body), "sending the icon", 200, 201, 422)
    }

    private fun dispatch(token: String, api: String, job: Job, iconPath: String) {
        val inputs = JSONObject().put("build_id", job.id).put("package", job.pkg).put("app_name", job.name)
            .put("icon_path", iconPath).put("icon_fit", job.fit)
        val body = JSONObject().put("ref", "main").put("inputs", inputs).toString()
        Gh.need(Gh.call(token, "POST", "$api/actions/workflows/custom.yml/dispatches", body), "starting the build", 204)
    }

    /** GitHub names each run after its build id (run-name in custom.yml), which is how this build's run is told apart from older ones. */
    private fun findRun(token: String, api: String, id: String): Long {
        val until = System.currentTimeMillis() + 120_000L
        while (System.currentTimeMillis() < until) {
            val r = Gh.need(Gh.call(token, "GET", "$api/actions/workflows/custom.yml/runs?event=workflow_dispatch&per_page=20"), "looking for your build")
            val runs = JSONObject(r.body).optJSONArray("workflow_runs")
            if (runs != null) {
                for (i in 0 until runs.length()) {
                    val o = runs.getJSONObject(i)
                    if (o.optString("display_title") == "build $id" || o.optString("name") == "build $id") return o.getLong("id")
                }
            }
            Thread.sleep(4000)
        }
        throw Stop("GitHub did not start the build. Check that custom.yml exists in your repository (Setup, Check connection).")
    }

    private fun stepLabel(token: String, api: String, runId: Long): String {
        val r = Gh.call(token, "GET", "$api/actions/runs/$runId/jobs")
        if (r.code != 200) return "Building"
        val jobs = JSONObject(r.body).optJSONArray("jobs") ?: return "Building"
        if (jobs.length() == 0) return "Starting up"
        val steps = jobs.getJSONObject(0).optJSONArray("steps") ?: return "Starting up"
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            if (s.optString("status") != "in_progress") continue
            val n = s.optString("name")
            return when {
                n.startsWith("Validate") -> "Checking your inputs"
                n.startsWith("Prepare Android SDK") -> "Preparing the tools"
                n.startsWith("Build the container") -> "Compiling your app, about 3 minutes"
                n.startsWith("Verify") -> "Checking the finished app"
                n.startsWith("Publish the APK") -> "Publishing"
                else -> "Starting up"
            }
        }
        return "Starting up"
    }

    /** True when the build succeeded, false when the person removed it meanwhile. Throws when it failed or took too long. */
    private fun watch(ctx: Context, token: String, api: String, id: String, runId: Long, upd: ((Job) -> Job) -> Unit): Boolean {
        val until = System.currentTimeMillis() + WAIT_MINUTES * 60_000L
        while (System.currentTimeMillis() < until) {
            if (BuildState.get(id) == null) return false
            try {
                val r = Gh.need(Gh.call(token, "GET", "$api/actions/runs/$runId"), "checking on your build")
                val o = JSONObject(r.body)
                val status = o.optString("status")
                if (status == "completed") {
                    if (o.optString("conclusion") == "success") return true
                    throw Stop(failReason(token, api, runId), false)
                }
                val label = if (status == "queued") "Waiting in line on GitHub" else stepLabel(token, api, runId)
                upd { it.copy(stage = if (status == "queued") Stage.Queued else Stage.Building, detail = label) }
            } catch (e: GhError) {
                if (e.kind != GhError.Kind.Network) throw e
                upd { it.copy(detail = "Waiting for a connection") }
            }
            Thread.sleep(6000)
        }
        throw Stop("The build took longer than $WAIT_MINUTES minutes. Open the Actions page of your repository to see what happened.", true)
    }

    /** Our own script reports a bad input in plain words (errors.txt on branch build-logs-custom). Otherwise name the step that stopped. */
    private fun failReason(token: String, api: String, runId: Long): String {
        try {
            val rt = Gh.call(token, "GET", "$api/contents/run.txt?ref=build-logs-custom", accept = "application/vnd.github.raw")
            if (rt.code == 200 && rt.body.startsWith("run $runId,")) {
                val er = Gh.call(token, "GET", "$api/contents/errors.txt?ref=build-logs-custom", accept = "application/vnd.github.raw")
                if (er.code == 200) {
                    val line = er.body.lines().firstOrNull { it.startsWith("INPUT ERROR") }
                    if (line != null) return "GitHub refused an input: " + line.removePrefix("INPUT ERROR:").trim()
                }
            }
        } catch (e: Exception) {
            // fall through to the step name
        }
        try {
            val r = Gh.call(token, "GET", "$api/actions/runs/$runId/jobs")
            val steps = JSONObject(r.body).optJSONArray("jobs")?.optJSONObject(0)?.optJSONArray("steps")
            if (steps != null) {
                for (i in 0 until steps.length()) {
                    val s = steps.getJSONObject(i)
                    if (s.optString("conclusion") == "failure") return "The build failed at: " + s.optString("name") + ". Open the Actions page of your repository for the log."
                }
            }
        } catch (e: Exception) {
            // nothing more to say
        }
        return "The build failed on GitHub. Open the Actions page of your repository for the log."
    }

    private fun finish(ctx: Context, token: String, api: String, id: String, name: String, upd: ((Job) -> Job) -> Unit) {
        upd { it.copy(stage = Stage.Downloading, detail = "Downloading your app") }
        var url: String? = null
        for (i in 0 until 6) {
            val r = Gh.call(token, "GET", "$api/releases/tags/out-$id")
            if (r.code == 200) {
                val assets = JSONObject(r.body).optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    url = assets.getJSONObject(0).optString("browser_download_url")
                    break
                }
            } else if (r.code != 404) {
                throw Gh.failFor(r.code, r.body, "finding your app")
            }
            Thread.sleep(3000)
        }
        if (url.isNullOrEmpty()) throw Stop("The build finished, but its file was not found on GitHub.", true)
        val tmp = File(ctx.cacheDir, "dl-$id.apk")
        try {
            try {
                Files.download(url, tmp)
            } catch (e: IOException) {
                throw Stop("The download did not finish: " + (e.message ?: "unknown error"), true)
            }
            val uri = Files.saveToDownloads(ctx, tmp, Rules.fileName(name)) ?: throw Stop("Tally could not save the file to Downloads.", true)
            upd { it.copy(stage = Stage.Done, detail = "", saved = uri, error = "") }
        } finally {
            tmp.delete()
        }
    }
}
