package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.railway.ticketsystem.model.User

class UserRepository(context: Context) {
    
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = SecurePreferences.open(context, "secure_user_data", "user_data")
    private val gson = Gson()

    companion object { private val pointsLock = Any() }

    init { migrateLegacyPasswords() }
    
    fun registerUser(user: User): Boolean {
        // 检查用户名是否已存在
        if (isUsernameExists(user.username)) {
            return false
        }
        
        val userJson = gson.toJson(prepareForStorage(user))
        return prefs.edit()
            .putString("current_user", userJson)
            .putString("user_${user.username}", userJson)
            .commit()
    }
    
    fun loginUser(username: String, password: String): User? {
        val userJson = prefs.getString("user_$username", null)
        if (userJson != null) {
            val user = gson.fromJson(userJson, User::class.java)
            if (PasswordHasher.verify(password, user.passwordHash, user.passwordSalt)) {
                val storedUser = prepareForStorage(user)
                val sanitizedJson = gson.toJson(storedUser)
                prefs.edit().putString("current_user", sanitizedJson).commit()
                return storedUser
            }
        }
        return null
    }
    
    fun getCurrentUser(): User? {
        val userJson = prefs.getString("current_user", null)
        return if (userJson != null) {
            gson.fromJson(userJson, User::class.java)
        } else {
            null
        }
    }
    
    fun logout() {
        prefs.edit().remove("current_user").commit()
    }
    
    fun isUsernameExists(username: String): Boolean {
        return prefs.contains("user_$username")
    }
    
    fun updateUser(user: User) {
        val userJson = gson.toJson(prepareForStorage(user))
        prefs.edit()
            .putString("current_user", userJson)
            .putString("user_${user.username}", userJson)
            .commit()
    }

    /** Updates a user even when a different account is currently signed in. */
    fun adjustPoints(userId: String, delta: Int): Boolean {
        return adjustPointsOnce(userId, delta, null)
    }

    /** Writes a durable receipt with the point mutation, so payment recovery cannot credit twice. */
    fun adjustPointsOnce(userId: String, delta: Int, eventKey: String?): Boolean = synchronized(pointsLock) {
        val receiptKey = eventKey?.let { "points_event_${userId}_$it" }
        if (receiptKey != null && prefs.getBoolean(receiptKey, false)) {
            MembershipRepository(appContext).recordPointChange(userId, delta, eventKey)
            return@synchronized true
        }
        val account = prefs.all.entries
            .asSequence()
            .filter { it.key.startsWith("user_") && it.value is String }
            .mapNotNull { entry -> runCatching { gson.fromJson(entry.value as String, User::class.java) }.getOrNull() }
            .firstOrNull { it.id == userId }
            ?: return false
        val updated = prepareForStorage(account.copy(points = (account.points + delta).coerceAtLeast(0)))
        val updatedJson = gson.toJson(updated)
        val editor = prefs.edit().putString("user_${updated.username}", updatedJson)
        if (getCurrentUser()?.id == userId) editor.putString("current_user", updatedJson)
        if (receiptKey != null) editor.putBoolean(receiptKey, true)
        val saved = editor.commit()
        if (saved) MembershipRepository(appContext).recordPointChange(userId, delta, eventKey)
        return saved
    }

    private fun prepareForStorage(user: User): User {
        val digest = if (!user.passwordHash.isNullOrBlank() && !user.passwordSalt.isNullOrBlank()) {
            PasswordDigest(user.passwordHash, user.passwordSalt)
        } else {
            PasswordHasher.hash(user.password)
        }
        return user.copy(
            password = "",
            passwordHash = digest.hash,
            passwordSalt = digest.salt
        )
    }

    /** Converts the encrypted copies of legacy plaintext accounts during their first open. */
    private fun migrateLegacyPasswords() {
        val editor = prefs.edit()
        var changed = false
        prefs.all.forEach { (key, value) ->
            if ((key == "current_user" || key.startsWith("user_")) && value is String) {
                val user = runCatching { gson.fromJson(value, User::class.java) }.getOrNull()
                if (user != null && user.passwordHash.isNullOrBlank() && user.password.isNotBlank()) {
                    editor.putString(key, gson.toJson(prepareForStorage(user)))
                    changed = true
                }
            }
        }
        if (changed) editor.commit()
    }
}







