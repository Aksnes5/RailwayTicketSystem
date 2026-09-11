package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Opens an encrypted preference file and moves values from its legacy plaintext file once. */
object SecurePreferences {
    private const val migrationPrefix = "migration_complete_"

    /**
     * Building the master key and opening an encrypted file both hit the Keystore and decrypt
     * the whole file, so screens that construct several repositories pay for it repeatedly —
     * the trip detail screen opens ten files, three of them the same one.  Instances are
     * cached per file name; every call site pairs a name with a single legacy name, so the
     * one-time migration still runs exactly once per process.
     */
    private val cache = HashMap<String, SharedPreferences>()

    fun open(context: Context, name: String, legacyName: String): SharedPreferences {
        val appContext = context.applicationContext
        synchronized(cache) {
            cache[name]?.let { return it }
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encrypted = EncryptedSharedPreferences.create(
                appContext,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            migrateLegacyValues(appContext, encrypted, legacyName)
            cache[name] = encrypted
            return encrypted
        }
    }

    private fun migrateLegacyValues(
        context: Context,
        encrypted: SharedPreferences,
        legacyName: String
    ) {
        val marker = migrationPrefix + legacyName
        if (encrypted.getBoolean(marker, false)) return

        val legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        val encryptedEditor = encrypted.edit()
        legacy.all.forEach { (key, value) ->
            when (value) {
                is String -> encryptedEditor.putString(key, value)
                is Int -> encryptedEditor.putInt(key, value)
                is Long -> encryptedEditor.putLong(key, value)
                is Float -> encryptedEditor.putFloat(key, value)
                is Boolean -> encryptedEditor.putBoolean(key, value)
                is Set<*> -> encryptedEditor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        if (encryptedEditor.putBoolean(marker, true).commit() && legacy.all.isNotEmpty()) {
            legacy.edit().clear().commit()
        }
    }
}
