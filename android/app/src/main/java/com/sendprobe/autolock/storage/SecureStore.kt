package com.sendprobe.autolock.storage

import android.content.Context
import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores a single serializable value per account key, encrypted with a
 * Keystore-backed AES-GCM key (see [KeystoreCryptoBox]) before it ever
 * touches disk. Only ciphertext lives in SharedPreferences.
 */
internal object SecureStore {
    private const val PREFS_NAME = "com.sendprobe.autolock.secure"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    inline fun <reified T> save(context: Context, value: T, account: String) {
        val plaintext = json.encodeToString(value).toByteArray(Charsets.UTF_8)
        val ciphertext = KeystoreCryptoBox.encrypt(plaintext)
        prefs(context).edit()
            .putString(account, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    inline fun <reified T> load(context: Context, account: String): T? {
        val encoded = prefs(context).getString(account, null) ?: return null
        val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
        val plaintext = KeystoreCryptoBox.decrypt(ciphertext)
        return json.decodeFromString(plaintext.toString(Charsets.UTF_8))
    }

    fun delete(context: Context, account: String) {
        prefs(context).edit().remove(account).apply()
    }
}
