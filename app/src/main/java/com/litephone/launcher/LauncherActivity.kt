package com.litephone.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/** The home screen: clock, date and the short list of allowed apps. */
class LauncherActivity : Activity() {

    private lateinit var banner: TextView
    private lateinit var appList: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var clock: TextClock
    private lateinit var date: TextClock
    private lateinit var wallpaper: ImageView
    private lateinit var dim: View
    private lateinit var bottomBar: LinearLayout
    private lateinit var search: EditText
    private lateinit var header: LinearLayout
    private lateinit var wxBox: LinearLayout
    private lateinit var wxMain: TextView
    private lateinit var wxDetail: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val badgesChanged: () -> Unit = { refreshApps() }
    private val ticker = object : Runnable {
        override fun run() {
            updateBanner()
            if (Weather.enabled(this@LauncherActivity) && Weather.isStale(this@LauncherActivity)) loadWeather()
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 28)

        val customize = { startActivity(Intent(this, CustomizeActivity::class.java)) }
        clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Ui.FG)
            setOnLongClickListener { customize(); true }
        }
        date = TextClock(this).apply {
            format12Hour = "EEEE, MMMM d"
            format24Hour = "EEEE, MMMM d"
            textSize = 18f
            setTextColor(0xFFD0D0D6.toInt())
            setOnLongClickListener { customize(); true }
        }
        banner = Ui.text(this, "", 15f, Ui.BG, bold = true).apply {
            val p = Ui.dp(this@LauncherActivity, 12)
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        search = Ui.input(this, "Search apps", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER).apply {
            textSize = 17f
            background = Ui.rounded(0x33FFFFFF, Ui.dp(this@LauncherActivity, 14).toFloat())
            setHintTextColor(0xFFB0B0B8.toInt())
            imeOptions = EditorInfo.IME_ACTION_GO
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { refreshApps() }
            })
            // "Go" on the keyboard opens the top result.
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) {
                    searchResults().firstOrNull()?.let { app ->
                        try { startActivity(app.intent) } catch (e: Exception) { }
                    }
                    true
                } else false
            }
        }
        appList = Ui.column(this)
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(appList)
        }
        fun bottomLink(label: String, onClick: () -> Unit) =
            Ui.text(this, label, 14f, Ui.MUTED).apply {
                gravity = Gravity.CENTER
                val p = Ui.dp(this@LauncherActivity, 14)
                setPadding(p / 2, p, p / 2, p)
                setOnClickListener { onClick() }
            }
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(bottomLink("\u2699 Settings") {
                startActivity(Intent(this@LauncherActivity, OptionsActivity::class.java))
            }, weight())
        }

        // Weather sits beside the big clock (or under it when centred).
        wxMain = Ui.text(this, "", 30f).apply { isSingleLine = true }
        wxDetail = Ui.text(this, "", 13f, 0xFFD0D0D6.toInt())
        wxBox = Ui.column(this).apply {
            addView(wxMain)
            addView(wxDetail)
            isClickable = true
            setOnClickListener { onWeatherTap() }
            setOnLongClickListener { startActivity(Intent(this@LauncherActivity, CustomizeActivity::class.java)); true }
        }
        header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(clock)
            addView(wxBox)
        }

        val content = Ui.column(this).apply {
            addView(header, Ui.lp(this@LauncherActivity, 24).apply { leftMargin = pad; rightMargin = pad })
            addView(date, Ui.lp(this@LauncherActivity).apply { leftMargin = pad; rightMargin = pad })
            addView(search, Ui.lp(this@LauncherActivity, 16).apply { leftMargin = pad; rightMargin = pad })
            addView(banner, Ui.lp(this@LauncherActivity, 12).apply { leftMargin = pad; rightMargin = pad })
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Ui.dp(this@LauncherActivity, 24); leftMargin = pad; rightMargin = pad
            })
            addView(bottomBar, Ui.lp(this@LauncherActivity))
        }
        Ui.padForSystemBars(content)

        // Layers: wallpaper photo, darkening layer for readability, then the content.
        wallpaper = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        dim = View(this)
        val root = FrameLayout(this).apply {
            addView(wallpaper, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(dim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)
    }

    /** Applies the Customize settings: background, clock, date and alignment. */
    private fun applyLook() {
        val bg = Look.background(this)
        val photo = if (bg == Look.BG_PHOTO) Look.wallpaperBitmap(this) else null
        if (bg == Look.BG_PHONE) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }
        wallpaper.setImageBitmap(photo)
        wallpaper.visibility = if (photo != null) View.VISIBLE else View.GONE
        dim.setBackgroundColor(if (bg == Look.BG_BLACK) Color.TRANSPARENT else Look.dimColor(this))

        val gravity = if (Look.centered(this)) Gravity.CENTER_HORIZONTAL else Gravity.START
        val shadow = bg != Look.BG_BLACK
        for (t in listOf<TextView>(clock, date)) {
            t.gravity = gravity
            if (shadow) t.setShadowLayer(10f, 0f, 1f, 0xCC000000.toInt()) else t.setShadowLayer(0f, 0f, 0f, 0)
        }
        val centered = Look.centered(this)
        header.orientation = if (centered) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        header.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        clock.layoutParams = if (centered)
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        wxBox.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.END
        for (t in listOf(wxMain, wxDetail)) {
            t.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.END
            t.typeface = Look.typeface(this)
            if (shadow) t.setShadowLayer(10f, 0f, 1f, 0xCC000000.toInt()) else t.setShadowLayer(0f, 0f, 0f, 0)
        }
        wxBox.visibility = if (Weather.enabled(this)) View.VISIBLE else View.GONE
        showWeather()
        clock.typeface = Look.clockTypeface(this)
        clock.visibility = if (Look.clock(this) == "hidden") View.GONE else View.VISIBLE
        clock.textSize = if (Look.clock(this) == "small") 48f else 72f
        date.visibility = if (Look.showDate(this)) View.VISIBLE else View.GONE
        date.typeface = Look.typeface(this)
        for (i in 0 until bottomBar.childCount) {
            (bottomBar.getChildAt(i) as? TextView)?.let {
                it.setTextColor(if (shadow) 0xFFD0D0D6.toInt() else Ui.MUTED)
                if (shadow) it.setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AdminSession.end() // back on the home screen = passcode-holder visit is over
        if (!Prefs.isArmed(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        applyLook()
        clearSearch()
        refreshApps()
        if (Weather.enabled(this) && Weather.isStale(this)) loadWeather()
        BadgeService.addListener(badgesChanged)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        BadgeService.removeListener(badgesChanged)
        handler.removeCallbacks(ticker)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        clearSearch()
        scroll.smoothScrollTo(0, 0)
    }

    private fun clearSearch() {
        if (search.text.isNotEmpty()) search.setText("")
        search.clearFocus()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
    }

    /** Allowed apps whose name matches the search; names starting with it come first. */
    private fun searchResults(): List<Policy.AppEntry> {
        val q = search.text.toString().trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return Policy.allowedEntries(this)
            .filter { it.label.lowercase().contains(q) }
            .sortedWith(compareBy({ !it.label.lowercase().startsWith(q) }, { it.label.lowercase() }))
    }

    // The home screen has nowhere to go "back" to.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
    }

    private fun refreshApps() {
        appList.removeAllViews()
        val arrangeApps = { ArrangeActivity.openCategories(this) }
        if (search.text.isNotBlank()) {
            val results = searchResults()
            if (results.isEmpty()) {
                appList.addView(Ui.text(this, "No allowed app matches that.", 17f, 0xFFB0B0B8.toInt()))
            }
            for (app in results) appList.addView(AppRow.build(this, app, compact = true, onLongClick = arrangeApps))
            return
        }
        val categories = Policy.categories(this)
        if (categories.isEmpty()) {
            appList.addView(Ui.text(this, "No apps yet. Ask the passcode holder to add some.", 18f, Ui.MUTED))
            return
        }
        val arrange = { ArrangeActivity.openCategories(this) }
        for (cat in categories) {
            appList.addView(categoryRow(cat) {
                startActivity(Intent(this, CategoryActivity::class.java).putExtra(CategoryActivity.EXTRA_NAME, cat.name))
            }.apply { setOnLongClickListener { arrange(); true } })
        }
    }

    /** One category line: name, a few of its apps underneath, and a count of new notifications. */
    private fun categoryRow(cat: Policy.Category, onClick: () -> Unit): View {
        val centered = Look.centered(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (centered) Gravity.CENTER else Gravity.CENTER_VERTICAL
            val p = Ui.dp(this@LauncherActivity, 11)
            setPadding(0, p, 0, p)
            isClickable = true
            setOnClickListener { onClick() }
        }
        val names = cat.apps.take(3).joinToString(", ") { it.label } + if (cat.apps.size > 3) ", \u2026" else ""
        val texts = Ui.column(this).apply {
            if (centered) gravity = Gravity.CENTER_HORIZONTAL
            addView(AppRow.styledLabel(this@LauncherActivity, cat.name, Look.labelSp(this@LauncherActivity, false)))
            addView(AppRow.styledLabel(this@LauncherActivity, names, 13f, 0xFFB0B0B8.toInt()))
        }
        row.addView(
            texts,
            if (centered) LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val waiting = cat.apps.sumOf { BadgeService.counts[it.pkg] ?: 0 }
        if (waiting > 0) row.addView(Ui.countBadge(this, waiting), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = Ui.dp(this@LauncherActivity, 10) })
        return row
    }

    // ---- Weather ----

    private fun showWeather() {
        if (!Weather.enabled(this)) return
        val now = Weather.cached(this)
        if (now == null) {
            wxMain.text = ""
            wxDetail.text = if (Weather.needsSetup(this)) "Tap for weather" else ""
            return
        }
        val (icon, words) = Weather.describe(now.code, now.isDay)
        wxMain.text = "$icon ${now.temp}\u00B0"
        val place = if (now.place.isNotBlank()) "\n${now.place}" else ""
        wxDetail.text = "$words \u00B7 H ${now.hi}\u00B0 L ${now.lo}\u00B0$place"
    }

    private fun loadWeather(force: Boolean = false) {
        Weather.refresh(this, force) { _, err ->
            if (isFinishing || isDestroyed) return@refresh
            if (err != null && Weather.cached(this) == null) {
                wxMain.text = ""
                wxDetail.text = if (err == "setup") "Tap for weather" else err
            } else {
                showWeather()
            }
        }
    }

    /** Tap: set up weather if needed, otherwise open the weather app. */
    private fun onWeatherTap() {
        if (Weather.needsSetup(this)) {
            if (Weather.usesCity(this)) {
                startActivity(Intent(this, CustomizeActivity::class.java))
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 42)
            }
            return
        }
        val app = Weather.WEATHER_APPS.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (app != null) {
            startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            Toast.makeText(this, "Updating weather\u2026", Toast.LENGTH_SHORT).show()
            loadWeather(force = true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 42) return
        if (Weather.hasLocationPermission(this)) {
            loadWeather(force = true)
        } else {
            Toast.makeText(this, "No location, so no weather. You can pick a city in Customize.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateBanner() {
        val armed = Prefs.isArmed(this)
        val paused = Policy.pauseRemainingMs(this) > 0
        val (msg, color) = when {
            armed && !Policy.isGuardEnabled(this) ->
                "App blocker is OFF. Tap for the passcode holder." to Ui.DANGER
            armed && !Policy.isDefaultHome(this) ->
                "Lite Phone isn't the Home app. Tap for the passcode holder." to Ui.WARN
            armed && paused ->
                "All apps unlocked until ${Policy.pauseEndText(this)}. Tap to lock now." to Ui.WARN
            armed && WebPass.isActive(this) ->
                "\uD83C\uDF10 Web pass until ${WebPass.endText(this)}. Tap to open the browser." to Ui.ACCENT
            else -> null to 0
        }
        if (msg == null) {
            banner.visibility = View.GONE
            return
        }
        banner.text = msg
        banner.background = Ui.rounded(color, Ui.dp(this, 12).toFloat())
        banner.visibility = View.VISIBLE
        banner.setOnClickListener {
            val healthy = Policy.isGuardEnabled(this) && Policy.isDefaultHome(this)
            if (healthy && !paused && WebPass.isActive(this)) {
                WebPass.browserIntent(this)?.let { startActivity(it) }
            } else if (paused && healthy) {
                Policy.lockNow(this)
                updateBanner()
                Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show()
            } else {
                PinActivity.openAdmin(this)
            }
        }
    }
}
