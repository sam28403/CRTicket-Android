package cc.samlab.ticket.ui.travel

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.components.MarkdownText
import cc.samlab.ticket.ui.components.rememberMarkdownRenderer
import cc.samlab.ticket.ui.station.StationDiscoveryCard
import cc.samlab.ticket.ui.trainlist.TrainListItem
import cc.samlab.ticket.ui.trainlist.parseTrainListRow
import org.json.JSONObject

internal val travelPagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)

@Composable
internal fun TravelArticleBody(
    article: TravelArticle,
    markdown: String,
    readingWidth: Dp,
    cardLookup: TravelCardLookup,
    refresh: Int,
    onNavigate: (String) -> Unit,
    articleState: TravelArticleState = remember { TravelArticleState() },
) {
    val context = LocalContext.current
    val renderer = rememberMarkdownRenderer()
    val blocks = remember(renderer, markdown) { travelBlocks(renderer.parse(markdown)) }
    LaunchedEffect(blocks, refresh, articleState) {
        if (articleState.cardsRevision != refresh) {
            articleState.cards = cardLookup.load(context, blocks)
            articleState.cardsRevision = refresh
        }
    }
    val cards = articleState.cards
    LazyColumn(
        modifier = Modifier.widthIn(max = readingWidth).fillMaxSize().testTag("travel-article"),
        contentPadding = travelPagePadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(blocks) { block ->
            when (block) {
                is TravelBlock.Markdown -> MarkdownText(block.document, renderer)
                is TravelBlock.Train -> TravelTrainCard(block, cards, onNavigate)
                is TravelBlock.Station -> {
                    val row = cards.stations[block.name]
                    StationDiscoveryCard(
                        row = row ?: remember(block.name) { JSONObject().put("station", block.name) },
                        tag = "travel-station:${block.name}",
                        status = if (row == null) stringResource(R.string.display_unavailable) else null,
                    ) { onNavigate("/station?station=${Uri.encode(block.name)}") }
                }
                is TravelBlock.Picture -> TravelPicture(article, block, refresh, articleState)
            }
        }
    }
}

@Composable
private fun TravelTrainCard(block: TravelBlock.Train, cards: TravelCardData, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val found = cards.trains[block]
    val articleRow = remember(block, context) {
        val data = JSONObject().put("train", block.code).put("from", block.from).put("to", block.to)
        block.metrics?.let { metrics ->
            val hours = (metrics.minutes / 60).toString().padStart(2, '0')
            val minutes = (metrics.minutes % 60).toString().padStart(2, '0')
            data.put("stationCount", metrics.stations).put("duration", "$hours:$minutes")
                .put("mileage", metrics.mileage)
        }
        parseTrainListRow(context, data)
    }
    val row = if (block.metrics != null) articleRow else found ?: articleRow
    TrainListItem(row = row) {
        onNavigate("/train?train=${Uri.encode(block.code)}&source=online")
    }
}

@Composable
private fun TravelPicture(
    article: TravelArticle,
    block: TravelBlock.Picture,
    refresh: Int,
    articleState: TravelArticleState,
) {
    val url = remember(article, block.destination) {
        TravelRepository.articleUrl(article).resolve(block.destination)?.takeIf { it.isHttps }
    }
    val initial = remember(url) { cachedTravelImage(url)?.let { Result.success(it) } }
    val photo by produceState<Result<ImageBitmap>?>(initial, url, refresh) {
        val result = loadTravelImage(url)
        result.getOrNull()?.let { image ->
            articleState.imageRatios[block.destination] = image.width.toFloat() / image.height
        }
        value = result
    }
    val shape = RoundedCornerShape(20.dp)
    val bitmap = photo?.getOrNull()
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = block.description.ifBlank { null },
            modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .clip(shape).testTag("travel-image"),
        )
    } else {
        val ratio = articleState.imageRatios[block.destination]
        Surface(
            modifier = Modifier.fillMaxWidth().then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                if (photo == null) CircularProgressIndicator()
                else if (block.description.isNotBlank()) Text(block.description)
            }
        }
    }
}
