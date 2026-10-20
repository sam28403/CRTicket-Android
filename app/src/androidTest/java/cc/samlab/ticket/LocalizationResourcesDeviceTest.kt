package cc.samlab.ticket

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.os.LocaleList
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationResourcesDeviceTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val locales = supportedTestLocales(target)
    private val arguments = Regex("%(\\d+)\\$[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z])")

    private fun localized(tag: String): Context = target.createConfigurationContext(
        Configuration(target.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        },
    )

    @Test
    fun frameworkSelectsEachRegionAndFallsBackToEnglish() {
        assertEquals("Settings", localized("en").getString(R.string.home_settings))
        assertEquals("设置", localized("zh-CN").getString(R.string.home_settings))
        assertEquals("電池優化", localized("zh-HK").getString(R.string.permissions_battery))
        assertEquals("電池最佳化", localized("zh-TW").getString(R.string.permissions_battery))
        assertEquals("Settings", localized("is-IS").getString(R.string.home_settings))
        assertEquals("设置", localized("zh-SG").getString(R.string.home_settings))
        for (tag in locales.filterNot { it == "en" }) {
            assertFalse("$tag fell back to English", localized(tag).getString(R.string.home_settings) == "Settings")
        }
    }

    @Test
    fun allPackagedTranslationsFormatWithoutErrors() {
        val fields = R.string::class.java.fields
        assertTrue(fields.size > 450)
        for (tag in locales) {
            val context = localized(tag)
            for (field in fields) {
                val id = field.getInt(null)
                val template = context.getString(id)
                val matches = arguments.findAll(template).toList()
                if (matches.isEmpty()) continue
                val values = Array<Any>(matches.maxOf { it.groupValues[1].toInt() }) { "北京南" }
                for (match in matches) {
                    val index = match.groupValues[1].toInt() - 1
                    values[index] = when (match.groupValues[2]) {
                        "d" -> 7
                        "f" -> 1.5
                        else -> "北京南"
                    }
                }
                val result = context.getString(id, *values)
                assertFalse("$tag/${field.name}", arguments.containsMatchIn(result))
            }
        }
    }

    @Test
    fun stationNamesAreInsertedWithoutTranslation() {
        for (tag in locales) {
            val title = localized(tag).getString(R.string.monitor_notification_title, "北京南", "南京南")
            assertTrue(title.contains("北京南"))
            assertTrue(title.contains("南京南"))
        }
    }

    @Test
    fun writingDirectionFollowsTheSelectedLanguage() {
        for (tag in locales) {
            val expected = if (tag in setOf("ar", "fa", "ur", "ug")) {
                View.LAYOUT_DIRECTION_RTL
            } else {
                View.LAYOUT_DIRECTION_LTR
            }
            assertEquals(tag, expected, localized(tag).resources.configuration.layoutDirection)
        }
    }

    @Test
    fun systemFontFallbackIncludesTheAdditionalScripts() {
        val paint = Paint()
        for (sample in listOf("ລ", "Ө", "ئ", "བ")) {
            assertTrue("Missing system glyph: $sample", paint.hasGlyph(sample))
        }
    }
}
