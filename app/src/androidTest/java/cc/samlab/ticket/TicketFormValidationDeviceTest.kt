package cc.samlab.ticket

import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TicketFormValidationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun generatorMarksAllInvalidFieldsAndClearsCorrectedErrors() {
        val fixture = TabletPageFixture(compose)
        val preferences = fixture.activity.getSharedPreferences("ticket-generator", 0)
        val previousDraft = preferences.getString("draft", null)
        val previousStorage = fixture.evaluate("localStorage.getItem('crticket-generator-draft')")
        try {
            fixture.shell("input keyevent KEYCODE_WAKEUP")
            fixture.navigate("/")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("generator-editor").fetchSemanticsNodes().isNotEmpty()
            }
            fixture.evaluate("""
                CRTicketAndroid.generatorResult(JSON.stringify({reset: true, draft: {
                    number: '', trainNo: 'bad', from: '', to: '', date: '', time: '',
                    price: '-1', distance: '-1', seatType: '', seatNo: ''
                }}));
            """.trimIndent())
            compose.onNodeWithTag("generator-pdf").performScrollTo()
            assertValidationToast(fixture) {
                compose.onNodeWithTag("generator-pdf").performClick()
            }
            assertAllErrors()
            fixture.capture("generator-field-errors")
            compose.onNodeWithTag("generator-error").assertDoesNotExist()
            compose.onNodeWithTag("history-field-trainNo").performScrollTo()
                .performTextReplacement("G25")
            closeSoftKeyboard()
            assertError("trainNo", false)
            assertError("from", true)
            compose.onNodeWithTag("history-field-trainNo").performTextReplacement("bad-again")
            closeSoftKeyboard()
            assertError("trainNo", true)
            checkPickers()
            assertTicketNumericInputs(compose)
        } finally {
            fixture.evaluate("""
                if ($previousStorage === null) localStorage.removeItem('crticket-generator-draft');
                else localStorage.setItem('crticket-generator-draft', $previousStorage);
            """.trimIndent())
            fixture.close()
            compose.waitForIdle()
            if (previousDraft == null) preferences.edit().remove("draft").commit()
            else preferences.edit().putString("draft", previousDraft).commit()
        }
    }

    @Test fun createAndEditMarkAllInvalidFieldsWithoutDispatchingSave() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.shell("input keyevent KEYCODE_WAKEUP")
            fixture.navigate("/history")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty()
            }
            fixture.evaluate("""
                window.validationSaveCalls = 0;
                window.addEventListener('crticket-history-action', event => {
                    if (event.detail.action === 'save') window.validationSaveCalls++;
                });
                const invalid = {id: 'validation-fixture', number: '', trainNo: 'bad',
                    from: '', to: '', date: '', time: '', price: '-1', distance: '-1',
                    seatType: '', seatNo: ''};
                CRTicketAndroid.historyResult(JSON.stringify({
                    loading: false, error: '', defaults: invalid, rows: [invalid]
                }));
            """.trimIndent())
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("history-record-validation-fixture")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            for (mode in listOf("create", "edit")) {
                if (mode == "create") compose.onNodeWithText("补登记录").performClick()
                else {
                    compose.onNodeWithContentDescription("记录操作").performClick()
                    compose.onNodeWithText("编辑记录").performClick()
                }
                assertValidationToast(fixture) {
                    compose.onNodeWithText("保存", substring = false).performClick()
                }
                assertAllErrors()
                compose.onNodeWithTag("history-editor-error").assertDoesNotExist()
                fixture.capture("history-$mode-field-errors")
                org.junit.Assert.assertEquals("0", fixture.evaluate("window.validationSaveCalls"))
                assertTicketNumericInputs(compose)
                compose.onNodeWithContentDescription("返回").performClick()
            }
        } finally {
            fixture.close()
        }
    }

    private fun assertAllErrors() {
        val keys = listOf(
            "trainNo", "from", "to", "date", "time", "distance",
            "number", "price", "seatType", "seatNo", "sellPlace",
        )
        for (key in keys) {
            assertError(key, true)
        }
    }

    private fun assertValidationToast(fixture: TabletPageFixture, save: () -> Unit) {
        val event = fixture.instrumentation.uiAutomation.executeAndWaitForEvent(
            { save() },
            { event ->
                event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    event.text.any { it.contains("请检查标红的输入框") }
            },
            5_000,
        )
        event.recycle()
    }

    private fun assertError(key: String, expected: Boolean) {
        val node = compose.onNodeWithTag("history-field-$key").performScrollTo()
        node.assert(SemanticsMatcher("native field error = $expected") {
            it.config.contains(SemanticsProperties.Error) == expected
        })
    }

    private fun checkPickers() {
        for (key in listOf("date", "time")) {
            compose.onNodeWithTag("history-field-$key").performScrollTo().performClick()
            compose.onNodeWithText("确定").assertIsDisplayed()
            compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
        }
    }
}
