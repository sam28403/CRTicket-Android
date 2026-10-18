package cc.samlab.ticket.ui.generator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.history.*
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveGeneratorScreen(
    controller: NativeGeneratorController,
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    embedded: Boolean,
    active: Boolean,
) {
    val draft = controller.draft
    LaunchedEffect(draft, active) {
        if (draft != null && active) snapshotFlow { draft.json().toString() }.collect { json ->
            // Persistence is immediate; the web renderer coalesces preview requests.
            controller.update(json)
        }
    }
    BackHandler(enabled = active && controller.busy) { }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                TopAppBar(
                    title = { Text("生成车票", fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(
                            onClick = onBack,
                            enabled = !controller.busy,
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
                if (draft == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else GeneratorForm(controller, draft)
            }
        }
    }
}

@Composable
private fun GeneratorForm(controller: NativeGeneratorController, draft: HistoryDraft) {
    val enabled = !controller.busy
    Column(
        Modifier.fillMaxSize().testTag("generator-editor")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BoxWithConstraints {
            if (maxWidth >= 720.dp) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { JourneyFields(draft, controller.stations, enabled) }
                Column(Modifier.weight(1f)) {
                    TicketFields(draft, controller.seats, controller.airDisabledSeats, enabled)
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                JourneyFields(draft, controller.stations, enabled)
                TicketFields(draft, controller.seats, controller.airDisabledSeats, enabled)
            }
        }
        EditorSection("其他信息") {
            HistoryTextField(draft, "sellPlace", "售票地点", "例如：南京南站", enabled = enabled)
            HistoryTextField(draft, "gate", "检票 / 候车", "例如：1A / 一候", enabled = enabled)
            HistoryChoiceField(draft, "theme", "车票背景", controller.themes, enabled)
            HistoryTextField(draft, "message", "提示语", multiline = true, enabled = enabled)
        }
        if (controller.showPreview) Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = 480.dp)) {
                EditorSection("实时预览") {
                    val bitmap = controller.preview
                    if (bitmap == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else Image(
                        bitmap.asImageBitmap(), "车票实时预览",
                        Modifier.fillMaxWidth().aspectRatio(86f / 54f).testTag("generator-preview"),
                    )
                }
            }
        }
        if (controller.error.isNotEmpty()) Text(
            controller.error,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("generator-error"),
        )
        GeneratorActions(controller)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GeneratorActions(controller: NativeGeneratorController) {
    val enabled = !controller.busy
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = { controller.showPreview = !controller.showPreview },
                modifier = Modifier.weight(1f), enabled = enabled,
            ) { Text(if (controller.showPreview) "收起实时预览" else "实时预览") }
            OutlinedButton(
                onClick = { controller.action("clear") },
                modifier = Modifier.weight(1f).testTag("generator-clear"), enabled = enabled,
            ) { Text("清空") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { controller.action("pdf") }, enabled = enabled,
                modifier = Modifier.weight(1f).testTag("generator-pdf"),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) { Text("保存 PDF") }
            Button(
                onClick = { controller.action("image") }, enabled = enabled,
                modifier = Modifier.weight(1f).testTag("generator-image"),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) { Text("保存图片") }
        }
        if (!enabled) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
