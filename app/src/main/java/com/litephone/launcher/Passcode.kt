package com.litephone.launcher

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores only a salted PBKDF2 hash of the passcode, never the passcode itself. */
object Passcode {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    private const val KEY_SALT = "pin_salt"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_FAILS = "pin_fails"
    private const val ITERATIONS = 100_000
    private const val FREE_TRIES = 5
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 60 * 60 * 1000L

    private val lockout = Deadline("pin_lock_until", "pin_lock_boot")

    fun isSet(ctx: Context): Boolean = Prefs.get(ctx).contains(KEY_HASH)

    fun set(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        Prefs.get(ctx).edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_HASH, b64(derive(pin, salt)))
            .putInt(KEY_FAILS, 0)
            .apply()
        lockout.clear(ctx)
    }

    fun check(ctx: Context, pin: String): Boolean {
        val sp = Prefs.get(ctx)
        val salt = sp.getString(KEY_SALT, null) ?: return false
        val hash = sp.getString(KEY_HASH, null) ?: return false
        val ok = MessageDigest.isEqual(derive(pin, unb64(salt)), unb64(hash))
        if (ok) {
            sp.edit().putInt(KEY_FAILS, 0).apply()
            lockout.clear(ctx)
        } else {
            val fails = sp.getInt(KEY_FAILS, 0) + 1
            sp.edit().putInt(KEY_FAILS, fails).apply()
            if (fails >= FREE_TRIES) lockout.set(ctx, lockoutLength(fails))
        }
        return ok
    }

    /** Milliseconds until another guess is allowed (0 = allowed now). */
    fun lockoutRemainingMs(ctx: Context): Long {
        val fails = Prefs.get(ctx).getInt(KEY_FAILS, 0)
        if (fails < FREE_TRIES) return 0
        // Rebooting doesn't skip the wait; it restarts it.
        if (!lockout.isSameBoot(ctx)) lockout.set(ctx, lockoutLength(fails))
        return lockout.remainingMs(ctx)
    }

    private fun lockoutLength(fails: Int): Long {
        val doublings = (fails - FREE_TRIES).coerceIn(0, 10)
        return (BASE_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
