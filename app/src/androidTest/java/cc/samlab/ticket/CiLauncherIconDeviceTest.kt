package cc.samlab.ticket

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class CiLauncherIconDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun installedCiIconIncludesDistinctMonochromeArtwork() {
        val fixture = TabletPageFixture(compose)
        try {
            val context = fixture.activity
            assertEquals("cc.samlab.ticket.ci", context.packageName)
            val icon = context.packageManager.getApplicationIcon(context.packageName)
            assertTrue(icon is AdaptiveIconDrawable)
            val adaptive = icon as AdaptiveIconDrawable
            val mono = checkNotNull(adaptive.monochrome)
            val colorBitmap = adaptive.toBitmap(216, 216)
            val monoBitmap = mono.toBitmap(216, 216)
            val themedForeground = checkNotNull(mono.constantState).newDrawable().mutate()
            themedForeground.setTint(android.graphics.Color.rgb(113, 54, 0))
            val themedBitmap = AdaptiveIconDrawable(
                ColorDrawable(android.graphics.Color.rgb(255, 219, 191)),
                themedForeground,
            ).toBitmap(216, 216)
            val center = context.getDrawable(R.drawable.ic_launcher_background)!!
                .toBitmap(108, 108).getPixel(54, 54)
            assertEquals(android.graphics.Color.rgb(196, 81, 22), center)
            assertTrue("CI letters must be cut out", android.graphics.Color.alpha(monoBitmap.getPixel(80, 108)) == 0)
            assertTrue("Ticket body must be opaque", android.graphics.Color.alpha(monoBitmap.getPixel(108, 74)) == 255)
            fixture.navigate("/sale-time")
            fixture.instrumentation.runOnMainSync {
                context.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize().background(Color(0xFFF5F1EB)).padding(24.dp)) {
                            Text("CRTicket CI", color = Color(0xFF29221C))
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Image(colorBitmap.asImageBitmap(), "CI icon", Modifier.size(80.dp))
                                Image(
                                    themedBitmap.asImageBitmap(), "CI monochrome icon",
                                    Modifier.size(80.dp),
                                )
                            }
                        }
                    }
                }
            }
            fixture.capture("ci-launcher-icons")
        } finally {
            fixture.close()
        }
    }
}
