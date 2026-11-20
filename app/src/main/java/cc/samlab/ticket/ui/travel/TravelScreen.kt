package cc.samlab.ticket.ui.travel

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TravelScreen(
    location: String,
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    cardLookup: TravelCardLookup,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val largeScreen = embedded || LocalConfiguration.current.smallestScreenWidthDp >= 600
    val articlePage = location.substringBefore('?') == "/travel/article"
    val article = remember(location) {
        TravelRepository.article(Uri.parse(location).getQueryParameter("file"))
    }
    var refresh by rememberSaveable(location) { mutableIntStateOf(0) }
    val indexTitle = stringResource(R.string.travel_title)

    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val surface = MaterialTheme.colorScheme.surfaceContainer
        Surface(Modifier.fillMaxSize(), color = surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            article?.title ?: indexTitle,
                            fontFamily = projectTitleFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (!embedded || articlePage) FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { refresh++ }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.station_refresh))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
                )
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (articlePage) {
                        val result = travelContent(location, refresh) {
                            TravelRepository.markdown(context, requireNotNull(article))
                        }
                        TravelResult(result, onRetry = { refresh++ }) { markdown ->
                            if (article != null) TravelArticleBody(
                                article, markdown, if (largeScreen) 600.dp else 400.dp, cardLookup, refresh, onNavigate,
                            )
                        }
                    } else {
                        val result = travelContent(location, refresh) { TravelRepository.articles(context) }
                        TravelResult(result, onRetry = { refresh++ }) { articles ->
                            if (articles.isEmpty()) {
                                Text(stringResource(R.string.search_no_results), Modifier.padding(24.dp))
                            } else {
                                TravelArticleList(articles, onNavigate)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelArticleList(articles: List<TravelArticle>, onNavigate: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 248.dp),
        modifier = Modifier.fillMaxSize().testTag("travel-list"),
        contentPadding = travelPagePadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(articles, key = { it.file }) { article ->
            Surface(
                onClick = { onNavigate("/travel/article?file=${Uri.encode(article.file)}") },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth().testTag("travel-entry:${article.file}"),
            ) {
                ListItem(
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> travelContent(location: String, revision: Int, load: suspend () -> T): Result<T>? {
    var state by remember(location) { mutableStateOf<Result<T>?>(null) }
    LaunchedEffect(location, revision) {
        val result = try {
            Result.success(load())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("RailwayTravel", "Could not retrieve railway travel content", error)
            Result.failure(error)
        }
        if (result.isSuccess || state?.isSuccess != true) state = result
    }
    return state
}

@Composable
private fun <T> TravelResult(result: Result<T>?, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when {
        result == null -> CircularProgressIndicator(Modifier.padding(24.dp))
        result.isSuccess -> content(result.getOrThrow())
        else -> OutlinedButton(onClick = onRetry, modifier = Modifier.padding(24.dp)) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
