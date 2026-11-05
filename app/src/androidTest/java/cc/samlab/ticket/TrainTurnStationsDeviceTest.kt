package cc.samlab.ticket

import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import cc.samlab.ticket.ui.home.homeColorScheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.trainquery.NativeTrainQueryController
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TrainTurnStationsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realTimetableTurnTagsAcrossThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("android", 0)
        val previous = prefs.getString("themePreference", null)
        WelcomeTestState(context).use {
            try {
                for (theme in listOf("classic-light", "classic-dark", "contrast-dark")) {
                    prefs.edit().putString("themePreference", theme).commit()
                    verifyTheme(theme)
                }
            } finally {
                prefs.edit().apply {
                    if (previous == null) remove("themePreference")
                    else putString("themePreference", previous)
                }.commit()
            }
        }
    }

    private fun contextMileage(stop: JSONObject): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(
            R.string.distance_km, stop.optString("mileage"),
        )

    private fun verifyTheme(theme: String) {
        val fixture = TabletPageFixture(compose)
        try {
            val dark = theme != "classic-light"
            val colors = homeColorScheme(fixture.activity, dark, theme == "contrast-dark")
            val contrast = ColorUtils.calculateContrast(
                colors.onTertiaryContainer.toArgb(), colors.tertiaryContainer.toArgb(),
            )
            assertTrue("Tag contrast must be at least 4.5:1", contrast >= 4.5)
            println("TURN CONTRAST $theme: $contrast")
            val date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            var location = "/train?train=6856&date=$date&source=llt"
            fixture.evaluate("window.crticketNavigate(${JSONObject.quote(location)})")
            lateinit var controller: NativeTrainQueryController
            fixture.instrumentation.runOnMainSync {
                controller = fixture.activity.testController<NativeTrainQueryController>()
            }
            compose.waitUntil(120_000) {
                val result = controller.state.optJSONObject("result")
                result?.optString("train")?.startsWith("6856") == true &&
                    (result.optJSONArray("stops")?.length() ?: 0) > 0
            }
            location = controller.state.optString("location")
            val state = controller.state
            val result = state.getJSONObject("result")
            val stops = result.getJSONArray("stops")
            val turnStations = (0 until stops.length()).filter {
                stops.getJSONObject(it).optBoolean("isTurn")
            }.map { stops.getJSONObject(it).getString("station") }
            assertEquals(listOf("集宁南", "二连"), turnStations)
            println("TURN DATA $theme: ${result.optString("mileageVersion")} $turnStations")
            for (station in listOf("集宁南", "二连")) {
                val index = (0 until stops.length()).first {
                    stops.getJSONObject(it).optString("station") == station
                }
                compose.onNodeWithTag("train-query-content")
                    .performScrollToNode(hasTestTag("train-query-stop-$index"))
                val tag = compose.onNodeWithTag("train-query-turn-$index", useUnmergedTree = true)
                tag.assertIsDisplayed().assert(hasAnyDescendant(hasText("本站调向")))
                val card = compose.onNodeWithTag("train-query-stop-$index").fetchSemanticsNode().boundsInRoot
                val bounds = tag.fetchSemanticsNode().boundsInRoot
                assertTrue("Tag must be inside the card", bounds.left >= card.left && bounds.right <= card.right + 1)
                assertTrue("Tag must sit at the bottom right", bounds.center.x > card.center.x && bounds.center.y > card.center.y)
                val mileageText = contextMileage(stops.getJSONObject(index))
                val mileage = compose.onNode(
                    hasText(mileageText) and hasAnyAncestor(hasTestTag("train-query-stop-$index")),
                    useUnmergedTree = true,
                ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Mileage must not overlap the tag", mileage.right + 1 <= bounds.left)
                val original = controller.stateFor(location).toString()
                val hidden = JSONObject(original)
                hidden.getJSONObject("result").getJSONArray("stops")
                    .getJSONObject(index).remove("isTurn")
                controller.receive(hidden.toString())
                compose.waitForIdle()
                compose.onNodeWithTag("train-query-turn-$index", useUnmergedTree = true).assertDoesNotExist()
                val withoutTag = compose.onNodeWithTag("train-query-stop-$index")
                    .fetchSemanticsNode().boundsInRoot
                assertEquals("Tag must not increase card height", withoutTag.height, card.height, 1f)
                val mileageWithoutTag = compose.onNode(
                    hasText(mileageText) and hasAnyAncestor(hasTestTag("train-query-stop-$index")),
                    useUnmergedTree = true,
                ).fetchSemanticsNode().boundsInRoot
                assertEquals("Tag must not squeeze mileage", mileageWithoutTag.width, mileage.width, 1f)
                assertEquals("Tag must not move mileage", mileageWithoutTag.left, mileage.left, 1f)
                controller.receive(original)
                compose.waitForIdle()
                tag.assertIsDisplayed()
                fixture.capture("turn-$theme-$station")
            }
            val absent = (0 until stops.length()).first { !stops.getJSONObject(it).optBoolean("isTurn") }
            compose.onNodeWithTag("train-query-content")
                .performScrollToNode(hasTestTag("train-query-stop-$absent"))
            compose.onNodeWithTag("train-query-turn-$absent", useUnmergedTree = true).assertDoesNotExist()
            fixture.capture("turn-$theme-no-tag")
        } catch (error: Throwable) {
            println("TURN failure route: ${fixture.evaluate("location.hash")}")
            fixture.capture("turn-$theme-failure")
            throw error
        } finally {
            fixture.close()
        }
    }
}

