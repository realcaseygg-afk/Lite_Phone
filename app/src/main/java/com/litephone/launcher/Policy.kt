package com.litephone.launcher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import java.text.DateFormat
import java.util.Date

/** Decides which apps may open. */
object Policy {

    // The apps you asked for. Phone/Messages/Camera are looked up on the device;
    // these are the Samsung (then Google) package names used as a fallback.
    private val PHONE = listOf("com.samsung.android.dialer", "com.google.android.dialer")
    private val MESSAGES = listOf("com.samsung.android.messaging", "com.google.android.apps.messaging")
    private val CAMERA = listOf("com.sec.android.app.camera", "com.google.android.GoogleCamera")
    private val NAMED_APPS = listOf(
        "com.spotify.music",               // Spotify
        "com.audible.application",         // Audible
        "com.anthropic.claude",            // Claude
        "com.openai.chatgpt",              // ChatGPT
        "com.microsoft.office.outlook",    // Outlook
        "com.instructure.candroid",        // Canvas Student
        "com.duosecurity.duomobile",       // Duo Mobile
    )

    /**
     * Parts of Android that must never be blocked: the status bar and notification
     * shade, calls and emergency screens, permission pop-ups, share sheet, photo
     * and file pickers, Google sign-in, fingerprint prompts, contacts (part of the
     * phone app) and the clock (so alarms can always be turned off).
     */
    private val ALWAYS_ALLOWED = setOf(
        "android",
        "com.android.systemui",
        "com.android.intentresolver",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.credentialmanager",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.samsung.android.app.telephonyui",
        "com.android.stk",
        "com.android.emergency",
        "com.samsung.android.emergency",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",
        "com.samsung.android.biometrics.app.setting",
        "com.google.android.gms",
        "com.android.providers.media.module",
        "com.google.android.providers.media.module",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.samsung.android.app.sharelive",
        "com.android.contacts",
        "com.samsung.android.app.contacts",
        "com.google.android.contacts",
        "com.sec.android.app.clockpackage",
        "com.google.android.deskclock",
    )

    const val SETTINGS = "com.android.settings"

    /** Bump when adding to the defaults; existing installs get the new apps once. */
    const val DEFAULTS_VERSION = 3
    val ADDED_IN_VERSION = listOf(
        2 to listOf(SETTINGS),
        3 to listOf("com.instructure.candroid", "com.duosecurity.duomobile"),
    )

    class Suggestion(val pkg: String, val group: String, val why: String)

    /** Shown (if installed) at the top of the allowed-apps screen for one-tap adding. */
    val SUGGESTED = listOf(
        Suggestion("com.azure.authenticator", "School", "Sign-in codes for Microsoft school accounts"),
        Suggestion("com.google.android.apps.authenticator2", "School", "Sign-in codes"),
        Suggestion("com.microsoft.teams", "School", "Class chats and meetings"),
        Suggestion("us.zoom.videomeetings", "School", "Online classes and office hours"),
        Suggestion("com.google.android.apps.classroom", "School", "Assignments"),
        Suggestion("com.microsoft.office.officehubrow", "School", "Word, Excel and PowerPoint in one app"),
        Suggestion("com.microsoft.office.word", "School", "Papers"),
        Suggestion("com.microsoft.office.excel", "School", "Spreadsheets"),
        Suggestion("com.microsoft.office.powerpoint", "School", "Slides"),
        Suggestion("com.microsoft.office.onenote", "School", "Notes"),
        Suggestion("com.microsoft.skydrive", "School", "School files"),
        Suggestion("com.google.android.apps.docs", "School", "School files"),
        Suggestion("com.google.android.apps.docs.editors.docs", "School", "Papers"),
        Suggestion("com.google.android.apps.docs.editors.sheets", "School", "Spreadsheets"),
        Suggestion("com.google.android.apps.docs.editors.slides", "School", "Slides"),
        Suggestion("com.adobe.reader", "School", "Readings and PDFs"),
        Suggestion("com.quizlet.quizletandroid", "School", "Studying"),
        Suggestion("com.google.android.gm", "School", "School email (if your school uses Gmail)"),
        Suggestion("com.samsung.android.calendar", "Everyday", "Class schedule"),
        Suggestion("com.google.android.calendar", "Everyday", "Class schedule"),
        Suggestion("com.samsung.android.app.notes", "Everyday", "Quick notes"),
        Suggestion("com.sec.android.app.popupcalculator", "Everyday", "Calculator"),
        Suggestion("com.sec.android.gallery3d", "Everyday", "See photos you take (camera preview opens this)"),
        Suggestion("com.sec.android.app.myfiles", "Everyday", "Downloads and attachments"),
        Suggestion("com.sec.android.app.clockpackage", "Everyday", "Set alarms and timers (alarms ring either way)"),
        Suggestion("com.google.android.apps.maps", "Everyday", "Getting around"),
        Suggestion("com.samsung.android.spay", "Everyday", "Tap to pay and student ID cards"),
        Suggestion("com.google.android.apps.walletnfcrefapp", "Everyday", "Tap to pay and student ID cards"),
    )

    /**
     * Settings pages showing any of this text are locked: everything about Lite
     * Phone itself (app info, accessibility, Home app, device admin, battery
     * sleep lists) and the developer options / build number used to turn on USB debugging.
     */
    val LOCKED_SETTINGS_TEXT = listOf("Lite Phone", "USB debugging", "Wireless debugging", "Build number")

    /** Apps where the allowed-apps screen shows a warning. */
    val RISKY = mapOf(
        SETTINGS to "Pages that could switch Lite Phone off stay locked",
        "com.sec.android.app.launcher" to "Lets anyone leave Lite Phone",
        "com.android.vending" to "Can install and uninstall apps",
        "com.sec.android.app.samsungapps" to "Can install and uninstall apps",
    )

    // ---- Blocking switch -------------------------------------------------------

    private val pause = Deadline("pause_until", "pause_boot")

    /** True when apps that aren't allowed should be closed right now. */
    fun isEnforcing(ctx: Context): Boolean =
        Prefs.isArmed(ctx) && Passcode.isSet(ctx) && pause.remainingMs(ctx) == 0L

    fun pauseRemainingMs(ctx: Context): Long = pause.remainingMs(ctx)

    fun pauseFor(ctx: Context, ms: Long) = pause.set(ctx, ms)

    fun pauseAtLeast(ctx: Context, ms: Long) {
        if (pause.remainingMs(ctx) < ms) pause.set(ctx, ms)
    }

    fun lockNow(ctx: Context) = pause.clear(ctx)

    fun pauseEndText(ctx: Context): String =
        DateFormat.getTimeInstance(DateFormat.SHORT)
            .format(Date(System.currentTimeMillis() + pause.remainingMs(ctx)))

    // ---- Allow list ------------------------------------------------------------

    fun isAllowed(ctx: Context, pkg: String, className: String?): Boolean {
        if (pkg == ctx.packageName) return true
        if (pkg in ALWAYS_ALLOWED) return true
        if (pkg in Prefs.allowedList(ctx)) return true
        if (pkg in systemHelpers(ctx)) return true
        // The recent-apps screen belongs to the stock launcher on Samsung; let it
        // show (anything opened from it is still checked).
        if (className != null && className.contains("Recents", ignoreCase = true) &&
            pkg in homePackages(ctx)
        ) return true
        return false
    }

    fun defaultAllowedPackages(ctx: Context): List<String> {
        val pm = ctx.packageManager
        fun installed(p: String) = try {
            pm.getApplicationInfo(p, 0); true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        fun pick(found: String?, fallbacks: List<String>): List<String> =
            if (found != null && found != "android") listOf(found)
            else fallbacks.filter { installed(it) }.ifEmpty { fallbacks.take(1) }

        val dialer = try {
            ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        } catch (e: Exception) {
            null
        }
        val sms = try {
            Telephony.Sms.getDefaultSmsPackage(ctx)
        } catch (e: Exception) {
            null
        }
        val camera = pm.resolveActivity(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

        return (pick(dialer, PHONE) + pick(sms, MESSAGES) + pick(camera, CAMERA) + NAMED_APPS + SETTINGS).distinct()
    }

    // Keyboards and the default phone app are always allowed. Cached briefly
    // because this runs on every app switch.
    private var helpersCache: Set<String> = emptySet()
    private var helpersAt = 0L

    private fun systemHelpers(ctx: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - helpersAt < 30_000 && helpersCache.isNotEmpty()) return helpersCache
        val set = HashSet<String>()
        try {
            ctx.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList?.forEach { set.add(it.packageName) }
        } catch (e: Exception) { }
        try {
            ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { set.add(it) }
        } catch (e: Exception) { }
        helpersCache = set
        helpersAt = now
        return set
    }

    private fun homePackages(ctx: Context): Set<String> =
        ctx.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }.toSet()

    // ---- Home screen entries ---------------------------------------------------

    class AppEntry(val pkg: String, val label: String, val icon: Drawable, val intent: Intent)

    fun entryFor(ctx: Context, pkg: String): AppEntry? {
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return null
        val info = try {
            pm.getApplicationInfo(pkg, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info), intent)
    }

    fun homeEntries(ctx: Context): List<AppEntry> =
        Prefs.allowedList(ctx).mapNotNull { entryFor(ctx, it) }

    /** Every app that has an icon in the normal app drawer. */
    fun launchableApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ctx.packageName }
            .mapNotNull { entryFor(ctx, it) }
    }

    // ---- Protection status -----------------------------------------------------

    fun isGuardEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(ctx, GuardService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun isDefaultHome(ctx: Context): Boolean {
        val res = ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return res?.activityInfo?.packageName == ctx.packageName
    }

    fun isUninstallProtected(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java)
            ?.isAdminActive(AdminReceiver.component(ctx)) == true
}
