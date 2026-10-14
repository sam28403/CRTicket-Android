package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExpressiveHomeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before fun launch() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        compose.waitUntil(20_000) {
            evaluate("Boolean(window.crticketNavigate)") == "true"
        }
        awaitRoute("/home")
    }

    @After fun close() {
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun compactHomeSearchAcceptsInputWithoutFilteringAndKeepsNavigation() {
        compose.onNodeWithText("CRTicket").assertIsDisplayed()
        compose.onNodeWithText("关于").assertExists()
        compose.waitUntil(10_000) { !keyboardVisible() }
        capture("home")

        val search = compose.onNodeWithContentDescription("搜索")
        search.assertIsDisplayed().performClick().performTextInput("G1")
        search.assertTextEquals("G1")
        compose.waitUntil(10_000) { keyboardVisible() }
        capture("search-keyboard")
        search.performImeAction()
        compose.waitUntil(10_000) { !keyboardVisible() }
        compose.onNodeWithText("车票生成").assertIsDisplayed()
        compose.onNodeWithText("设置").assertExists()
        compose.onNodeWithText("关于").assertExists()
        assertEquals("\"/home\"", evaluate("location.hash.slice(1)"))

        compose.onNodeWithText("车票生成").performClick()
        awaitRoute("/")
        compose.waitUntil(20_000) {
            evaluate("Boolean(document.querySelector('.main-layout'))") == "true"
        }
        pressBack()
        awaitRoute("/home")
        compose.onNodeWithContentDescription("搜索").assertTextEquals("G1")
        compose.waitUntil(10_000) { !keyboardVisible() }

        compose.onNodeWithText("设置").performScrollTo().performClick()
        awaitRoute("/settings")
        compose.onNodeWithText("个人资料").assertIsDisplayed()
        pressBack()
        awaitRoute("/home")
        compose.onNodeWithText("关于").performScrollTo().assertIsDisplayed()
        capture("home-returned")
    }

    @Test fun everyHomeDestinationAndAboutReturnToHome() {
        val routes = listOf(
            Triple("车票生成", "/", ".main-layout"),
            Triple("历史记录", "/history", ".history-page"),
            Triple("余票监控", "/monitor", ".monitor-page"),
            Triple("车次查询", "/train", ".train-query-page"),
            Triple("车次列表", "/list", ".train-list-page"),
            Triple("车站大屏", "/station", ".station-page"),
            Triple("运转数据", "/user", ".user-page"),
        )
        routes.forEach { (label, route, selector) ->
            compose.onNodeWithText(label).performScrollTo().performClick()
            awaitRoute(route)
            compose.waitUntil(20_000) {
                evaluate("Boolean(document.querySelector('$selector'))") == "true"
            }
            pressBack()
            awaitRoute("/home")
            compose.waitUntil(10_000) { !keyboardVisible() }
        }
        compose.onNodeWithText("关于").performScrollTo().performClick()
        awaitRoute("/about")
        compose.onNodeWithContentDescription("CRTicket 应用图标").assertIsDisplayed()
        compose.onNodeWithText("CRTicket").assertIsDisplayed()
        compose.onNodeWithText("版本 ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithContentDescription("GitHub").assertIsDisplayed()
        assertEquals("https://github.com/sam28403/CRTicket",
            activity.getString(R.string.about_github_url))
        capture("about")
        compose.onNodeWithContentDescription("返回").performClick()
        awaitRoute("/home")
        compose.onNodeWithText("关于").performScrollTo().performClick()
        awaitRoute("/about")
        pressBack()
        awaitRoute("/home")
        capture("home-destinations")
    }

    @Test fun aboutLogoUnlocksTicketOnEighthTapAndDialogBackStaysOnAbout() {
        compose.onNodeWithText("关于").performScrollTo().performClick()
        awaitRoute("/about")
        val logo = compose.onNodeWithContentDescription("CRTicket 应用图标")
        repeat(7) { logo.performClick() }
        compose.onNodeWithText("隐藏站台 · 八方来福纪念票").assertDoesNotExist()
        logo.performClick()
        compose.onNodeWithText("隐藏站台 · 八方来福纪念票").assertIsDisplayed()
        compose.onNodeWithText("G8888 · 一路发发特别快车").assertIsDisplayed()
        compose.onNodeWithText("08 车 08A 号 · 靠窗 · 永久有效").assertIsDisplayed()
        capture("about-hidden-ticket")
        pressBack()
        compose.onNodeWithText("隐藏站台 · 八方来福纪念票").assertDoesNotExist()
        awaitRoute("/about")
        pressBack()
        awaitRoute("/home")
    }

    @Test fun predictiveAboutBackCanCancelThenCommit() {
        compose.onNodeWithText("关于").performScrollTo().performClick()
        awaitRoute("/about")
        compose.waitForIdle()
        instrumentation.runOnMainSync {
            val dispatcher = activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 300f, 0.6f, BackEventCompat.EDGE_LEFT))
            val about = activity.findViewById<android.view.View>(R.id.expressive_about_view)
            assertTrue(about.scaleX < 1f)
            assertTrue(about.translationX > 0f)
            assertTrue(about.clipToOutline)
            val home = activity.findViewById<android.view.View>(R.id.expressive_home_view)
            assertEquals(android.view.View.VISIBLE, home.visibility)
            assertTrue(home.scaleX < 1f)
            assertTrue(home.foreground != null)
        }
        capture("predictive-return-card")
        instrumentation.runOnMainSync {
            activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        compose.waitUntil(5_000) {
            var restored = false
            instrumentation.runOnMainSync {
                val about = activity.findViewById<android.view.View>(R.id.expressive_about_view)
                restored = about.scaleX == 1f && about.translationX == 0f && !about.clipToOutline
            }
            restored
        }
        awaitRoute("/about")
        instrumentation.runOnMainSync {
            val home = activity.findViewById<android.view.View>(R.id.expressive_home_view)
            assertEquals(android.view.View.GONE, home.visibility)
            assertEquals(1f, home.scaleX)
            assertTrue(home.foreground == null)
            val dispatcher = activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(400f, 300f, 0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(250f, 300f, 0.8f, BackEventCompat.EDGE_RIGHT))
            val about = activity.findViewById<android.view.View>(R.id.expressive_about_view)
            assertTrue(about.translationX < 0f)
            val gestureScale = about.scaleX
            dispatcher.onBackPressed()
            assertEquals(gestureScale, about.scaleX)
            assertEquals(android.view.View.VISIBLE, about.visibility)
        }
        awaitRoute("/home")
        instrumentation.runOnMainSync {
            assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
            val home = activity.findViewById<android.view.View>(R.id.expressive_home_view)
            assertEquals(1f, home.scaleX)
            assertTrue(home.foreground == null)
        }
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 30) {
                visible = activity.window.decorView.rootWindowInsets
                    ?.isVisible(WindowInsets.Type.ime()) == true
            }
        }
        return visible
    }

    private fun awaitRoute(route: String) {
        compose.waitUntil(20_000) { evaluate("location.hash.slice(1)") == "\"$route\"" }
        instrumentation.waitForIdleSync()
    }

    private fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        var value = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) {
                value = it
                done.countDown()
            }
        }
        assertTrue("WebView callback timed out", done.await(10, TimeUnit.SECONDS))
        return value
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val folder = File(activity.getExternalFilesDir(null), "expressive-home-scenes")
            .apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
