package com.vaultgallery.app.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the user's chosen UI language by wrapping the base context with an updated
 * [Configuration]. We mirror the language tag into a small synchronous SharedPreferences
 * (DataStore is async and can't be read inside [android.content.ContextWrapper.attachBaseContext]).
 *
 * "system" means follow the device locale.
 */
object LocaleManager {
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    fun persist(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TAG, tag).apply()
    }

    fun storedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "system") ?: "system"

    /** Returns a context configured for the persisted locale (or unchanged for "system"). */
    fun wrap(base: Context): Context {
        val tag = storedTag(base)
        if (tag == "system") return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
        }
        return base.createConfigurationContext(config)
    }
}
