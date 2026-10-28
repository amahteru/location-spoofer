package io.github.amahteru.locationspoofer.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

class LocaleUtils {
    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "zh")

        fun normalizeLanguage(language: String): String = when {
            language.isEmpty() -> ""
            Locale.forLanguageTag(language).language in SUPPORTED_LANGUAGES -> language
            else -> "en"
        }

        @android.annotation.SuppressLint("AppBundleLocaleChanges")
        fun wrap(context: Context, language: String): Context {
            val locale = if (language.isEmpty()) {
                Configuration(context.resources.configuration).locales[0] ?: Locale.getDefault()
            } else {
                Locale.forLanguageTag(language)
            }

            Locale.setDefault(locale)

            val resources = context.resources
            val configuration = Configuration(resources.configuration)

            configuration.setLocale(locale)
            val localeList = LocaleList(locale)
            configuration.setLocales(localeList)
            configuration.setLayoutDirection(locale)

            return context.createConfigurationContext(configuration)
        }
    }
}
