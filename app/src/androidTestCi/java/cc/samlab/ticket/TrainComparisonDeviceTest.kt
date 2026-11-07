package cc.samlab.ticket

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.comparison.loadTrainComparison
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainComparisonDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun rightRailDragsToMiddleBottomAndTop() {
        val fixture = TabletPageFixture(compose)
        try {
            val rows = runBlocking { loadTrainComparison(fixture.activity) }.rows
            val indices = rows.mapIndexed { index, row -> "train-card:${row.new}" to index }.toMap()
            val firstTag = "train-card:${rows.first().new}"
            val lastTag = "train-card:${rows.last().new}"
            fixture.navigate("/train-comparison")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag(firstTag).fetchSemanticsNodes().isNotEmpty()
            }
            val list = compose.onNodeWithTag("train-comparison-list")

            dragRail(fixture, list, 0f, 0.5f)
            compose.waitForIdle()
            val middle = list.onChildren().fetchSemanticsNodes().mapNotNull {
                indices[it.config.getOrElse(SemanticsProperties.TestTag) { "" }]
            }.minOrNull()
            assertTrue(
                "Expected a position near the middle, got $middle of ${rows.size}",
                middle != null && middle in (rows.size * 45 / 100)..(rows.size * 55 / 100),
            )
            compose.onNodeWithTag("train-comparison-query").assertIsDisplayed()

            dragRail(fixture, list, 0.5f, 1f)
            compose.waitForIdle()
            compose.onNodeWithTag(lastTag).assertIsDisplayed()

            dragRail(fixture, list, 0.98f, 0f)
            compose.waitForIdle()
            compose.onNodeWithTag(firstTag).assertIsDisplayed()
        } finally {
            fixture.close()
        }
    }

    private fun dragRail(
        fixture: TabletPageFixture,
        list: SemanticsNodeInteraction,
        from: Float,
        to: Float,
    ) {
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val view = fixture.activity.findViewById<View>(R.id.expressive_train_comparison_view)
        val x = view.width - 4f * view.resources.displayMetrics.density
        val top = bounds.top + 1f
        val height = bounds.height - 2f
        val downTime = SystemClock.uptimeMillis()
        fun touch(action: Int, fraction: Float) {
            fixture.instrumentation.runOnMainSync {
                val event = MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(), action, x, top + fraction * height, 0,
                )
                try {
                    view.dispatchTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
        }
        touch(MotionEvent.ACTION_DOWN, from)
        repeat(12) { step ->
            SystemClock.sleep(16)
            touch(MotionEvent.ACTION_MOVE, from + (to - from) * (step + 1) / 12)
        }
        touch(MotionEvent.ACTION_UP, to)
    }

    @Test fun searchesOldAndNewNumbersAndKeepsListLazy() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/train-comparison")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("train-comparison-search")
                    .fetchSemanticsNodes().any {
                        !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                    }
            }
            val field = compose.onNodeWithTag("train-comparison-query")
            field.performTextReplacement("K3")
            compose.onNodeWithTag("train-comparison-search").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("train-card:V3").assertIsDisplayed().assertIsSelected()
            compose.onNode(
                hasText("K3") and hasAnyAncestor(hasTestTag("train-card:V3")),
                useUnmergedTree = true,
            )
                .assertIsDisplayed()
            fixture.capture("train-comparison-old-query")
            field.performTextReplacement("Z3")
            compose.onNodeWithTag("train-comparison-search").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("train-card:Z3").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithTag("train-card:V3").assertDoesNotExist()
            field.performTextReplacement("V3")
            compose.onNodeWithTag("train-comparison-search").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("train-card:V3").assertIsDisplayed().assertIsSelected()
            field.performTextReplacement("G12345")
            field.assertTextEquals("V3")
            fixture.capture("train-comparison-new-query")
        } finally {
            fixture.close()
        }
    }
}
