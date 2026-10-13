package app.votify.mobile.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language override («Русский» / «English» в Настройках → Основные).
 * Kept in a tiny SharedPreferences file because attachBaseContext needs a synchronous
 * read before any UI exists; changing it recreates the activity to re-resolve resources.
 * Russian is the default in res/values, English lives in res/values-en.
 */
object AppLocale {
    const val RU = "ru"
    const val EN = "en"
    private const val PREFS = "votify_locale"
    private const val KEY = "language"

    fun language(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, RU) ?: RU

    fun setLanguage(context: Context, code: String) {
        if (language(context) == code) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    /** Wraps a base context so every resource lookup uses the selected language. */
    fun wrap(context: Context): Context {
        val locale = Locale(language(context))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
