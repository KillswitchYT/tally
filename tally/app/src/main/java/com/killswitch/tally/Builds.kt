package com.killswitch.tally

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Stage {
    Preparing, Uploading, Queued, Building, Downloading, Done, Failed;

    val working: Boolean get() = this != Done && this != Failed
}

/** One requested app. [resume] means the build itself finished fine and only the last steps failed, so "Try again" can pick up from there. */
data class Job(
    val id: String, val name: String, val pkg: String, val fit: String, val created: Long,
    val stage: Stage = Stage.Preparing, val detail: String = "", val runId: Long = 0L,
    val saved: String = "", val error: String = "", val resume: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("pkg", pkg).put("fit", fit).put("created", created)
        .put("stage", stage.name).put("detail", detail).put("runId", runId).put("saved", saved).put("error", error).put("resume", resume)

    companion object {
        fun from(o: JSONObject): Job? {
            val id = o.optString("id")
            if (id.isEmpty()) return null
            val st = Stage.values().firstOrNull { it.name == o.optString("stage") } ?: Stage.Failed
            return Job(
                id, o.optString("name"), o.optString("pkg"), o.optString("fit", "fill"), o.optLong("created"), st,
                o.optString("detail"), o.optLong("runId"), o.optString("saved"), o.optString("error"), o.optBoolean("resume"),
            )
        }
    }
}

/** The list of builds, kept on the phone and shown on the Builds tab. Writes are saved at once, so a killed app loses nothing. */
object BuildState {
    var jobs by mutableStateOf<List<Job>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var damaged by mutableStateOf(false)
        private set

    private const val KEY = "builds"
    private fun p(c: Context) = c.getSharedPreferences("tally", Context.MODE_PRIVATE)

    @Synchronized fun load(c: Context) {
        val raw = p(c).getString(KEY, "") ?: ""
        if (raw.isEmpty()) {
            jobs = emptyList(); damaged = false; loaded = true
            return
        }
        try {
            val a = JSONArray(raw)
            jobs = (0 until a.length()).mapNotNull { Job.from(a.getJSONObject(it)) }
            damaged = false
        } catch (e: Exception) {
            jobs = emptyList(); damaged = true
        }
        loaded = true
    }

    @Synchronized private fun save(c: Context, list: List<Job>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        p(c).edit().putString(KEY, a.toString()).commit()
        jobs = list
    }

    @Synchronized fun add(c: Context, j: Job) { save(c, (listOf(j) + jobs).take(30)) }

    @Synchronized fun update(c: Context, id: String, f: (Job) -> Job) {
        val cur = jobs
        if (cur.none { it.id == id }) return
        save(c, cur.map { if (it.id == id) f(it) else it })
    }

    @Synchronized fun remove(c: Context, id: String) {
        File(c.filesDir, "icons/$id.png").delete()
        save(c, jobs.filter { it.id != id })
    }

    @Synchronized fun reset(c: Context) {
        p(c).edit().remove(KEY).commit()
        jobs = emptyList(); damaged = false
    }

    fun get(id: String): Job? = jobs.firstOrNull { it.id == id }

    /**
     * "Try again". A build that only failed at the end (download, saving) carries on from there. Any other failure starts a new build with
     * the same inputs under a new id, because GitHub finds a build by its id and the old one is already finished.
     */
    @Synchronized fun retry(c: Context, id: String): String? {
        val old = get(id) ?: return null
        if (old.resume && old.runId != 0L) {
            update(c, id) { it.copy(stage = Stage.Building, detail = "Checking on your build", error = "") }
            return id
        }
        val fresh = Rules.newBuildId()
        val icon = File(c.filesDir, "icons/$id.png")
        if (icon.exists()) icon.copyTo(File(c.filesDir, "icons/$fresh.png"), overwrite = true)
        remove(c, id)
        add(c, Job(fresh, old.name, old.pkg, old.fit, System.currentTimeMillis()))
        return fresh
    }
}
