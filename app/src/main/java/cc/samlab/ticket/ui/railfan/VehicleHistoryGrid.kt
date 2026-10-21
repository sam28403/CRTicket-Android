package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.trainlist.TrainListRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun VehicleHistoryGrid(
    rows: List<VehicleHistory>,
    byTrain: Boolean,
    trainRows: Map<String, TrainListRow>,
    modifier: Modifier,
    onVehicle: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        if (locale.language == Locale.CHINESE.language) {
            DateTimeFormatter.ofPattern("yyyy年M月d日", locale)
        } else DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }
    val groups = remember(rows) { rows.groupBy { it.day } }
    val images = remember(rows) { mutableStateMapOf<String, ImageBitmap?>() }
    val context = LocalContext.current.applicationContext
    val artwork = remember(context) { VehicleArtworkOverrides.load(context) }
    val imageKeys = remember(rows, artwork) {
        rows.associateWith { artwork.resolve(it.vehicle) ?: it.imageKey }
    }
    LaunchedEffect(rows, byTrain) {
        if (byTrain) rows.distinctBy { imageKeys[it] }.forEach { row ->
            launch {
                images[imageKeys.getValue(row)] = try {
                    VehicleQueryRepository.image(context, row.modelKey, row.vehicle)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = ((maxWidth - 32.dp + 8.dp) / 328.dp).toInt().coerceAtLeast(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().testTag("vehicle-history"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(
                        if (byTrain) R.string.vehicle_query_vehicle_count else R.string.vehicle_query_train_count,
                        rows.size,
                    ),
                    Modifier.padding(start = 4.dp, top = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            groups.forEach { (day, history) ->
                item(key = "date:$day", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        runCatching { LocalDate.parse(day).format(formatter) }.getOrDefault(day),
                        Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(history) { row ->
                    if (byTrain) VehicleHistoryCard(
                        row, images[imageKeys[row]], !images.containsKey(imageKeys[row]),
                    ) { onVehicle(row.displayVehicle) }
                    else VehicleTrainHistoryCard(
                        row, trainRows["${row.day}|${row.train}"], onVehicle, onNavigate,
                    )
                }
            }
        }
    }
}

@Composable
internal fun VehicleHistoryCard(
    row: VehicleHistory,
    image: ImageBitmap?,
    imageLoading: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLowest,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    row.displayVehicle,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = projectTitleFont,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                Text(
                    "${row.train} · ${row.time}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            VehicleHeadImage(row.modelKey, image, imageLoading)
        }
    }
}

@Composable
private fun VehicleHeadImage(model: String, image: ImageBitmap?, loading: Boolean) {
    if (image != null) Image(
        image,
        contentDescription = model,
        modifier = Modifier.size(80.dp),
        contentScale = ContentScale.Fit,
    ) else if (loading) Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp))
    } else Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Train, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
    }
}
