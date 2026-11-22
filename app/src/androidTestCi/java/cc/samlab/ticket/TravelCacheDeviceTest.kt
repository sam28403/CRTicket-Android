package cc.samlab.ticket

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.settings.ManageStorageActivity
import cc.samlab.ticket.ui.settings.StorageKind
import cc.samlab.ticket.ui.settings.StorageRepository
import cc.samlab.ticket.ui.travel.TravelArticle
import cc.samlab.ticket.ui.travel.TravelArticleState
import cc.samlab.ticket.ui.travel.TravelCache
import cc.samlab.ticket.ui.travel.TravelCardLookup
import cc.samlab.ticket.ui.travel.TravelPageState
import cc.samlab.ticket.ui.travel.TravelRepository
import cc.samlab.ticket.ui.travel.TravelScreen
import cc.samlab.ticket.ui.travel.clearTravelImageMemory
import cc.samlab.ticket.ui.travel.travelImageUrl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TravelCacheDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun scrollingAndReopeningUseDiskWhileRefreshUpdatesSavedContent() = withIsolatedCache { context ->
        val fixture = TabletPageFixture(compose)
        val article = TravelArticle("cache-test-${UUID.randomUUID()}.md")
        val destination = "${article.file}.png"
        val textKey = TravelRepository.articleUrl(article).toString()
        val photoKey = "image:${travelImageUrl(article, destination)}"
        val storage = StorageRepository(context)
        val textRequests = AtomicInteger()
        val photoRequests = AtomicInteger()
        val offline = AtomicInteger()
        val visit = mutableIntStateOf(0)
        val lookup = TravelCardLookup(fixture.activity.webView)
        val view = fixture.activity.findViewById<ComposeView>(R.id.expressive_navigation_view)
        try {
            fixture.instrumentation.runOnMainSync {
                view.setContent {
                    key(visit.intValue) {
                        val page = remember {
                            TravelPageState(
                                readArticle = { ctx, _, refresh ->
                                    withContext(Dispatchers.IO) {
                                        TravelCache.get(ctx, textKey, refresh, {
                                            if (offline.get() != 0) throw IOException("Offline fixture")
                                            val version = textRequests.incrementAndGet()
                                            buildString {
                                                append("# Cached article $version\n\n![Cached photo]($destination)\n\n")
                                                repeat(60) { append("Fixture reading paragraph $it.\n\n") }
                                            }.toByteArray()
                                        }, { it.toString(Charsets.UTF_8) })
                                    }
                                },
                                articleState = TravelArticleState { _, _ ->
                                    val version = photoRequests.incrementAndGet()
                                    if (offline.get() != 0) throw IOException("Offline fixture")
                                    png(version)
                                },
                            )
                        }
                        TravelScreen("/travel/article?file=${article.file}", false, false, false, lookup, {}, {}, page)
                    }
                }
            }
            fun awaitPhoto(version: Int) {
                val expected = png(version)
                val photo = compose.onNodeWithTag("travel-image")
                compose.waitUntil(20_000) {
                    if (TravelCache.read(context, photoKey)?.contentEquals(expected) != true || !photo.isDisplayed()) {
                        return@waitUntil false
                    }
                    val bounds = photo.fetchSemanticsNode().boundsInRoot
                    val expectedHeight = bounds.width * photoHeight(version) / PHOTO_WIDTH
                    abs(bounds.height - expectedHeight) <= 2f
                }
                assertEquals(version, photoRequests.get())
            }

            awaitPhoto(1)
            val reader = compose.onNodeWithTag("travel-article")
            reader.performScrollToIndex(2)
            compose.onNodeWithTag("travel-image").assertIsNotDisplayed()
            clearTravelImageMemory()
            reader.performScrollToIndex(0)
            awaitPhoto(1)
            assertNotNull(TravelCache.read(context, photoKey))
            assertTrue(storage.scan(0).sizes.getValue(StorageKind.WEB) >=
                TravelCache.directory(context).listFiles().orEmpty().sumOf { it.length() })

            offline.set(1)
            clearTravelImageMemory()
            fixture.instrumentation.runOnMainSync { visit.intValue++ }
            awaitPhoto(1)
            assertEquals(1, textRequests.get())

            offline.set(0)
            compose.onNodeWithContentDescription(fixture.activity.getString(R.string.station_refresh)).performClick()
            awaitPhoto(2)
            assertEquals(2, textRequests.get())
            reader.performScrollToIndex(2)
            clearTravelImageMemory()
            reader.performScrollToIndex(0)
            awaitPhoto(2)

            compose.onNodeWithTag("travel-pull").performTouchInput {
                swipeDown(startY = height * 0.20f, endY = height * 0.85f, durationMillis = 450)
            }
            awaitPhoto(3)
            assertEquals(3, textRequests.get())
            fixture.instrumentation.runOnMainSync { visit.intValue++ }
            awaitPhoto(3)
            assertEquals(4, textRequests.get())
            assertTrue(TravelCache.read(context, textKey)!!.toString(Charsets.UTF_8).contains("Cached article 4"))
            val oldToken = TravelCache.token()
            storage.clear(StorageKind.WEB)
            TravelCache.write(context, photoKey, png(4), oldToken)
            assertNull(TravelCache.read(context, textKey))
            assertNull(TravelCache.read(context, photoKey))
        } finally {
            fixture.close()
        }
    }

    @Test fun webCacheCleanupRemovesArticlesAndPhotos() = withIsolatedCache { context ->
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val suppressed = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val fixture = TabletPageFixture(compose)
        try {
            val token = TravelCache.token()
            TravelCache.write(context, "cleanup-article", "# Test article".toByteArray(), token)
            TravelCache.write(context, "cleanup-photo", png(1), token)
            fixture.shell("am start -n ${context.packageName}/${ManageStorageActivity::class.java.name}")
            val webCache = hasText(context.getString(R.string.storage_web)) and hasClickAction() and isEnabled()
            compose.waitUntil(20_000) { compose.onAllNodes(webCache).fetchSemanticsNodes().size == 1 }
            compose.onNode(webCache).performScrollTo().performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            compose.waitUntil(15_000) { !TravelCache.directory(context).exists() }
            assertNull(TravelCache.read(context, "cleanup-article"))
            assertNull(TravelCache.read(context, "cleanup-photo"))
        } finally {
            fixture.close()
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, suppressed).commit()
        }
    }

    private fun withIsolatedCache(test: (Context) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = TravelCache.directory(context)
        val backup = File(context.cacheDir, "travel-test-backup-${UUID.randomUUID()}")
        if (folder.exists()) check(folder.renameTo(backup))
        clearTravelImageMemory()
        try {
            test(context)
        } finally {
            TravelCache.clear(context)
            if (backup.exists()) check(backup.renameTo(folder))
        }
    }

    private fun photoHeight(version: Int): Int = 12 + version * 4

    private fun png(version: Int): ByteArray = ByteArrayOutputStream().use { output ->
        val image = Bitmap.createBitmap(PHOTO_WIDTH, photoHeight(version), Bitmap.Config.ARGB_8888)
        try {
            image.eraseColor(android.graphics.Color.BLUE)
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        } finally {
            image.recycle()
        }
    }

    private companion object {
        const val PHOTO_WIDTH = 48
    }
}
