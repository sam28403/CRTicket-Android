package cc.samlab.ticket.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import cc.samlab.ticket.R
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

internal data class AppLanguage(val tag: String, val nativeName: String)

/** The Android language declaration is also the source of truth for the in-app picker. */
internal object AppLanguages {
    const val SYSTEM = ""

    fun selectedTag(): String = AppCompatDelegate.getApplicationLocales()
        .toLanguageTags().substringBefore(',')

    fun select(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun supported(context: Context): List<AppLanguage> {
        val languages = mutableListOf<AppLanguage>()
        context.resources.getXml(R.xml.locales_config).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    val tag = checkNotNull(parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android", "name",
                    ))
                    val configuration = Configuration(context.resources.configuration).apply {
                        setLocales(LocaleList(Locale.forLanguageTag(tag)))
                    }
                    val localized = context.createConfigurationContext(configuration)
                    languages += AppLanguage(tag, localized.getString(R.string.language_native_name))
                }
                parser.next()
            }
        }
        return languages
    }
}
