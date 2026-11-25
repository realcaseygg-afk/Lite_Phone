package com.litephone.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Current weather for the home screen, from Open-Meteo (free, no account).
 * Only a rounded location (about 1 km) is sent. Results are cached for 30 minutes.
 */
object Weather {
    private const val K_ON = "wx_on"
    private const val K_MODE = "wx_mode"            // "location" | "city"
    private const val K_UNIT = "wx_unit"            // "auto" | "f" | "c"
    private const val K_CITY = "wx_city"
    private const val K_CITY_LAT = "wx_city_lat"
    private const val K_CITY_LON = "wx_city_lon"
    private const val K_CACHE = "wx_cache"          // temp|code|isDay|hi|lo|place|unit|time
    private const val STALE_MS = 30 * 60 * 1000L
    private const val K_RISE = "wx_sunrise"
    private const val K_SET = "wx_sunset"

    /** Sunrise and sunset as minutes after midnight, once the weather has loaded. */
    fun sunTimes(ctx: Context): Pair<Int, Int>? {
        val sp = Prefs.get(ctx)
        if (!sp.contains(K_RISE) || !sp.contains(K_SET)) return null
        return sp.getInt(K_RISE, 420) to sp.getInt(K_SET, 1140)
    }

    val WEATHER_APPS = listOf(
        "com.sec.android.daemonapp",        // Samsung Weather
        "com.weather.Weather",              // The Weather Channel
        "com.accuweather.android",          // AccuWeather
    )

    class Now(
        val temp: Int, val code: Int, val isDay: Boolean,
        val hi: Int, val lo: Int, val place: String, val unit: String, val at: Long,
    )

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var fetching = false
    private var lastAttempt = 0L

    // ---- Settings ----

    fun enabled(ctx: Context) = Prefs.get(ctx).getBoolean(K_ON, true)
    fun setEnabled(ctx: Context, on: Boolean) = Prefs.get(ctx).edit().putBoolean(K_ON, on).apply()

    fun usesCity(ctx: Context) = Prefs.get(ctx).getString(K_MODE, "location") == "city"
    fun setUseCity(ctx: Context, city: Boolean) {
        Prefs.get(ctx).edit().putString(K_MODE, if (city) "city" else "location").remove(K_CACHE).apply()
    }
    fun cityName(ctx: Context): String? = Prefs.get(ctx).getString(K_CITY, null)

    fun unit(ctx: Context) = Prefs.get(ctx).getString(K_UNIT, "auto") ?: "auto"
    fun setUnit(ctx: Context, u: String) = Prefs.get(ctx).edit().putString(K_UNIT, u).remove(K_CACHE).apply()

    private fun fahrenheit(ctx: Context): Boolean = when (unit(ctx)) {
        "f" -> true
        "c" -> false
        else -> Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")
    }

    fun hasLocationPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** True when weather can't load until the user grants location or picks a city. */
    fun needsSetup(ctx: Context) =
        if (usesCity(ctx)) cityName(ctx) == null else !hasLocationPermission(ctx)

    // ---- Cache ----

    fun cached(ctx: Context): Now? {
        val parts = Prefs.get(ctx).getString(K_CACHE, null)?.split('|') ?: return null
        if (parts.size < 8) return null
        return try {
            Now(parts[0].toInt(), parts[1].toInt(), parts[2] == "1", parts[3].toInt(), parts[4].toInt(),
                parts[5], parts[6], parts[7].toLong())
        } catch (e: Exception) {
            null
        }
    }

    fun isStale(ctx: Context): Boolean {
        val c = cached(ctx) ?: return true
        return System.currentTimeMillis() - c.at > STALE_MS
    }

    private fun save(ctx: Context, n: Now) {
        val s = listOf(n.temp, n.code, if (n.isDay) 1 else 0, n.hi, n.lo, n.place.replace('|', ' '), n.unit, n.at)
            .joinToString("|")
        Prefs.get(ctx).edit().putString(K_CACHE, s).apply()
    }

    // ---- Loading ----

    /** Refreshes in the background; [done] runs on the main thread with the result or an error. */
    fun refresh(ctx: Context, force: Boolean = false, done: (Now?, String?) -> Unit) {
        if (fetching) return
        if (needsSetup(ctx)) { done(null, "setup"); return }
        // Don't retry more than every 2 minutes (e.g. while offline).
        val now = System.currentTimeMillis()
        if (!force && now - lastAttempt < 2 * 60_000L) return
        lastAttempt = now
        fetching = true
        val app = ctx.applicationContext
        val finish: (Now?, String?) -> Unit = { n, err ->
            fetching = false
            main.post { done(n, err) }
        }
        if (usesCity(app)) {
            val sp = Prefs.get(app)
            val lat = sp.getString(K_CITY_LAT, null)?.toDoubleOrNull()
            val lon = sp.getString(K_CITY_LON, null)?.toDoubleOrNull()
            if (lat == null || lon == null) { finish(null, "setup"); return }
            Thread { fetchAndSave(app, lat, lon, cityName(app) ?: "", finish) }.start()
        } else {
            locate(app) { loc ->
                if (loc == null) finish(null, "Couldn't find your location")
                else Thread {
                    fetchAndSave(app, loc.latitude, loc.longitude, placeName(app, loc.latitude, loc.longitude), finish)
                }.start()
            }
        }
    }

    private fun fetchAndSave(ctx: Context, lat: Double, lon: Double, place: String, finish: (Now?, String?) -> Unit) {
        try {
            val f = fahrenheit(ctx)
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${round2(lat)}&longitude=${round2(lon)}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                "&timezone=auto&forecast_days=1" +
                if (f) "&temperature_unit=fahrenheit" else ""
            val json = JSONObject(get(url))
            val cur = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val n = Now(
                temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
                code = cur.optInt("weather_code", 0),
                isDay = cur.optInt("is_day", 1) == 1,
                hi = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                lo = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                place = place,
                unit = if (f) "F" else "C",
                at = System.currentTimeMillis(),
            )
            save(ctx, n)
            // Sunrise/sunset (local "2026-11-25T06:58") for the Auto day/night theme.
            fun minutes(key: String) = daily.optJSONArray(key)?.optString(0)?.substringAfter('T')
                ?.split(':')?.let { it[0].toInt() * 60 + it[1].toInt() }
            try {
                val rise = minutes("sunrise")
                val set = minutes("sunset")
                if (rise != null && set != null) {
                    Prefs.get(ctx).edit().putInt(K_RISE, rise).putInt(K_SET, set).apply()
                    Theme.invalidate()
                }
            } catch (e: Exception) { }
            finish(n, null)
        } catch (e: Exception) {
            finish(null, "Weather unavailable")
        }
    }

    /** Looks up a typed city name. [done] gets the saved name, or null if not found. */
    fun setCity(ctx: Context, query: String, done: (String?) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val name = try {
                val q = URLEncoder.encode(query.trim(), "UTF-8")
                val res = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&format=json"))
                    .optJSONArray("results")?.optJSONObject(0)
                if (res == null) null else {
                    val label = listOfNotNull(
                        res.optString("name").takeIf { it.isNotBlank() },
                        res.optString("admin1").takeIf { it.isNotBlank() },
                    ).joinToString(", ")
                    Prefs.get(app).edit()
                        .putString(K_CITY, label)
                        .putString(K_CITY_LAT, res.getDouble("latitude").toString())
                        .putString(K_CITY_LON, res.getDouble("longitude").toString())
                        .putString(K_MODE, "city")
                        .remove(K_CACHE)
                        .apply()
                    label
                }
            } catch (e: Exception) {
                null
            }
            main.post { done(name) }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun locate(ctx: Context, cb: (Location?) -> Unit) {
        if (!hasLocationPermission(ctx)) { cb(null); return }
        val lm = ctx.getSystemService(LocationManager::class.java) ?: run { cb(null); return }
        val providers = listOfNotNull(
            if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).filter { try { lm.isProviderEnabled(it) } catch (e: Exception) { false } }
        val last = (providers + LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 60 * 60 * 1000L) { cb(last); return }
        if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            try {
                lm.getCurrentLocation(providers.first(), null, ctx.mainExecutor) { loc -> cb(loc ?: last) }
                return
            } catch (e: Exception) { }
        }
        cb(last)
    }

    @Suppress("DEPRECATION")
    private fun placeName(ctx: Context, lat: Double, lon: Double): String = try {
        if (!Geocoder.isPresent()) "" else Geocoder(ctx).getFromLocation(lat, lon, 1)?.firstOrNull()?.let {
            it.locality ?: it.subAdminArea ?: it.adminArea
        } ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun round2(v: Double) = String.format(Locale.US, "%.2f", v)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // ---- Display ----

    /** Symbol and words for an Open-Meteo (WMO) weather code. */
    fun describe(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
        0 -> (if (isDay) "☀️" else "🌙") to "Clear"
        1, 2 -> (if (isDay) "⛅" else "☁️") to "Partly cloudy"
        3 -> "☁️" to "Cloudy"
        45, 48 -> "🌫️" to "Fog"
        51, 53, 55, 56, 57 -> "🌦️" to "Drizzle"
        61, 63, 65, 66, 67 -> "🌧️" to "Rain"
        71, 73, 75, 77 -> "🌨️" to "Snow"
        80, 81, 82 -> "🌦️" to "Showers"
        85, 86 -> "🌨️" to "Snow showers"
        95, 96, 99 -> "⛈️" to "Thunderstorm"
        else -> "🌡️" to "Weather"
    }
}
