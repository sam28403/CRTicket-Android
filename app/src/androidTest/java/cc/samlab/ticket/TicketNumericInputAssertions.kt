package cc.samlab.ticket

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.Espresso.closeSoftKeyboard

internal fun assertTicketNumericInputs(compose: ComposeTestRule) {
    fun field(key: String) = compose.onNodeWithTag("history-field-$key").performScrollTo()
    fun assertError(key: String, expected: Boolean) {
        field(key).assert(SemanticsMatcher("field error = $expected") {
            it.config.contains(SemanticsProperties.Error) == expected
        })
    }
    field("sellPlace").performTextReplacement("北京南站")
    closeSoftKeyboard()
    assertError("sellPlace", false)
    field("price").performTextReplacement("12.5")
    closeSoftKeyboard()
    assertError("price", false)
    for (invalid in listOf("1,000", "1、2", "1 2", "-12", "1.2.3")) {
        field("price").performTextReplacement(invalid)
        field("price").assertTextContains("12.5")
    }
    closeSoftKeyboard()
    field("price").performTextReplacement("12.1")
    closeSoftKeyboard()
    assertError("price", true)
    field("price").performTextReplacement("12.5")
    closeSoftKeyboard()
    compose.onNodeWithTag("history-switch-使用积分").performScrollTo().performClick()
    field("price").assertTextContains("1250")
    assertError("price", false)
    field("price").performTextReplacement("1251")
    closeSoftKeyboard()
    assertError("price", true)
    field("price").performTextReplacement("1250")
    field("price").performTextReplacement("12.5")
    field("price").assertTextContains("1250")
    closeSoftKeyboard()
    assertError("price", false)
    compose.onNodeWithTag("history-switch-使用积分").performScrollTo().performClick()
    field("price").assertTextContains("12.5")
    field("distance").performTextReplacement("1318")
    for (invalid in listOf("1,000", "1、2", "1 2", "-12", "1.5")) {
        field("distance").performTextReplacement(invalid)
        field("distance").assertTextContains("1318")
    }
    closeSoftKeyboard()
    assertError("distance", false)
    field("distance").performTextReplacement("0")
    closeSoftKeyboard()
    assertError("distance", true)
}
