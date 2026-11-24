package com.litephone.launcher

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One-time codes the passcode holder can text back to unlock the phone.
 * Codes are shown once when made; only salted hashes are kept on the phone.
 */
object UnlockCodes {
    private const val KEY_CODES = "unlock_codes"
    private const val KEY_SALT = "unlock_codes_salt"
    private const val KEY_MINUTES = "unlock_code_minutes"
    const val LENGTH = 8

    fun remaining(ctx: Context): Int = hashes(ctx).size

    fun minutes(ctx: Context): Int = Prefs.get(ctx).getInt(KEY_MINUTES, 15)

    fun setMinutes(ctx: Context, minutes: Int) {
        Prefs.get(ctx).edit().putInt(KEY_MINUTES, minutes).apply()
    }

    /** Makes a fresh set of codes (old ones stop working) and returns them for display. */
    fun generate(ctx: Context, count: Int = 10): List<String> {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val codes = LinkedHashSet<String>()
        while (codes.size < count) codes.add(String.format("%08d", random.nextInt(100_000_000)))
        Prefs.get(ctx).edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_CODES, codes.joinToString(",") { hash(salt, it) })
            .apply()
        return codes.toList()
    }

    /** True (and the code is used up) if [input] is one of the unused codes. */
    fun use(ctx: Context, input: String): Boolean {
        val code = input.filter { it.isDigit() }
        if (code.length != LENGTH) return false
        val sp = Prefs.get(ctx)
        val salt = sp.getString(KEY_SALT, null) ?: return false
        val h = hash(Base64.decode(salt, Base64.NO_WRAP), code)
        val list = hashes(ctx)
        if (h !in list) return false
        sp.edit().putString(KEY_CODES, (list - h).joinToString(",")).apply()
        return true
    }

    private fun hashes(ctx: Context): List<String> =
        (Prefs.get(ctx).getString(KEY_CODES, null) ?: "").split(',').filter { it.isNotBlank() }

    private fun hash(salt: ByteArray, code: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return b64(md.digest(code.toByteArray()))
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}
