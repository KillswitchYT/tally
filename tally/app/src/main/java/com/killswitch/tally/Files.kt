package com.killswitch.tally

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Files {
    /** Downloads a release file. Public repositories need no token for this, so none is sent to the download host. */
    fun download(url: String, dest: File) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", "Tally")
            c.instanceFollowRedirects = true
            val code = c.responseCode
            if (code != 200) throw IOException("code $code")
            c.inputStream.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        } finally {
            c.disconnect()
        }
        if (dest.length() < 1_000_000L) throw IOException("the file is too small to be an app")
        dest.inputStream().use {
            val b = ByteArray(2)
            if (it.read(b) != 2 || b[0] != 'P'.code.toByte() || b[1] != 'K'.code.toByte()) throw IOException("the file is not an app")
        }
    }

    /** Puts the file in the phone's Downloads folder. Returns its address, or null if Android refused. */
    fun saveToDownloads(ctx: Context, file: File, displayName: String): String? {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues()
                v.put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                v.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                v.put(MediaStore.Downloads.IS_PENDING, 1)
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return null
                val out = ctx.contentResolver.openOutputStream(uri)
                if (out == null) {
                    ctx.contentResolver.delete(uri, null, null)
                    return null
                }
                out.use { o -> file.inputStream().use { it.copyTo(o) } }
                val done = ContentValues()
                done.put(MediaStore.Downloads.IS_PENDING, 0)
                ctx.contentResolver.update(uri, done, null, null)
                return uri.toString()
            }
            // Android 8 and 9: no shared-storage write without a permission, so the file goes to the app's own Downloads folder.
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            dir.mkdirs()
            val target = File(dir, displayName)
            file.copyTo(target, overwrite = true)
            return Uri.fromFile(target).toString()
        } catch (e: Exception) {
            return null
        }
    }
}

object IconPrep {
    /** Reads a picked image no bigger than [max] pixels on its longest side. Null if it cannot be read. */
    fun load(ctx: Context, uri: Uri, max: Int): Bitmap? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= max && bounds.outHeight / (sample * 2) >= max) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val longest = maxOf(bmp.width, bmp.height)
            if (longest <= max) return bmp
            return Bitmap.createScaledBitmap(bmp, maxOf(1, bmp.width * max / longest), maxOf(1, bmp.height * max / longest), true)
        } catch (e: Exception) {
            return null
        }
    }

    /** Stores the picked image for one build (PNG, 768 px at most) so it can be sent, and sent again on a retry. */
    fun saveForBuild(ctx: Context, uri: Uri, id: String): Boolean {
        try {
            val bmp = load(ctx, uri, 768) ?: return false
            val dir = File(ctx.filesDir, "icons")
            dir.mkdirs()
            File(dir, "$id.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

object Installed {
    const val NOT = 0
    const val OURS = 1      // installed and signed with the project key, so a new build installs over it as an update
    const val OTHER = 2     // installed and signed by somebody else, so a build with this id could not be installed
    const val UNKNOWN = 3

    /** SHA-256 of the fixed, public project key (app/debug.keystore) that signs every app Tally builds. */
    private const val OUR_KEY = "5705a747f60e5f21d42d315ffc97ed21e853df1eba937510132f47967990773a"

    @Suppress("DEPRECATION")
    fun status(ctx: Context, pkg: String): Int {
        if (Build.VERSION.SDK_INT < 28) return UNKNOWN
        val pm = ctx.packageManager
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            else pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: PackageManager.NameNotFoundException) {
            return NOT
        } catch (e: Exception) {
            return UNKNOWN
        }
        val sigs = info.signingInfo?.apkContentsSigners ?: return UNKNOWN
        val ours = sigs.any { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) == OUR_KEY }
        return if (ours) OURS else OTHER
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
