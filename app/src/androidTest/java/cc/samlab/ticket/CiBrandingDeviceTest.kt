package cc.samlab.ticket

import android.content.res.Configuration
import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class CiBrandingDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun localizedBrandingAndAnimatedSplashUseCiIdentity() {
        val fixture = TabletPageFixture(compose)
        try {
            val context = fixture.activity
            assertEquals("cc.samlab.ticket.ci", context.packageName)
            val locales = mutableListOf("en")
            context.resources.getXml(R.xml.locales_config).use { xml ->
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG && xml.name == "locale") {
                        locales.add(xml.getAttributeValue("http://schemas.android.com/apk/res/android", "name"))
                    }
                    xml.next()
                }
            }
            val brandedKeys = listOf(
                R.string.home_title, R.string.about_icon, R.string.welcome_title,
                R.string.welcome_updated_title, R.string.welcome_terms,
                R.string.permissions_all_description, R.string.arrival_permission_message,
                R.string.settings_backup_filename, R.string.report_app_version,
                R.string.quiz_share_text,
            )
            for (locale in locales.distinct()) {
                val configuration = Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(locale))
                }
                val localized = context.createConfigurationContext(configuration)
                assertEquals("CRTicket CI", localized.getString(R.string.home_title))
                for (key in brandedKeys) {
                    val value = localized.getString(key)
                    assertFalse("Unbranded resource in $locale: $value", Regex("CRTicket(?! CI)").containsMatchIn(value))
                    assertFalse(value.contains("CRTicket CI CI"))
                }
                assertEquals(
                    "https://github.com/sam28403/CRTicket-Android",
                    localized.getString(R.string.about_github_url),
                )
            }
            val notes = context.assets.open("changelogs/${cc.samlab.ticket.ui.welcome.WelcomeState.version}.md")
                .bufferedReader().use { it.readText() }
            assertTrue(notes.contains("Sam-Lab CRTicket CI"))
            fixture.navigate("/sale-time")
            fixture.instrumentation.runOnMainSync {
                context.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize().padding(24.dp)) {
                            Text(context.getString(R.string.home_title))
                            AndroidView(
                                factory = { viewContext ->
                                    ImageView(viewContext).apply {
                                        val splash = context.getDrawable(R.drawable.splash_icon_animated)
                                        assertTrue(splash is AnimatedVectorDrawable)
                                        setImageDrawable(splash)
                                        (splash as AnimatedVectorDrawable).start()
                                    }
                                },
                                modifier = Modifier.size(216.dp),
                            )
                        }
                    }
                }
            }
            Thread.sleep(800)
            fixture.capture("ci-branding-splash")
            println("CI_BRANDING_LOCALES=${locales.distinct().size}")
        } finally {
            fixture.close()
        }
    }
}
