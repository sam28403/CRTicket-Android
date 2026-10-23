package cc.samlab.ticket.ui.sale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.sale.SaleReminder
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.QueryResultEmptyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SaleRemindersScreen(dark: Boolean, pureBlack: Boolean, active: Boolean, onBack: () -> Unit) {
    val text = rememberAppText()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(active) { while (active) { now = Instant.now(); delay(1000) } }
    val actions = rememberSaleReminderActions(now)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var undoJob by remember { mutableStateOf<Job?>(null) }
    val restored = remember { mutableStateMapOf<String, Int>() }
    fun delete(row: SaleReminder) {
        actions.delete(row.id)
        undoJob?.cancel()
        snackbar.currentSnackbarData?.dismiss()
        undoJob = scope.launch {
            val result = snackbar.showSnackbar(
                "${text(R.string.speed_deleted)} ${row.station}",
                text(R.string.speed_undo), duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (actions.restore(row)) restored[row.id] = (restored[row.id] ?: 0) + 1
                else snackbar.showSnackbar(text(R.string.sale_reminder_unavailable))
            }
        }
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text(text(R.string.sale_reminders), fontFamily = projectTitleFont) },
                        navigationIcon = {
                            FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, text(R.string.about_back))
                            }
                        },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            Modifier.fillMaxSize().testTag("sale-reminder-list"),
                            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(actions.rows, key = { it.id }) { reminder ->
                                key(restored[reminder.id] ?: 0) {
                                    SaleReminderItem(
                                        reminder, now, { delete(reminder) },
                                        Modifier.animateItem(
                                            fadeInSpec = tween(200),
                                            placementSpec = tween(360, easing = FastOutSlowInEasing),
                                            fadeOutSpec = tween(180),
                                        ),
                                    )
                                }
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            actions.rows.isEmpty(),
                            enter = fadeIn(tween(240, delayMillis = 180)),
                            exit = fadeOut(tween(120)),
                        ) {
                            QueryResultEmptyState(
                                text(R.string.sale_reminder_empty), active,
                                Modifier.fillMaxSize().testTag("sale-reminder-empty"),
                            )
                        }
                    }
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).padding(16.dp)
                        .testTag("sale-reminder-snackbar"),
                )
            }
        }
    }
}
