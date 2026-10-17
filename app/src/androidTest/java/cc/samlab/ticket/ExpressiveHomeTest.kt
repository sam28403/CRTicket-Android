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
import androidx.compose.ui.test.onNodeWithTag
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

    @Test fun statisticsSharesWebViewAndRestoresItForOtherPages() {
        val originalParent = activity.webView.parent
        repeat(2) {
            evaluate("window.crticketNavigate('/user')")
            awaitRoute("/user")
            compose.waitUntil(10_000) {
                var visible = false
                instrumentation.runOnMainSync {
                    val screen = activity.findViewById<android.view.View>(R.id.expressive_navigation_view)
                    visible = screen.isShown && screen.width > 0 && screen.height > 0
                }
                visible
            }
            compose.onNodeWithTag("statistics-screen").assertIsDisplayed()
            compose.waitUntil(20_000) {
                evaluate("Boolean(document.querySelector('.user-main'))") == "true"
            }
            assertEquals("false", evaluate("Boolean(document.querySelector('.operations-toolbar'))"))
            instrumentation.runOnMainSync {
                assertTrue(activity.webView.isShown)
                assertTrue(activity.webView.width > 0 && activity.webView.height > 0)
                assertTrue(activity.webView.parent !== originalParent)
            }
            evaluate("window.CRTicketAndroid.setPullRefreshProgress(0.5)")
            instrumentation.runOnMainSync {
                val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                    .getChildAt(0) as android.view.ViewGroup
                val refresh = root.findViewWithTag<android.view.View>("expressive-pull-refresh")
                assertEquals(android.view.View.GONE, refresh.visibility)
            }
            evaluate("window.CRTicketAndroid.setPullRefreshProgress(0)")
            if (it == 0) capture("statistics")
            pressBack()
            awaitRoute("/home")
            instrumentation.runOnMainSync { assertTrue(activity.webView.parent === originalParent) }
            evaluate("window.crticketNavigate('/')")
            awaitRoute("/")
            compose.waitUntil(20_000) {
                evaluate("Boolean(document.querySelector('.main-layout'))") == "true"
            }
            instrumentation.runOnMainSync { assertTrue(activity.webView.isShown) }
            pressBack()
            awaitRoute("/home")
        }
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
            when (route) {
                "/monitor" -> compose.onNodeWithTag("monitor-screen").assertIsDisplayed()
                "/history" -> compose.onNodeWithContentDescription("历史记录排序").assertIsDisplayed()
                "/list" -> compose.onNodeWithTag("train-list-date").assertIsDisplayed()
                "/station" -> compose.onNodeWithTag("station-screen").assertIsDisplayed()
                else -> compose.waitUntil(20_000) {
                    evaluate("Boolean(document.querySelector('$selector'))") == "true"
                }
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
        val title = activity.getString(R.string.egg_title)
        repeat(7) { logo.performClick() }
        compose.onNodeWithText(title).assertDoesNotExist()
        logo.performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.egg_train)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.egg_seat)).assertIsDisplayed()
        capture("about-hidden-ticket")
        pressBack()
        compose.onNodeWithText(title).assertDoesNotExist()
        awaitRoute("/about")
        pressBack()
        awaitRoute("/home")
    }

    @Test fun predictiveAboutBackCanCancelThenCommit() {
        compose.onNodeWithText("关于").performScrollTo().performClick()
        awaitRoute("/about")
        compose.waitForIdle()
        val page = compose.onNodeWithTag("navigation-page:/about")
        val width = page.fetchSemanticsNode().boundsInRoot.width
        instrumentation.runOnMainSync {
            val dispatcher = activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 300f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitUntil(5_000) { page.fetchSemanticsNode().boundsInRoot.width < width - 1f }
        compose.onNodeWithTag("navigation-page:/home").assertExists()
        capture("predictive-return-card")
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitUntil(5_000) {
            kotlin.math.abs(page.fetchSemanticsNode().boundsInRoot.width - width) < 1f
        }
        awaitRoute("/about")
        instrumentation.runOnMainSync {
            val dispatcher = activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(400f, 300f, 0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(250f, 300f, 0.8f, BackEventCompat.EDGE_RIGHT))
        }
        compose.waitUntil(5_000) { page.fetchSemanticsNode().boundsInRoot.width < width - 1f }
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        awaitRoute("/home")
        compose.waitForIdle()
        compose.onNodeWithTag("navigation-page:/about").assertDoesNotExist()
        compose.onNodeWithTag("navigation-page:/home").assertExists()
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
