package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleDayCompleteDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SaleDayPreviewActivity>()

    @Test fun completeBadgeAndMessageRemainVisibleDuringAnimation() {
        compose.onNodeWithTag("sale-day-complete").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithText(compose.activity.getString(R.string.sale_day_complete)).assertIsDisplayed()
    }
}
