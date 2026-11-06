package cc.samlab.ticket.ui.comparison

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.trainlist.TrainListItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrainComparisonScreen(
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyGridState()
    var input by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("") }
    var data by remember { mutableStateOf<TrainComparisonIndex?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val highlighted = remember(data, selected) { data?.find(selected).orEmpty().toSet() }

    LaunchedEffect(retry) {
        failed = false
        try {
            data = loadTrainComparison(context.applicationContext)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    fun search() {
        val number = normalizeComparisonNumber(input)
        val error = if (!validComparisonNumber(number)) R.string.train_comparison_invalid
            else if (data?.find(number).isNullOrEmpty()) R.string.train_comparison_missing
            else null
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            return
        }
        input = number
        selected = number
        keyboard?.hide()
        scrollJob?.cancel()
        scrollJob = scope.launch {
            list.animateScrollToItem(data!!.find(number).first())
        }
    }

    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.train_comparison_title), fontFamily = projectTitleFont)
                    },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(
                            onClick = onBack, modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                TrainComparisonSearch(input, { input = it }, data != null, ::search)
                when {
                    failed -> TextButton(onClick = { retry++ }) {
                        Text(stringResource(R.string.train_comparison_error))
                    }
                    data == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        state = list,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("train-comparison-list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            data!!.rows,
                            key = { index, _ -> index },
                            contentType = { _, _ -> "train" },
                        ) { index, row ->
                            TrainListItem(
                                row.card,
                                highlighted = index in highlighted,
                                showMetrics = false,
                                stationCountLabel = row.old,
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
    }
}
