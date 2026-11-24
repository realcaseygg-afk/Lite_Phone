package com.litephone.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lite Phone updates itself: downloads the newest build (or takes an APK file),
 * checks it really is Lite Phone signed with the same key and newer, then installs it.
 */
object Updater {
    // Filled in by the build from the GitHub repository it was built from.
    val UPDATE_URL: String = BuildConfig.UPDATE_URL

    class Candidate(val file: File, val versionCode: Long, val versionName: String)

    fun currentVersionCode(ctx: Context): Long = packageInfo(ctx, ctx.packageName, null)?.let { codeOf(it) } ?: 0L

    @Suppress("DEPRECATION")
    private fun codeOf(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Downloads the latest release. Throws with a readable message on failure. */
    fun download(ctx: Context): File {
        val out = File(ctx.cacheDir, "update.apk")
        val conn = URL(UPDATE_URL).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            val code = conn.responseCode
            if (code == 404) throw Exception(
                "No update found online. If your GitHub repository is private, use \"Install from a file\" instead."
            )
            if (code !in 200..299) throw Exception("Download failed (error $code).")
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        return out
    }

    /** Copies a picked APK into the app so it can be checked and installed. */
    fun copyFromUri(ctx: Context, uri: Uri): File {
        val out = File(ctx.cacheDir, "update.apk")
        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            ?: throw Exception("Couldn't read that file.")
        return out
    }

    /** Returns the update if [file] is a newer Lite Phone signed with the same key. */
    fun check(ctx: Context, file: File): Candidate? {
        val archive = packageInfo(ctx, null, file) ?: throw Exception("That file isn't an Android app.")
        if (archive.packageName != ctx.packageName) throw Exception("That file isn't Lite Phone.")
        val mine = packageInfo(ctx, ctx.packageName, null) ?: throw Exception("Couldn't read the installed version.")
        if (!sameSigner(mine, archive)) throw Exception("That file wasn't signed with Lite Phone's key, so it can't be trusted.")
        val code = codeOf(archive)
        if (code <= currentVersionCode(ctx)) return null
        return Candidate(file, code, archive.versionName ?: code.toString())
    }

    fun install(ctx: Context, candidate: Candidate) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            // Android 12+: an app updating itself can skip the confirmation screen.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, candidate.file.length()).use { out ->
                candidate.file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(ctx: Context, pkg: String?, apk: File?): PackageInfo? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return try {
            if (apk != null) pm.getPackageArchiveInfo(apk.path, flags) else pm.getPackageInfo(pkg!!, flags)
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(a: PackageInfo, b: PackageInfo): Boolean {
        val sa = if (Build.VERSION.SDK_INT >= 28) a.signingInfo?.apkContentsSigners else a.signatures
        val sb = if (Build.VERSION.SDK_INT >= 28) b.signingInfo?.apkContentsSigners else b.signatures
        if (sa.isNullOrEmpty() || sb.isNullOrEmpty()) return false
        return sa.map { it.toCharsString() }.toSet() == sb.map { it.toCharsString() }.toSet()
    }
}

/** Gets the install result. If Android still wants a confirmation tap, shows it. */
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                Policy.allowInstallerBriefly(ctx) // the confirmation screen belongs to Android's installer
                try {
                    ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
            }
            PackageInstaller.STATUS_SUCCESS -> { } // Lite Phone restarts as the new version
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                Toast.makeText(ctx, "Update failed: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
