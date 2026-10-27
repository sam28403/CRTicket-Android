package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.intent.IntentCallback
import androidx.test.runner.intent.IntentMonitorRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.quiz.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class QuizShareDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var welcome: WelcomeTestState
    private lateinit var session: QuizSession
    private lateinit var sessionFile: File
    private var previous: ByteArray? = null
    private var previousNotice: Boolean? = null

    @Before fun prepare() {
        welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", 0)
        previousNotice = preferences.all[IMPORTANT_NOTICE_SUPPRESSED] as? Boolean
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val date = quizToday()
        sessionFile = File(context.filesDir, "daily-quiz/$date.json")
        previous = sessionFile.takeIf { it.exists() }?.readBytes()
        val source = QuizSourceReader.load(context)
        val questions = DailyQuizGenerator(source).generate(date)
        session = QuizSession(date, source.version, questions,
            questions.mapIndexed { index, question ->
                if (index == 0) (question.correctIndex + 1) % 4 else question.correctIndex
            }, 20, true)
        runBlocking { DailyQuizRepository(context).save(session) }
    }

    @After fun restore() {
        previous?.let { sessionFile.writeBytes(it) } ?: sessionFile.delete()
        welcome.close()
        context.getSharedPreferences("android", 0).edit().apply {
            previousNotice?.let { putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
                ?: remove(IMPORTANT_NOTICE_SUPPRESSED)
        }.commit()
    }

    @Test fun sharesPersonalizedImageAndScoreThroughNativeMenuWithoutChangingSummary() {
        val page = TabletPageFixture(compose)
        val sent = AtomicReference<Intent>()
        val callback = IntentCallback { intent -> if (intent.action == Intent.ACTION_CHOOSER) sent.set(intent) }
        IntentMonitorRegistry.getInstance().addIntentCallback(callback)
        try {
            val avatar = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val bytes = ByteArrayOutputStream().also { avatar.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            avatar.recycle()
            page.evaluate("""
                window.__quizOriginalSettings = window.crticketSettings;
                window.crticketSettings = (id, action, payload) => {
                  if (action !== 'snapshot') return window.__quizOriginalSettings(id, action, payload);
                  CRTicketAndroid.settingsResult(JSON.stringify({id, success:true,
                    state:{username:'分享测试用户',avatar:'data:image/png;base64,$encoded'}}));
                }; true
            """.trimIndent())
            page.evaluate("window.crticketNavigate('/railfan/quiz')")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("quiz-share").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("quiz-score").assertTextEquals("95")
            compose.onNodeWithText(context.getString(R.string.quiz_summary_heading)).assertIsDisplayed()
            compose.onNodeWithText("分享测试用户").assertDoesNotExist()
            page.capture("quiz-share-screen")
            compose.onNodeWithTag("quiz-share").assertIsEnabled().performClick()
            compose.waitUntil(20_000) { sent.get() != null }
            @Suppress("DEPRECATION")
            val intent = sent.get().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
            assertEquals(quizShareText(context, session), intent.getStringExtra(Intent.EXTRA_TEXT))
            assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("95"))
            assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains(QUIZ_DOWNLOAD_URL))
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            val folder = File(context.getExternalFilesDir(null), "quiz-share").apply { mkdirs() }
            val image = File(folder, "shared-result.png")
            context.contentResolver.openInputStream(uri)!!.use { input -> image.outputStream().use(input::copyTo) }
            val bitmap = BitmapFactory.decodeFile(image.path)
            assertNotNull(bitmap)
            assertTrue(bitmap.width > 400 && bitmap.height > bitmap.width)
            // The solid red avatar is included only in the exported image.
            var red = 0
            for (y in 0 until bitmap.height / 3 step 4) for (x in 0 until bitmap.width / 2 step 4) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 240 && Color.green(pixel) < 15 && Color.blue(pixel) < 15) red++
            }
            assertTrue("Export must include the user's avatar", red > 100)
            bitmap.recycle()
            Thread.sleep(800)
            instrumentation.uiAutomation.takeScreenshot()!!.also { screenshot ->
                File(folder, "native-share-menu.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            page.shell("input keyevent KEYCODE_BACK")
            compose.waitForIdle()
            compose.onNodeWithTag("quiz-score").assertTextEquals("95")
            compose.onNodeWithText(context.getString(R.string.quiz_summary_heading)).assertIsDisplayed()
            compose.onNodeWithText("分享测试用户").assertDoesNotExist()
        } finally {
            IntentMonitorRegistry.getInstance().removeIntentCallback(callback)
            page.evaluate("window.crticketSettings = window.__quizOriginalSettings; true")
            page.close()
        }
    }

    @Test fun zeroAndPerfectScoresProduceCorrectTextAndReadableImages() = runBlocking {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        try {
            for (correct in listOf(false, true)) {
                val record = session.copy(answers = session.questions.map {
                    if (correct) it.correctIndex else (it.correctIndex + 1) % 4
                })
                val intent = quizShareIntent(context, record, bitmap)
                val points = if (correct) 100 else 0
                assertEquals(context.getString(R.string.quiz_share_text, points, QUIZ_DOWNLOAD_URL),
                    intent.getStringExtra(Intent.EXTRA_TEXT))
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    assertEquals(20, BitmapFactory.decodeStream(input).width)
                }
            }
        } finally { bitmap.recycle() }
    }
}
