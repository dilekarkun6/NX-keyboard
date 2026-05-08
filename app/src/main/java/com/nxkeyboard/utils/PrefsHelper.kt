package com.nxkeyboard.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PrefsHelper {

    private const val ENCRYPTED_NAME = "nx_secure_prefs"

    fun get(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    }

    fun getEncrypted(context: Context): SharedPreferences {
        val app = context.applicationContext
        return try {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                ENCRYPTED_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            app.getSharedPreferences(ENCRYPTED_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getString(context: Context, key: String, default: String): String {
        return get(context).getString(key, default) ?: default
    }

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean {
        return get(context).getBoolean(key, default)
    }
}
