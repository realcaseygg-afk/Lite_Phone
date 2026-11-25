package com.litephone.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/** Home screen appearance chosen in Customize. Purely cosmetic: none of it needs the passcode. */
object Look {
    private const val K_BG = "look_bg"
    private const val K_DIM = "look_dim"
    private const val K_ACCENT = "look_accent"
    private const val K_ICONS = "look_icons"
    private const val K_SIZE = "look_size"
    private const val K_ALIGN = "look_align"
    private const val K_FONT = "look_font"
    private const val K_CLOCK = "look_clock"
    private const val K_DATE = "look_date"
    private const val K_THEME = "look_theme"
    private const val K_JAPANESE = "look_japanese"
    private const val K_WORD = "look_word"
    private const val K_GARDEN = "look_garden"
    private const val K_GOAL = "look_garden_goal"
    private const val K_CARDS = "look_cards"

    const val BG_BLACK = "black"
    const val BG_PHONE = "phone"   // the phone's own wallpaper shows through
    const val BG_PHOTO = "photo"   // a photo picked in Customize

    val ACCENTS = listOf(
        0xFF7FB2FF.toInt(), // blue
        0xFF66D19E.toInt(), // green
        0xFFFFB74D.toInt(), // orange
        0xFFFF8FAB.toInt(), // pink
        0xFFB39DFF.toInt(), // purple
        0xFFFFFFFF.toInt(), // white
    )

    private fun str(ctx: Context, key: String, def: String) = Prefs.get(ctx).getString(key, null) ?: def
    private fun put(ctx: Context, key: String, value: String) = Prefs.get(ctx).edit().putString(key, value).apply()

    fun background(ctx: Context) = str(ctx, K_BG, BG_BLACK)
    fun setBackground(ctx: Context, v: String) = put(ctx, K_BG, v)

    /** 0 = none … 3 = strong darkening over a wallpaper, so text stays readable. */
    fun dim(ctx: Context): Int = Prefs.get(ctx).getInt(K_DIM, 2)
    fun setDim(ctx: Context, v: Int) = Prefs.get(ctx).edit().putInt(K_DIM, v).apply()
    fun dimColor(ctx: Context): Int {
        val alpha = intArrayOf(0, 0x4D, 0x80, 0xB3)[dim(ctx).coerceIn(0, 3)]
        // On the paper look the wallpaper is washed out with cream instead of darkened.
        return (alpha shl 24) or (if (Theme.paper) 0xF3EEE4 else 0)
    }

    // ---- Theme, language and home extras ----

    fun theme(ctx: Context) = str(ctx, K_THEME, "dark")       // dark | paper | auto
    fun setTheme(ctx: Context, v: String) {
        put(ctx, K_THEME, v)
        Theme.invalidate()
    }

    fun japanese(ctx: Context) = Prefs.get(ctx).getBoolean(K_JAPANESE, false)
    fun setJapanese(ctx: Context, v: Boolean) = Prefs.get(ctx).edit().putBoolean(K_JAPANESE, v).apply()

    /** English or Japanese text for the home screen, depending on Japanese mode. */
    fun t(ctx: Context, en: String, jp: String) = if (japanese(ctx)) jp else en

    private val CATEGORY_JP = mapOf(
        "Phone & Messages" to "電話・メッセージ", "My Apps" to "マイアプリ", "School" to "学校",
        "Notes & Tasks" to "ノート・タスク", "Sign-in & Security" to "ログイン・セキュリティ", "AI" to "AI",
        "Music & Books" to "音楽・本", "Camera & Photos" to "カメラ・写真", "Banking & Money" to "お金",
        "Food" to "食べ物", "Shopping" to "買い物", "Everyday" to "毎日", "Other" to "その他",
    )

    fun categoryLabel(ctx: Context, name: String) = if (japanese(ctx)) CATEGORY_JP[name] ?: name else name

    fun wordOfDay(ctx: Context) = Prefs.get(ctx).getBoolean(K_WORD, true)
    fun setWordOfDay(ctx: Context, v: Boolean) = Prefs.get(ctx).edit().putBoolean(K_WORD, v).apply()

    fun garden(ctx: Context) = Prefs.get(ctx).getBoolean(K_GARDEN, true)
    fun setGarden(ctx: Context, v: Boolean) = Prefs.get(ctx).edit().putBoolean(K_GARDEN, v).apply()

    /** Daily screen-time goal for the garden, in minutes. */
    fun gardenGoal(ctx: Context) = Prefs.get(ctx).getInt(K_GOAL, 180)
    fun setGardenGoal(ctx: Context, minutes: Int) = Prefs.get(ctx).edit().putInt(K_GOAL, minutes).apply()

    fun dayCards(ctx: Context) = Prefs.get(ctx).getBoolean(K_CARDS, true)
    fun setDayCards(ctx: Context, v: Boolean) = Prefs.get(ctx).edit().putBoolean(K_CARDS, v).apply()

    fun accent(ctx: Context): Int = Prefs.get(ctx).getInt(K_ACCENT, ACCENTS[0])
    fun setAccent(ctx: Context, color: Int) {
        Prefs.get(ctx).edit().putInt(K_ACCENT, color).apply()
        Ui.ACCENT = color
    }

    fun icons(ctx: Context) = str(ctx, K_ICONS, "grey")       // grey | color | none
    fun setIcons(ctx: Context, v: String) = put(ctx, K_ICONS, v)

    fun size(ctx: Context) = str(ctx, K_SIZE, "medium")       // small | medium | large
    fun setSize(ctx: Context, v: String) = put(ctx, K_SIZE, v)
    fun labelSp(ctx: Context, compact: Boolean): Float {
        val base = when (size(ctx)) { "small" -> 20f; "large" -> 30f; else -> 25f }
        return if (compact) base * 0.86f else base
    }

    fun centered(ctx: Context) = str(ctx, K_ALIGN, "left") == "center"
    fun setAlign(ctx: Context, v: String) = put(ctx, K_ALIGN, v)

    fun font(ctx: Context) = str(ctx, K_FONT, "default")      // default | light | serif | mono | condensed
    fun setFont(ctx: Context, v: String) = put(ctx, K_FONT, v)
    fun typeface(ctx: Context): Typeface = when (font(ctx)) {
        "default" -> if (Theme.paper) Typeface.SERIF else Typeface.DEFAULT
        "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        else -> Typeface.DEFAULT
    }
    fun clockTypeface(ctx: Context): Typeface = when {
        font(ctx) != "default" -> typeface(ctx)
        Theme.paper -> Typeface.create("serif", Typeface.NORMAL)
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    fun clock(ctx: Context) = str(ctx, K_CLOCK, "large")      // large | small | hidden
    fun setClock(ctx: Context, v: String) = put(ctx, K_CLOCK, v)

    fun showDate(ctx: Context) = Prefs.get(ctx).getBoolean(K_DATE, true)
    fun setShowDate(ctx: Context, v: Boolean) = Prefs.get(ctx).edit().putBoolean(K_DATE, v).apply()

    fun reset(ctx: Context) {
        Prefs.get(ctx).edit().apply {
            listOf(K_BG, K_DIM, K_ACCENT, K_ICONS, K_SIZE, K_ALIGN, K_FONT, K_CLOCK, K_DATE, K_THEME).forEach { remove(it) }
        }.apply()
        Ui.ACCENT = ACCENTS[0]
        Theme.invalidate()
        wallpaperFile(ctx).delete()
    }

    // ---- Wallpaper photo ------------------------------------------------------

    fun wallpaperFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    /** Copies a picked photo into the app, shrunk to screen size and turned upright. */
    fun saveWallpaper(ctx: Context, uri: Uri): Boolean = try {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val dm = ctx.resources.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("not an image")
        val rotation = cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation != 0f) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        }
        wallpaperFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Exception) {
        false
    }

    private var cached: Pair<Long, Bitmap>? = null

    fun wallpaperBitmap(ctx: Context): Bitmap? {
        val f = wallpaperFile(ctx)
        if (!f.exists()) return null
        cached?.let { if (it.first == f.lastModified()) return it.second }
        val bmp = BitmapFactory.decodeFile(f.path) ?: return null
        cached = f.lastModified() to bmp
        return bmp
    }
}
