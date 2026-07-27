package com.kairos.app.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kairos_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_REMEMBERED_EMAIL = "remembered_email"
    }

    fun saveEmail(email: String) {
        prefs.edit().putString(KEY_REMEMBERED_EMAIL, email).apply()
    }

    fun getEmail(): String? {
        return prefs.getString(KEY_REMEMBERED_EMAIL, null)
    }

    fun clearEmail() {
        prefs.edit().remove(KEY_REMEMBERED_EMAIL).apply()
    }
}
