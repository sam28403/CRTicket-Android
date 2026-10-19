package cc.samlab.ticket.ui.generator

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.history.EditorSection
import cc.samlab.ticket.ui.history.HistoryChoiceField
import cc.samlab.ticket.ui.history.HistoryDraft
import cc.samlab.ticket.ui.history.HistoryTextField

@Composable
internal fun GeneratorOtherFields(
    controller: NativeGeneratorController,
    draft: HistoryDraft,
    enabled: Boolean,
) {
    EditorSection("其他信息") {
        HistoryTextField(draft, "sellPlace", "售票地点", "例如：南京南站", enabled = enabled)
        HistoryTextField(draft, "gate", "检票 / 候车", "例如：1A / 一候", enabled = enabled)
        HistoryChoiceField(draft, "theme", "车票背景", controller.themes, enabled)
        HistoryTextField(draft, "message", "提示语", multiline = true, enabled = enabled)
    }
}

@Composable
internal fun GeneratorPreview(controller: NativeGeneratorController, wide: Boolean) {
    if (!controller.showPreview) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(max = 480.dp)
        Box(width.testTag("generator-preview-section")) {
            EditorSection("实时预览") {
                val bitmap = controller.preview
                if (bitmap == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Image(
                    bitmap.asImageBitmap(),
                    "车票实时预览",
                    Modifier.fillMaxWidth().aspectRatio(86f / 54f).testTag("generator-preview"),
                )
            }
        }
    }
}
