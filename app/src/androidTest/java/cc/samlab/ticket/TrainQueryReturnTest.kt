package cc.samlab.ticket

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TrainQueryReturnTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun previousTrainRestoresResultAndScrollWithoutAnotherRequest() {
        val fixture = TabletPageFixture(compose)
        try {
            fun navigate(location: String) {
                fixture.evaluate("window.crticketNavigate(${JSONObject.quote(location)})")
                compose.waitUntil(10_000) {
                    fixture.evaluate("location.hash === ${JSONObject.quote("#$location")}") == "true"
                }
                compose.waitForIdle()
            }
            val assets = fixture.instrumentation.targetContext.assets
            val apiFile = assets.list("web/assets")!!.first { name ->
                name.startsWith("api-") && assets.open("web/assets/$name")
                    .bufferedReader().use { it.readText().contains("withCredentials") }
            }
            val data = currentTrainQueryFixture()
            fixture.evaluate("window.returnFixture = $data")
            fixture.evaluate("""
                window.returnReady = false;
                import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                    const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                    const previous = api.defaults.adapter;
                    window.returnRequests = [];
                    api.defaults.adapter = async config => {
                        if (!['/train-info', '/llt-trains/detail'].includes(config.url)) return previous(config);
                        window.returnRequests.push(config.params.train);
                        const data = {...window.returnFixture,
                            train: config.params.train, date: config.params.date};
                        return {data, config, status: 200, statusText: 'OK', headers: {}};
                    };
                    window.returnReady = true;
                });
            """.trimIndent())
            compose.waitUntil(10_000) { fixture.evaluate("window.returnReady") == "true" }
            val date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            val first = "/train?train=G1&date=$date&source=llt"
            navigate(first)
            compose.waitUntil(10_000) {
                runCatching {
                    compose.onNodeWithTag("train-query-summary")
                        .assert(hasAnyDescendant(hasText("G1")))
                    true
                }.getOrDefault(false)
            }
            compose.onNodeWithTag("train-query-content").performScrollToNode(
                hasText(fixture.activity.getString(R.string.train_map_disclaimer)),
            )
            compose.waitForIdle()
            fun scroll() = compose.onNodeWithTag("train-query-content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            fun settledScroll(): Float {
                var last = scroll()
                var stableSamples = 0
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                while (stableSamples < 5 && android.os.SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(100)
                    val current = scroll()
                    stableSamples = if (kotlin.math.abs(current - last) < 1f) stableSamples + 1 else 0
                    last = current
                }
                check(stableSamples == 5) { "Train query scroll position did not settle" }
                return last
            }
            val previousScroll = settledScroll()
            val disclaimer = fixture.activity.getString(R.string.train_map_disclaimer)
            val previousAnchor = compose.onNodeWithText(disclaimer).fetchSemanticsNode().boundsInRoot.top
            navigate("/train?train=G32&date=$date&source=llt")
            compose.waitUntil(10_000) { fixture.evaluate("window.returnRequests.length") == "2" }
            compose.waitForIdle()
            pressBack()
            compose.waitUntil(10_000) {
                fixture.evaluate("location.hash === ${JSONObject.quote("#$first")}") == "true"
            }
            compose.waitForIdle()
            fixture.capture("train-query-return")
            assertEquals("2", fixture.evaluate("window.returnRequests.length"))
            val returnedScroll = settledScroll()
            val returnedAnchor = runCatching {
                compose.onNodeWithText(disclaimer).fetchSemanticsNode().boundsInRoot.top
            }.getOrNull()
            assertEquals(
                "Disclaimer top: before=$previousAnchor, after=$returnedAnchor",
                previousScroll, returnedScroll, 2f,
            )
            org.junit.Assert.assertNotNull("Returned map disclaimer is not visible", returnedAnchor)
            assertEquals(previousAnchor, returnedAnchor!!, 2f)
            compose.onNodeWithTag("train-query-content").performScrollToIndex(0)
            compose.onNodeWithTag("train-query-submit").performClick()
            compose.waitUntil(10_000) { fixture.evaluate("window.returnRequests.length") == "3" }
        } finally {
            fixture.close()
        }
    }
}
