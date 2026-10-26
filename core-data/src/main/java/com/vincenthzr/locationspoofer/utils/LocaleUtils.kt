package com.vincenthzr.locationspoofer.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

class LocaleUtils {
    companion object {
        /** 应用提供翻译的语言；其余语言（包括已移除的阿拉伯语）统一回落到英文 */
        private val SUPPORTED_LANGUAGES = setOf("en", "zh")

        /** 规范化已保存的语言代码：空串表示跟随系统，不受支持的语言改为 "en" */
        fun normalizeLanguage(language: String): String = when {
            language.isEmpty() -> ""
            Locale.forLanguageTag(language).language in SUPPORTED_LANGUAGES -> language
            else -> "en"
        }

        // 应用内切换语言；APK 直接分发，app 模块已关闭按语言拆分，不需要 Play Core 按需下载语言包
        @android.annotation.SuppressLint("AppBundleLocaleChanges")
        fun wrap(context: Context, language: String): Context {
            val locale = if (language.isEmpty()) {
                // 如果没有设置，使用系统默认
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
