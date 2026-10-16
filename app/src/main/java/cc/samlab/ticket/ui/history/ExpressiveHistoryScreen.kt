package cc.samlab.ticket.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveHistoryScreen(
    controller: NativeHistoryController,
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    val list = rememberLazyListState()
    var sorting by remember { mutableStateOf(false) }
    val rows = remember(controller.rows, controller.sort) { sortHistory(controller.rows, controller.sort) }
    val draft = controller.ui.draft
    val progress by animateFloatAsState(controller.ui.backProgress, tween(120), label = "history-back")
    LaunchedEffect(controller.sort) { list.scrollToItem(0) }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1f - progress * 0.05f
                scaleY = 1f - progress * 0.05f
                shape = RoundedCornerShape(24.dp)
                clip = progress > 0f
            },
            color = colors.surfaceContainer,
        ) {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            if (draft == null) "历史记录" else if (draft.id == null) "补登记录" else "编辑记录",
                            fontFamily = projectTitleFont,
                        )
                    },
                    navigationIcon = {
                        if (!embedded || draft != null) FilledTonalIconButton(
                            onClick = { if (!controller.ui.closeDetail()) onBack() },
                            modifier = Modifier.padding(start = 8.dp),
                            enabled = !controller.ui.busy,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        if (draft != null) TextButton(
                            onClick = controller::submit, enabled = !controller.ui.busy,
                        ) { Text(if (controller.ui.busy) "正在保存…" else "保存") }
                        else Box {
                            IconButton(onClick = { sorting = true }) {
                                Icon(painterResource(R.drawable.ic_filter_list), "历史记录排序")
                            }
                            DropdownMenu(sorting, { sorting = false }) {
                                HistorySort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort.label) },
                                        onClick = {
                                            controller.changeSort(sort)
                                            sorting = false
                                        },
                                        leadingIcon = { RadioButton(controller.sort == sort, onClick = null) },
                                    )
                                }
                            }
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                if (draft != null && controller.ui.error.isNotEmpty()) Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.large,
                    color = colors.errorContainer,
                ) {
                    Text(
                        controller.ui.error,
                        modifier = Modifier.padding(16.dp).testTag("history-editor-error"),
                        color = colors.onErrorContainer,
                    )
                }
                AnimatedContent(
                    targetState = draft,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (fadeIn(tween(160)) + slideInHorizontally(tween(220)) { it / 20 })
                            .togetherWith(fadeOut(tween(100)))
                    },
                    label = "history-page",
                ) { editor ->
                    if (editor == null) HistoryListContent(controller, list, rows)
                    else HistoryEditorContent(controller, editor)
                }
            }
        }
        HistoryDeleteDialog(controller)
    }
}
