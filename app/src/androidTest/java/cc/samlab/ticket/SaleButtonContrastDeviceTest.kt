package cc.samlab.ticket

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.sale.SaleTime
import cc.samlab.ticket.ui.sale.SaleTimeCard
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleButtonContrastDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun emphasizedCardBellHasContrastInEveryState() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/sale-time")
            for (dark in listOf(false, true)) {
                val colors = homeColorScheme(fixture.activity, dark)
                for (state in listOf("available", "subscribed", "disabled")) {
                    val enabled = state != "disabled"
                    val subscribed = state == "subscribed"
                    fixture.instrumentation.runOnMainSync {
                        fixture.activity.findViewById<ComposeView>(R.id.expressive_navigation_view)
                            .setContent {
                                MaterialTheme(colorScheme = colors) {
                                    SaleTimeCard(
                                        row = SaleTime("北京南", LocalTime.of(12, 45)),
                                        bureau = "京局",
                                        now = Instant.now(),
                                        emphasizeStation = true,
                                        subscribed = subscribed,
                                        subscriptionEnabled = enabled,
                                        onSubscribe = {},
                                    )
                                }
                            }
                    }
                    compose.waitForIdle()
                    val button = compose.onNodeWithTag("sale-subscribe:北京南")
                    if (enabled) button.assertIsEnabled() else button.assertIsNotEnabled()
                    val background = when {
                        !enabled -> colors.surfaceContainerHighest
                        subscribed -> colors.primary
                        else -> colors.secondaryContainer
                    }
                    val foreground = when {
                        !enabled -> colors.onSurface
                        subscribed -> colors.onPrimary
                        else -> colors.onSecondaryContainer
                    }
                    val pixels = button.captureToImage().toPixelMap()
                    val rendered = mutableSetOf<Int>()
                    for (y in 0 until pixels.height) {
                        for (x in 0 until pixels.width) rendered.add(pixels[x, y].toArgb())
                    }
                    assertTrue("Button background is rendered", background.toArgb() in rendered)
                    assertTrue("Bell foreground is rendered", foreground.toArgb() in rendered)
                    val ratio = contrast(foreground, background)
                    println("SALE_BELL_CONTRAST dark=$dark state=$state ratio=$ratio")
                    assertTrue("Bell contrast must be at least 3:1", ratio >= 3f)
                    fixture.capture("sale-bell-${if (dark) "dark" else "light"}-$state")
                }
            }
        } finally {
            fixture.close()
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val luminances = listOf(first.luminance(), second.luminance())
        return (luminances.max() + 0.05f) / (luminances.min() + 0.05f)
    }
}
