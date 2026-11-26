package com.litephone.launcher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.util.SizeF
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * The home screen, two pages you swipe between:
 *  1. Clock + weather, search, category tiles, and a dock of your 4 most-used apps.
 *  2. Agenda: your calendar and Canvas due dates for the next 7 days.
 * Page dots and Settings sit underneath both.
 */
class LauncherActivity : Activity() {

    private lateinit var pager: Pager
    private lateinit var banner: TextView
    private lateinit var appList: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var dock: LinearLayout
    private lateinit var agendaList: LinearLayout
    private lateinit var widgetBox: LinearLayout
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private var pendingWidgetId = -1
    private lateinit var dots: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var date: TextView
    private lateinit var wordView: TextView
    private lateinit var gardenView: TextView
    private lateinit var cardBox: LinearLayout
    private lateinit var agendaTitleText: TextView
    private var themeAtCreate = false
    private var cardsAt = 0L
    private lateinit var wallpaper: ImageView
    private lateinit var dim: View
    private lateinit var bottomBar: LinearLayout
    private lateinit var search: EditText
    private lateinit var header: LinearLayout
    private lateinit var wxBox: LinearLayout
    private lateinit var wxMain: TextView
    private lateinit var wxDetail: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val grey = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    private val badgesChanged: () -> Unit = { refreshApps(); refreshDock() }
    private var agendaLoading = false
    private val ticker = object : Runnable {
        override fun run() {
            updateBanner()
            if (themeAtCreate != Theme.paper) { recreate(); return }
            updateDate()
            if (Weather.enabled(this@LauncherActivity) && Weather.isStale(this@LauncherActivity)) loadWeather()
            if (System.currentTimeMillis() - cardsAt > 10 * 60_000L) loadExtras()
            handler.postDelayed(this, 10_000)
        }
    }

    private companion object {
        const val REQ_LOCATION = 42
        const val REQ_CALENDAR = 43
        const val REQ_CONTACTS = 44
        const val REQ_BIND_WIDGET = 50
        const val REQ_CONFIGURE_WIDGET = 51
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 24)
        val customize = { startActivity(Intent(this, CustomizeActivity::class.java)) }

        // ---- Page 1: apps ----
        clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Ui.FG)
            setOnLongClickListener { customize(); true }
        }
        themeAtCreate = Theme.paper
        date = Ui.text(this, "", 18f, Ui.SUBTLE).apply {
            setOnLongClickListener { customize(); true }
        }
        wordView = Ui.text(this, "", 14f, Ui.SUBTLE).apply {
            visibility = View.GONE
            setOnClickListener {
                packageManager.getLaunchIntentForPackage(Vocab.NOTEBOOK_PKG)?.let { launch(it, "毎日ノート") }
            }
        }
        gardenView = Ui.text(this, "", 15f, Ui.SUBTLE).apply {
            visibility = View.GONE
            isSingleLine = true
        }
        cardBox = Ui.column(this)
        wxMain = Ui.text(this, "", 30f).apply { isSingleLine = true }
        wxDetail = Ui.text(this, "", 13f, Ui.SUBTLE)
        wxBox = Ui.column(this).apply {
            addView(wxMain)
            addView(wxDetail)
            isClickable = true
            setOnClickListener { onWeatherTap() }
            setOnLongClickListener { customize(); true }
        }
        header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(clock)
            addView(wxBox)
        }
        search = Ui.input(this, "Search apps", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER).apply {
            textSize = 17f
            background = Ui.rounded(Ui.FIELD, Ui.dp(this@LauncherActivity, 14).toFloat())
            setHintTextColor(Ui.DIM)
            imeOptions = EditorInfo.IME_ACTION_GO
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { refreshApps() }
            })
            // "Go" on the keyboard opens the top result.
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) {
                    val cmd = Commands.suggestions(this@LauncherActivity, text.toString(), commandHooks).firstOrNull()
                    if (cmd != null) runCommand(cmd)
                    else searchResults().firstOrNull()?.let { app -> launch(app.intent, app.label) }
                    true
                } else false
            }
        }
        banner = Ui.text(this, "", 15f, Ui.BG, bold = true).apply {
            val p = Ui.dp(this@LauncherActivity, 12)
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        appList = Ui.column(this)
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(Ui.column(this@LauncherActivity).apply {
                addView(cardBox, Ui.lp(this@LauncherActivity))
                addView(appList, Ui.lp(this@LauncherActivity))
            })
        }
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = Ui.dp(this@LauncherActivity, 10)
            setPadding(0, p, 0, Ui.dp(this@LauncherActivity, 2))
        }
        val appsPage = Ui.column(this).apply {
            setPadding(pad, 0, pad, 0)
            addView(header, Ui.lp(this@LauncherActivity, 20))
            addView(date, Ui.lp(this@LauncherActivity))
            addView(wordView, Ui.lp(this@LauncherActivity, 4))
            addView(gardenView, Ui.lp(this@LauncherActivity, 4))
            addView(search, Ui.lp(this@LauncherActivity, 14))
            addView(banner, Ui.lp(this@LauncherActivity, 10))
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Ui.dp(this@LauncherActivity, 14)
            })
            addView(dock, Ui.lp(this@LauncherActivity))
        }

        // ---- Page 2: agenda ----
        agendaList = Ui.column(this)
        val agendaScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(agendaList)
        }
        val agendaTitle = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            agendaTitleText = Ui.text(this@LauncherActivity, "Agenda", 34f, bold = true)
            addView(agendaTitleText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.smallButton(this@LauncherActivity, "+ Widgets", Ui.MUTED) { widgetMenu() })
            addView(Ui.smallButton(this@LauncherActivity, "↻", Ui.MUTED) { loadAgenda(force = true) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = Ui.dp(this@LauncherActivity, 6)
                })
        }
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(applicationContext, AgendaWidgets.HOST_ID)
        widgetBox = Ui.column(this)
        val agendaPage = Ui.column(this).apply {
            setPadding(pad, 0, pad, 0)
            addView(agendaTitle, Ui.lp(this@LauncherActivity, 24))
            addView(widgetBox, Ui.lp(this@LauncherActivity, 6))
            addView(agendaScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Ui.dp(this@LauncherActivity, 6)
            })
        }

        pager = Pager(this) { page ->
            updateDots()
            if (page == 1) {
                clearSearch()
                loadAgenda()
            }
        }
        pager.addPage(appsPage)
        pager.addPage(agendaPage)

        // ---- Shared bottom: page dots + Settings ----
        dots = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@LauncherActivity, 8), 0, 0)
        }
        bottomBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(Ui.text(this@LauncherActivity, "⚙ Settings", 14f, Ui.MUTED).apply {
                gravity = Gravity.CENTER
                val p = Ui.dp(this@LauncherActivity, 12)
                setPadding(p * 2, p / 2, p * 2, p)
                setOnClickListener { startActivity(Intent(this@LauncherActivity, OptionsActivity::class.java)) }
            })
        }

        val content = Ui.column(this).apply {
            addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(dots, Ui.lp(this@LauncherActivity))
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
        updateDots()
        renderWidgets()
    }

    // Widgets only update while the host is listening.
    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (e: Exception) { }
    }

    override fun onStop() {
        super.onStop()
        try { widgetHost.stopListening() } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        // Day/night theme changed while away: rebuild everything in the new colours.
        if (themeAtCreate != Theme.paper) {
            recreate()
            return
        }
        AdminSession.end() // back on the home screen = passcode-holder visit is over
        if (!Prefs.isArmed(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        applyLook()
        clearSearch()
        refreshApps()
        refreshDock()
        if (Dock.isStale(this)) Thread {
            Dock.compute(this)
            runOnUiThread { if (!isDestroyed) refreshDock() }
        }.start()
        if (pager.page == 1) loadAgenda()
        if (Weather.enabled(this) && Weather.isStale(this)) loadWeather()
        loadExtras()
        BadgeService.addListener(badgesChanged)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        BadgeService.removeListener(badgesChanged)
        handler.removeCallbacks(ticker)
    }

    /** Pressing Home goes back to the first page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        clearSearch()
        pager.goTo(0)
        scroll.smoothScrollTo(0, 0)
    }

    // The home screen has nowhere to go "back" to; Back returns to page 1.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (pager.page != 0) pager.goTo(0)
    }

    private fun launch(intent: Intent, label: String) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open $label", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Look ----

    /** Applies the Customize settings: background, clock, date and alignment. */
    private fun applyLook() {
        val bg = Look.background(this)
        val photo = if (bg == Look.BG_PHOTO) Look.wallpaperBitmap(this) else null
        Ui.styleSystemBars(this)
        if (bg == Look.BG_PHONE) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Ui.BG))
        }
        wallpaper.setImageBitmap(photo)
        wallpaper.visibility = if (photo != null) View.VISIBLE else View.GONE
        dim.setBackgroundColor(if (bg == Look.BG_BLACK) Color.TRANSPARENT else Look.dimColor(this))

        val centered = Look.centered(this)
        val shadow = bg != Look.BG_BLACK && !Theme.paper
        fun shade(t: TextView) {
            if (shadow) t.setShadowLayer(10f, 0f, 1f, 0xCC000000.toInt()) else t.setShadowLayer(0f, 0f, 0f, 0)
        }
        for (t in listOf<TextView>(clock, date)) {
            t.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
            shade(t)
        }
        header.orientation = if (centered) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        header.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        clock.layoutParams = if (centered)
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        wxBox.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.END
        for (t in listOf(wxMain, wxDetail)) {
            t.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.END
            t.typeface = Look.typeface(this)
            shade(t)
        }
        wxBox.visibility = if (Weather.enabled(this)) View.VISIBLE else View.GONE
        showWeather()
        clock.typeface = Look.clockTypeface(this)
        clock.visibility = if (Look.clock(this) == "hidden") View.GONE else View.VISIBLE
        clock.textSize = if (Look.clock(this) == "small") 48f else 72f
        date.visibility = if (Look.showDate(this)) View.VISIBLE else View.GONE
        date.typeface = Look.typeface(this)
        updateDate()
        for (t in listOf(wordView, gardenView)) {
            t.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
            t.typeface = Look.typeface(this)
            shade(t)
        }
        search.hint = Look.t(this, "Search apps, or try: call, text, timer, ?", "アプリを検索・コマンド")
        agendaTitleText.text = Look.t(this, "Agenda", "予定")
        (bottomBar.getChildAt(0) as? TextView)?.text = Look.t(this, "\u2699 Settings", "\u2699 設定")
        (bottomBar.getChildAt(0) as? TextView)?.let {
            it.setTextColor(if (shadow) Ui.SUBTLE else Ui.MUTED)
            shade(it)
        }
    }

    /** Tiles sit on a slightly see-through card so wallpapers show through. */
    private fun cardColor() = if (Look.background(this) == Look.BG_BLACK) Ui.SURFACE else (Ui.SURFACE and 0x00FFFFFF) or 0xCC000000.toInt()

    private fun updateDots() {
        dots.removeAllViews()
        val size = Ui.dp(this, 7)
        for (i in 0 until pager.pageCount.coerceAtLeast(2)) {
            dots.addView(View(this).apply {
                background = Ui.rounded(if (i == pager.page) Ui.FG else Ui.DOT_OFF, size / 2f)
                setOnClickListener { pager.goTo(i) }
            }, LinearLayout.LayoutParams(size, size).apply {
                leftMargin = size / 2; rightMargin = size / 2
            })
        }
    }

    // ---- Page 1: search, tiles and dock ----

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

    private fun refreshApps() {
        appList.removeAllViews()
        val arrange = { ArrangeActivity.openCategories(this) }
        if (search.text.isNotBlank()) {
            val commands = Commands.suggestions(this, search.text.toString(), commandHooks)
            for (cmd in commands) appList.addView(commandRow(cmd))
            val results = searchResults()
            if (results.isEmpty() && commands.isEmpty()) {
                appList.addView(Ui.text(this, "No allowed app matches that.", 17f, Ui.DIM))
            }
            for (app in results) appList.addView(AppRow.build(this, app, compact = true, onLongClick = arrange))
            return
        }
        val categories = Policy.categories(this)
        if (categories.isEmpty()) {
            appList.addView(Ui.text(this, "No apps yet. Ask the passcode holder to add some.", 18f, Ui.MUTED))
            return
        }
        // Two tiles per row.
        val gap = Ui.dp(this, 10)
        categories.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, cat ->
                row.addView(tile(cat, arrange), LinearLayout.LayoutParams(0, Ui.dp(this, 96), 1f).apply {
                    if (i == 0) rightMargin = gap / 2 else leftMargin = gap / 2
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = gap / 2 })
            appList.addView(row, Ui.lp(this).apply { bottomMargin = gap })
        }
    }

    /** A category tile: name, a few of its app icons, and a calls/texts count. */
    private fun tile(cat: Policy.Category, onLongClick: () -> Unit): View {
        val p = Ui.dp(this, 13)
        val inner = Ui.column(this).apply { setPadding(p, p - 2, p, p - 2) }
        inner.addView(Ui.text(this, Look.categoryLabel(this, cat.name), 15.5f, bold = true).apply {
            typeface = Typeface.create(Look.typeface(this@LauncherActivity), Typeface.BOLD)
            maxLines = 2
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val icons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val style = Look.icons(this)
        val shown = if (cat.apps.size > 4) cat.apps.take(3) else cat.apps.take(4)
        val iconSize = Ui.dp(this, 24)
        if (style == "none") {
            icons.addView(Ui.text(this, "${cat.apps.size} ${if (cat.apps.size == 1) "app" else "apps"}", 13f, Ui.DIM))
        } else {
            for (app in shown) {
                icons.addView(ImageView(this).apply {
                    setImageDrawable(app.icon)
                    if (style == "grey") colorFilter = grey
                }, LinearLayout.LayoutParams(iconSize, iconSize).apply { rightMargin = Ui.dp(this@LauncherActivity, 6) })
            }
            if (cat.apps.size > 4) {
                icons.addView(Ui.text(this, "+${cat.apps.size - 3}", 11f, Ui.SUBTLE, bold = true).apply {
                    gravity = Gravity.CENTER
                    background = Ui.rounded(Ui.CHIP, iconSize / 2f)
                }, LinearLayout.LayoutParams(iconSize, iconSize))
            }
        }
        inner.addView(icons)

        val card = FrameLayout(this).apply {
            background = Ui.rounded(cardColor(), Ui.dp(this@LauncherActivity, 18).toFloat())
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@LauncherActivity, CategoryActivity::class.java).putExtra(CategoryActivity.EXTRA_NAME, cat.name))
            }
            setOnLongClickListener { onLongClick(); true }
            addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val waiting = cat.apps.sumOf { BadgeService.counts[it.pkg] ?: 0 }
        if (waiting > 0) {
            card.addView(Ui.countBadge(this, waiting), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END
            ).apply { topMargin = Ui.dp(this@LauncherActivity, 10); rightMargin = Ui.dp(this@LauncherActivity, 10) })
        }
        return card
    }

    /** The 4 most-used apps, above Settings. */
    private fun refreshDock() {
        dock.removeAllViews()
        val apps = Dock.apps(this)
        dock.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        val circle = Ui.dp(this, 56)
        val icon = Ui.dp(this, 34)
        val style = Look.icons(this)
        for (app in apps) {
            val bubble = FrameLayout(this).apply {
                background = Ui.rounded(cardColor(), circle / 2f)
                addView(ImageView(this@LauncherActivity).apply {
                    setImageDrawable(app.icon)
                    if (style == "grey") colorFilter = grey
                }, FrameLayout.LayoutParams(icon, icon, Gravity.CENTER))
            }
            val cell = FrameLayout(this)
            val col = Ui.column(this).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(bubble, LinearLayout.LayoutParams(circle, circle))
                addView(Ui.text(this@LauncherActivity, app.label, 11.5f, Ui.SUBTLE).apply {
                    isSingleLine = true
                    gravity = Gravity.CENTER
                    if (Look.background(this@LauncherActivity) != Look.BG_BLACK && !Theme.paper) setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt())
                }, Ui.lp(this@LauncherActivity, 5))
                isClickable = true
                setOnClickListener { launch(app.intent, app.label) }
                // Long-press: choose the dock apps.
                setOnLongClickListener {
                    startActivity(Intent(this@LauncherActivity, DockSettingsActivity::class.java))
                    true
                }
            }
            cell.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val count = BadgeService.counts[app.pkg] ?: 0
            if (count > 0) {
                cell.addView(Ui.countBadge(this, count), FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply { leftMargin = circle / 2 })
            }
            dock.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    // ---- Page 2: agenda ----

    // ---- Command bar ----

    private val commandHooks by lazy {
        Commands.Hooks(
            openAgenda = { clearSearch(); pager.goTo(1) },
            weather = { onWeatherTap() },
        )
    }

    private fun runCommand(cmd: Commands.Suggestion) {
        if (cmd.needsContacts) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.CALL_PHONE), REQ_CONTACTS)
            return
        }
        cmd.run(this)
        clearSearch()
    }

    private fun commandRow(cmd: Commands.Suggestion): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        val p = Ui.dp(this@LauncherActivity, 12)
        setPadding(p, p, p, p)
        background = Ui.rounded(cardColor(), Ui.dp(this@LauncherActivity, 14).toFloat())
        isClickable = true
        setOnClickListener { runCommand(cmd) }
        addView(Ui.text(this@LauncherActivity, cmd.icon, 20f), LinearLayout.LayoutParams(Ui.dp(this@LauncherActivity, 36), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(Ui.text(this@LauncherActivity, cmd.label, 17f).apply { typeface = Look.typeface(this@LauncherActivity) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }.also { it.layoutParams = Ui.lp(this).apply { bottomMargin = Ui.dp(this@LauncherActivity, 8) } }

    // ---- Date, word of the day, garden, morning/evening card ----

    private fun updateDate() {
        val now = java.time.LocalDate.now()
        date.text = if (Look.japanese(this)) {
            now.format(java.time.format.DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
        } else {
            now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
        }
        val word = if (Look.wordOfDay(this)) Vocab.today(this) else null
        wordView.visibility = if (word != null) View.VISIBLE else View.GONE
        if (word != null) wordView.text = "${word.word}（${word.reading}）· ${word.meaning}"
    }

    /** Garden, card and word list load off the main thread; at most every 10 minutes. */
    private fun loadExtras() {
        cardsAt = System.currentTimeMillis()
        Thread {
            if (Vocab.needsRefresh(this)) Vocab.refresh(this)
            val garden = if (Look.garden(this)) try { Garden.compute(this) } catch (e: Exception) { null } else null
            val card = try { DayCard.build(this) } catch (e: Exception) { null }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                updateDate()
                showGarden(garden)
                showCard(card)
            }
        }.start()
    }

    private fun showGarden(g: Garden.State?) {
        if (g == null) {
            gardenView.visibility = View.GONE
            return
        }
        val streak = if (g.streak > 0) "  " + Look.t(this, "${g.streak}-day streak", "${g.streak}日連続") else ""
        gardenView.text = g.plants + streak
        gardenView.visibility = View.VISIBLE
        gardenView.setOnClickListener {
            val msg = Look.t(this,
                "Today: ${Ui.formatDuration(g.todayMinutes * 60_000)} of your ${Ui.formatDuration(g.goal * 60_000L)} goal",
                "今日: ${Ui.formatDuration(g.todayMinutes * 60_000)}（目標 ${Ui.formatDuration(g.goal * 60_000L)}）")
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun showCard(card: DayCard.Card?) {
        cardBox.removeAllViews()
        if (card == null) return
        val p = Ui.dp(this, 14)
        val box = LinearLayout(this).apply {
            setPadding(p, p - 2, Ui.dp(this@LauncherActivity, 4), p)
            background = Ui.rounded(cardColor(), Ui.dp(this@LauncherActivity, 18).toFloat())
            isClickable = true
            setOnClickListener { pager.goTo(1) }
        }
        val texts = Ui.column(this)
        texts.addView(Ui.text(this, card.title, 17f, bold = true).apply {
            typeface = android.graphics.Typeface.create(Look.typeface(this@LauncherActivity), android.graphics.Typeface.BOLD)
        })
        for (line in card.lines) texts.addView(Ui.text(this, line, 14f, Ui.SUBTLE).apply {
            typeface = Look.typeface(this@LauncherActivity)
        }, Ui.lp(this, 3))
        box.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(Ui.text(this, "✕", 16f, Ui.MUTED).apply {
            setPadding(Ui.dp(this@LauncherActivity, 10), 0, Ui.dp(this@LauncherActivity, 10), Ui.dp(this@LauncherActivity, 10))
            setOnClickListener {
                DayCard.dismiss(this@LauncherActivity, card.key)
                cardBox.removeAllViews()
            }
        })
        cardBox.addView(box, Ui.lp(this).apply { bottomMargin = Ui.dp(this@LauncherActivity, 12) })
    }

    private fun loadAgenda(force: Boolean = false) {
        if (agendaLoading) return
        agendaLoading = true
        if (agendaList.childCount == 0) agendaList.addView(Ui.text(this, "Loading…", 15f, Ui.MUTED), Ui.lp(this, 16))
        Thread {
            val result = Agenda.load(this, forceCanvas = force)
            runOnUiThread {
                agendaLoading = false
                if (!isDestroyed) showAgenda(result)
            }
        }.start()
    }

    private fun showAgenda(r: Agenda.Result) {
        agendaList.removeAllViews()
        val shadow = Look.background(this) != Look.BG_BLACK && !Theme.paper

        // Setup prompts first, so it's obvious how to fill this page.
        if (!Agenda.hasCalendarPermission(this)) {
            agendaList.addView(prompt("Show your calendar", "Tap to allow Lite Phone to read your calendar events.") {
                requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
            }, Ui.lp(this, 12))
        }
        if (!Agenda.canvasConnected(this) && !AgendaWidgets.hasCanvasWidget(this)) {
            agendaList.addView(prompt("Connect Canvas", "Tap to show assignments that are due.") {
                startActivity(Intent(this, CanvasSetupActivity::class.java))
            }, Ui.lp(this, 12))
        }
        if (r.canvasError != null) {
            agendaList.addView(Ui.text(this, "Canvas: ${r.canvasError}", 13f, Ui.WARN), Ui.lp(this, 12))
        }

        if (r.items.isEmpty()) {
            if (Agenda.hasCalendarPermission(this) || Agenda.canvasConnected(this)) {
                agendaList.addView(Ui.text(this, "Nothing in the next 7 days.", 16f, Ui.DIM), Ui.lp(this, 16))
            }
            return
        }

        val today = LocalDate.now()
        val dayFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())
        val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        var lastDay: LocalDate? = null
        for (item in r.items) {
            if (item.day != lastDay) {
                lastDay = item.day
                val label = when (item.day) {
                    today -> "${Look.t(this, "TODAY", "今日")} · ${item.day.format(dayFmt).uppercase()}"
                    today.plusDays(1) -> "${Look.t(this, "TOMORROW", "明日")} · ${item.day.format(dayFmt).uppercase()}"
                    else -> item.day.format(DateTimeFormatter.ofPattern("EEEE · MMM d", Locale.getDefault())).uppercase()
                }
                agendaList.addView(Ui.text(this, label, 13f, Ui.DIM, bold = true).apply {
                    letterSpacing = 0.06f
                    if (shadow) setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt())
                }, Ui.lp(this, 18))
            }
            agendaList.addView(agendaRow(item, if (item.allDay) "All day" else timeFmt.format(Date(item.time)), shadow))
        }
    }

    private fun agendaRow(item: Agenda.Item, time: String, shadow: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = Ui.dp(this@LauncherActivity, 9)
            setPadding(0, p, 0, p)
            isClickable = true
            setOnClickListener {
                val open = item.open
                when {
                    open != null && tryStart(open) -> {}
                    item.isCanvas -> packageManager.getLaunchIntentForPackage(Agenda.CANVAS_PKG)?.let { tryStart(it) }
                    else -> {}
                }
            }
        }
        row.addView(Ui.text(this, time, 13f, Ui.DIM).apply {
            if (shadow) setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt())
        }, LinearLayout.LayoutParams(Ui.dp(this, 70), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(View(this).apply { background = Ui.rounded(item.color, Ui.dp(this@LauncherActivity, 2).toFloat()) },
            LinearLayout.LayoutParams(Ui.dp(this, 3), ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = Ui.dp(this@LauncherActivity, 12) })
        val texts = Ui.column(this)
        val titleLine = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleLine.addView(Ui.text(this, item.title, 16f).apply {
            maxLines = 2
            if (shadow) setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleLine.addView(Ui.text(this, if (item.isCanvas) "DUE" else "CAL", 10.5f, Ui.BG, bold = true).apply {
            setPadding(Ui.dp(this@LauncherActivity, 6), Ui.dp(this@LauncherActivity, 1), Ui.dp(this@LauncherActivity, 6), Ui.dp(this@LauncherActivity, 1))
            background = Ui.rounded(if (item.isCanvas) 0xFFFF8A65.toInt() else 0xFF7FB2FF.toInt(), Ui.dp(this@LauncherActivity, 6).toFloat())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = Ui.dp(this@LauncherActivity, 8)
        })
        texts.addView(titleLine)
        if (item.subtitle.isNotBlank()) texts.addView(Ui.text(this, item.subtitle, 12.5f, Ui.DIM).apply {
            maxLines = 1
            if (shadow) setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt())
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    // ---- Widgets on the Agenda page ----

    private fun renderWidgets() {
        widgetBox.removeAllViews()
        val widthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 48
        for (id in AgendaWidgets.ids(this)) {
            val info = widgetManager.getAppWidgetInfo(id)
            if (info == null) {
                AgendaWidgets.remove(this, id)
                continue
            }
            val hDp = AgendaWidgets.heightDp(this, id)
            val view = try {
                widgetHost.createView(applicationContext, id, info)
            } catch (e: Exception) {
                continue
            }
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), hDp.toFloat())))
                } else {
                    @Suppress("DEPRECATION")
                    view.updateAppWidgetSize(null, widthDp, hDp, widthDp, hDp)
                }
            } catch (e: Exception) { }
            widgetBox.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, hDp)).apply {
                bottomMargin = Ui.dp(this@LauncherActivity, 10)
            })
        }
    }

    /** Add a widget, or resize / remove one you already have. */
    private fun widgetMenu() {
        val ids = AgendaWidgets.ids(this)
        val names = ids.map { id -> widgetManager.getAppWidgetInfo(id)?.let { AgendaWidgets.label(this, it) } ?: "Widget" }
        val options = listOf("Add a widget…") + names.map { "Change: $it" }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Agenda widgets")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) pickWidget() else editWidget(ids[which - 1], names[which - 1])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun editWidget(id: Int, name: String) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(name)
            .setItems(arrayOf("Taller", "Shorter", "Remove")) { _, which ->
                when (which) {
                    0 -> AgendaWidgets.resize(this, id, bigger = true)
                    1 -> AgendaWidgets.resize(this, id, bigger = false)
                    2 -> {
                        widgetHost.deleteAppWidgetId(id)
                        AgendaWidgets.remove(this, id)
                    }
                }
                renderWidgets()
                loadAgenda()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickWidget() {
        val choices = AgendaWidgets.available(this)
        if (choices.isEmpty()) {
            Toast.makeText(this, "None of your allowed apps have widgets.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Add a widget")
            .setItems(choices.map { AgendaWidgets.label(this, it) }.toTypedArray()) { _, which -> addWidget(choices[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrSave(id, info)
        } else {
            // Android asks once whether Lite Phone may show widgets. That screen mentions
            // Lite Phone, so the Settings lock stands down for it.
            pendingWidgetId = id
            Policy.allowInstallerBriefly(this)
            try {
                startActivityForResult(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
                    REQ_BIND_WIDGET
                )
            } catch (e: Exception) {
                widgetHost.deleteAppWidgetId(id)
                pendingWidgetId = -1
                Toast.makeText(this, "Couldn't add that widget", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Some widgets have their own setup screen (pick a calendar, a course, etc.). */
    private fun configureOrSave(id: Int, info: AppWidgetProviderInfo?) {
        if (info?.configure != null) {
            pendingWidgetId = id
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE_WIDGET, null)
                return
            } catch (e: Exception) { }
        }
        pendingWidgetId = -1
        saveWidget(id)
    }

    private fun saveWidget(id: Int) {
        AgendaWidgets.add(this, id)
        renderWidgets()
        loadAgenda()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BIND_WIDGET && requestCode != REQ_CONFIGURE_WIDGET) return
        val id = pendingWidgetId
        if (id == -1) return
        pendingWidgetId = -1
        if (resultCode != RESULT_OK) {
            widgetHost.deleteAppWidgetId(id)
            return
        }
        if (requestCode == REQ_BIND_WIDGET) configureOrSave(id, widgetManager.getAppWidgetInfo(id)) else saveWidget(id)
    }

    private fun tryStart(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (e: Exception) {
        false
    }

    private fun prompt(title: String, body: String, onClick: () -> Unit): View = Ui.column(this).apply {
        val p = Ui.dp(this@LauncherActivity, 14)
        setPadding(p, p, p, p)
        background = Ui.rounded(cardColor(), Ui.dp(this@LauncherActivity, 16).toFloat())
        addView(Ui.text(this@LauncherActivity, title, 16f, Ui.ACCENT, bold = true))
        addView(Ui.text(this@LauncherActivity, body, 13.5f, Ui.DIM), Ui.lp(this@LauncherActivity, 2))
        isClickable = true
        setOnClickListener { onClick() }
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
        wxMain.text = "$icon ${now.temp}°"
        val place = if (now.place.isNotBlank()) "\n${now.place}" else ""
        wxDetail.text = "$words · H ${now.hi}° L ${now.lo}°$place"
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

    /** Tap: set up weather if needed; otherwise refresh it and open a weather app. */
    private fun onWeatherTap() {
        if (Weather.needsSetup(this)) {
            if (Weather.usesCity(this)) {
                startActivity(Intent(this, CustomizeActivity::class.java))
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            }
            return
        }
        loadWeather(force = true)
        val app = Weather.WEATHER_APPS.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            ?: Policy.allowedEntries(this).firstOrNull { it.label.contains("weather", ignoreCase = true) }?.intent
        if (app != null) {
            startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            Toast.makeText(this, "Updating weather… (install Samsung Weather for the full forecast)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_LOCATION -> if (Weather.hasLocationPermission(this)) {
                loadWeather(force = true)
            } else {
                Toast.makeText(this, "No location, so no weather. You can pick a city in Customize.", Toast.LENGTH_LONG).show()
            }
            REQ_CALENDAR -> loadAgenda()
            REQ_CONTACTS -> refreshApps()
        }
    }

    // ---- Status banner ----

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
                "🌐 Web pass until ${WebPass.endText(this)}. Tap to open the browser." to Ui.ACCENT
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
