package cc.samlab.ticket.ui.railfan

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.projectTitleFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@Composable
internal fun VehicleHeadCarousel(active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val models by produceState<List<VehicleCarouselModel>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) { readVehicleCarouselCatalog(context) }
    }
    if (models.isEmpty()) {
        Box(modifier.height(248.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    VehicleCatalogCarousel(models, active, modifier)
}

@Composable
private fun VehicleCatalogCarousel(
    models: List<VehicleCarouselModel>,
    active: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current.applicationContext
    val pager = rememberPagerState(initialPage = models.size * 100) { Int.MAX_VALUE }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        if (active) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(4_000)
                if (!pager.isScrollInProgress) pager.animateScrollToPage(
                    pager.currentPage + 1, animationSpec = tween(800),
                )
            }
        }
    }
    Column(modifier.testTag("vehicle-head-carousel"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.vehicle_query_explore),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = projectTitleFont,
        )
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
            val gutter = if (maxWidth > 440.dp) 88.dp else 24.dp
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = gutter),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val model = models[page % models.size]
                val image by produceState<ImageBitmap?>(null, model.image) {
                    value = try {
                        VehicleQueryRepository.carouselImage(context, model.name, model.image)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) { null }
                }
                val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(248.dp).graphicsLayer {
                        scaleX = 1f - distance * 0.1f
                        scaleY = 1f - distance * 0.1f
                        alpha = 1f - distance * 0.35f
                    },
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(172.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(148.dp).background(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), CircleShape,
                            ))
                            val head = image
                            if (head != null) Image(
                                head, model.name, Modifier.size(148.dp), contentScale = ContentScale.Fit,
                            ) else Icon(
                                Icons.Default.Train, model.name, Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            model.name,
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = projectTitleFont,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Column(
            Modifier.padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val position = pager.currentPage % models.size + 1
            Text("$position / ${models.size}", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(
                progress = { position.toFloat() / models.size },
                modifier = Modifier.width(120.dp).height(4.dp),
                drawStopIndicator = {},
            )
        }
    }
}
