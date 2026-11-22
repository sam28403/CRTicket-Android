package cc.samlab.ticket.ui.travel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

internal sealed interface TravelContent {
    data class Index(val articles: List<TravelArticle>) : TravelContent
    data class Body(val article: TravelArticle, val markdown: String) : TravelContent
}

internal class TravelPageState(
    private val listArticles: suspend (Context, Boolean) -> List<TravelArticle> = TravelRepository::articles,
    private val readArticle: suspend (Context, TravelArticle, Boolean) -> String = TravelRepository::markdown,
    val articleState: TravelArticleState = TravelArticleState(),
) : ViewModel() {
    var result by mutableStateOf<Result<TravelContent>?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var imageRefresh by mutableStateOf<TravelImageRefresh?>(null)
        private set

    fun load(context: Context, articlePage: Boolean, article: TravelArticle?, initial: Boolean = false) {
        if (loading || (initial && result != null)) return
        loading = true
        viewModelScope.launch {
            try {
                val content = if (articlePage) {
                    requireNotNull(article)
                    TravelContent.Body(article, readArticle(context, article, true))
                } else {
                    TravelContent.Index(listArticles(context, true))
                }
                if (articlePage && !initial) imageRefresh = TravelImageRefresh()
                result = Result.success(content)
                revision++
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("RailwayTravel", "Could not retrieve railway travel content", error)
                result = Result.failure(error)
            } finally {
                loading = false
            }
        }
    }
}

internal class TravelArticleState(
    val downloadImage: (HttpUrl, Long) -> ByteArray = TravelRepository::download,
) {
    var cards by mutableStateOf(TravelCardData())
    var cardsRevision = -1
    val imageRatios = mutableStateMapOf<String, Float>()
}
