package me.elordenador.clonetube.data.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the JWT between launches using Android's encrypted preferences so the token is
 * not stored in plaintext on disk. Falls back gracefully to an empty session when nothing
 * has been stored yet.
 */
class SessionStore(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "clonetube_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    fun setToken(value: String?) {
        prefs.edit().putString(KEY_TOKEN, value).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt"
    }
}
