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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Updates Lite Phone and your own apps. Downloads the newest APK (or takes a file),
 * checks it's the right app, signed with the same key as the installed one and
 * newer, then installs it.
 *
 * Lite Phone comes from this repo's latest public release. Your other apps are
 * listed in my-apps.json in this repo; private repos are read with a read-only
 * GitHub token saved on the phone.
 */
object Updater {
    private const val KEY_TOKEN = "github_token"

    class AppSource(val name: String, val pkg: String, val repo: String, val path: String?, val asset: String?)

    class Candidate(
        val file: File, val pkg: String, val label: String,
        val versionCode: Long, val versionName: String, val isNew: Boolean,
    )

    // ---- GitHub token (only sent to api.github.com) ----

    fun token(ctx: Context): String? = Prefs.get(ctx).getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setToken(ctx: Context, token: String?) {
        Prefs.get(ctx).edit().putString(KEY_TOKEN, token?.trim()?.takeIf { it.isNotEmpty() }).apply()
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun currentVersionCode(ctx: Context): Long = installedInfo(ctx, ctx.packageName)?.let { codeOf(it) } ?: 0L

    // ---- Where updates come from ----

    /** The apps listed in my-apps.json, read fresh each time. */
    fun fetchAppList(ctx: Context): List<AppSource> {
        val text = open(BuildConfig.APPS_LIST_URL, null, null).use { it.readText() }
        val apps = JSONObject(text).optJSONArray("apps") ?: return emptyList()
        return (0 until apps.length()).mapNotNull { i ->
            val o = apps.optJSONObject(i) ?: return@mapNotNull null
            val pkg = o.optString("package").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val repo = o.optString("repo").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AppSource(
                name = o.optString("name").ifBlank { pkg },
                pkg = pkg,
                repo = repo,
                path = o.optString("path").takeIf { it.isNotBlank() },
                asset = o.optString("asset").takeIf { it.isNotBlank() },
            )
        }
    }

    fun downloadLitePhone(ctx: Context): File {
        val out = File(ctx.cacheDir, "update-${ctx.packageName}.apk")
        val conn = connect(BuildConfig.UPDATE_URL, null, null)
        conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    fun downloadApp(ctx: Context, src: AppSource): File {
        val token = token(ctx)
        val out = File(ctx.cacheDir, "update-${src.pkg}.apk")
        val conn = if (src.path != null) {
            val path = src.path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            connect("https://api.github.com/repos/${src.repo}/contents/$path", "application/vnd.github.raw", token)
        } else {
            val asset = src.asset ?: throw Exception("No \"path\" or \"asset\" set in my-apps.json.")
            val release = connect("https://api.github.com/repos/${src.repo}/releases/latest", "application/vnd.github+json", token)
                .inputStream.bufferedReader().use { it.readText() }
            val assets = JSONObject(release).optJSONArray("assets")
            val match = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name") == asset } ?: throw Exception("The latest release has no $asset.")
            connect(match.getString("url"), "application/octet-stream", token)
        }
        conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    /** Copies a picked APK into the app so it can be checked and installed. */
    fun copyFromUri(ctx: Context, uri: Uri): File {
        val out = File(ctx.cacheDir, "update-picked.apk")
        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            ?: throw Exception("Couldn't read that file.")
        return out
    }

    // ---- Checking and installing ----

    /**
     * Returns the update if [file] is newer than what's installed and signed with the
     * same key. Null means already up to date. [expectedPkg] null (a picked file) only
     * accepts apps that are already installed.
     */
    fun check(ctx: Context, file: File, expectedPkg: String?): Candidate? {
        val pm = ctx.packageManager
        val archive = archiveInfo(ctx, file) ?: throw Exception("That file isn't an Android app.")
        val pkg = archive.packageName
        if (expectedPkg != null && pkg != expectedPkg) throw Exception("The downloaded file is a different app ($pkg).")
        val label = archive.applicationInfo?.let {
            it.sourceDir = file.path
            it.publicSourceDir = file.path
            pm.getApplicationLabel(it).toString()
        } ?: pkg
        val installed = installedInfo(ctx, pkg)
        val code = codeOf(archive)
        if (installed == null) {
            if (expectedPkg == null) throw Exception("$label isn't installed, so there's nothing to update.")
            return Candidate(file, pkg, label, code, archive.versionName ?: code.toString(), isNew = true)
        }
        if (!sameSigner(installed, archive)) throw Exception("That $label file wasn't signed with the same key as the installed app, so it can't be trusted.")
        if (code <= codeOf(installed)) return null
        return Candidate(file, pkg, label, code, archive.versionName ?: code.toString(), isNew = false)
    }

    fun install(ctx: Context, candidate: Candidate) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(candidate.pkg)
            // Android 12+: no confirmation when an app updates itself (or apps it installed).
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
            val result = Intent(ctx, InstallResultReceiver::class.java).putExtra(InstallResultReceiver.EXTRA_LABEL, candidate.label)
            val pending = PendingIntent.getBroadcast(ctx, id, result, flags)
            session.commit(pending.intentSender)
        }
    }

    // ---- Helpers ----

    @Suppress("DEPRECATION")
    private fun codeOf(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private val SIGNING_FLAGS =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    private fun installedInfo(ctx: Context, pkg: String): PackageInfo? = try {
        ctx.packageManager.getPackageInfo(pkg, SIGNING_FLAGS)
    } catch (e: Exception) {
        null
    }

    private fun archiveInfo(ctx: Context, apk: File): PackageInfo? = try {
        ctx.packageManager.getPackageArchiveInfo(apk.path, SIGNING_FLAGS)
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(a: PackageInfo, b: PackageInfo): Boolean {
        val sa = if (Build.VERSION.SDK_INT >= 28) a.signingInfo?.apkContentsSigners else a.signatures
        val sb = if (Build.VERSION.SDK_INT >= 28) b.signingInfo?.apkContentsSigners else b.signatures
        if (sa.isNullOrEmpty() || sb.isNullOrEmpty()) return false
        return sa.map { it.toCharsString() }.toSet() == sb.map { it.toCharsString() }.toSet()
    }

    private fun HttpURLConnection.readText(): String = inputStream.bufferedReader().use { it.readText() }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try { block(this) } finally { disconnect() }

    private fun open(url: String, accept: String?, token: String?) = connect(url, accept, token)

    /**
     * GET with redirects followed by hand, so the token only ever goes to
     * api.github.com and never to the download servers it redirects to.
     */
    internal fun connect(url: String, accept: String?, token: String?): HttpURLConnection {
        var u = URL(url)
        repeat(6) {
            val c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            if (accept != null) c.setRequestProperty("Accept", accept)
            if (u.host == "api.github.com") {
                c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location") ?: throw Exception("Download failed (bad redirect).")
                c.disconnect()
                u = URL(u, location)
                return@repeat
            }
            if (code in 200..299) return c
            c.disconnect()
            throw Exception(
                when (code) {
                    401 -> "GitHub rejected the token. It may have expired; make a new one."
                    403 -> "GitHub refused the request (error 403). Try again later, or add a token."
                    404 -> "Not found. If the repo is private, add a GitHub token below; also check my-apps.json."
                    else -> "Download failed (error $code)."
                }
            )
        }
        throw Exception("Download failed (too many redirects).")
    }
}

/** Gets the install result. If Android still wants a confirmation tap, shows it. */
class InstallResultReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_LABEL = "label"
    }

    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "The app"
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                Policy.allowInstallerBriefly(ctx) // lets the confirmation (and any Settings step) show
                try {
                    ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
            }
            // If Lite Phone itself was updated, this process is already gone.
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(ctx, "$label updated", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                val help = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED ->
                        "$label: the install was cancelled before it was confirmed. Try again and tap Install or " +
                            "Allow if Android asks. If Samsung's Auto Blocker is on, turn it off first."
                    PackageInstaller.STATUS_FAILURE_BLOCKED ->
                        "$label: Android blocked the install. Check Auto Blocker and Play Protect, then try again."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free space to install $label."
                    else -> "$label update failed: $msg"
                }
                Toast.makeText(ctx, help, Toast.LENGTH_LONG).show()
            }
        }
    }
}
