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
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
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
    val textResources = rememberAppText()
    EditorSection(textResources(R.string.ticket_section_other)) {
        HistoryTextField(
            draft,
            "sellPlace",
            textResources(R.string.ticket_sell_place),
            textResources(R.string.ticket_sell_place_hint),
            enabled = enabled,
        )
        HistoryTextField(
            draft,
            "gate",
            textResources(R.string.ticket_gate),
            textResources(R.string.ticket_gate_hint),
            enabled = enabled,
        )
        HistoryChoiceField(draft, "theme", textResources(R.string.ticket_theme), controller.themes, enabled)
        HistoryTextField(draft, "message", textResources(R.string.ticket_message), multiline = true, enabled = enabled)
    }
}

@Composable
internal fun GeneratorPreview(controller: NativeGeneratorController, wide: Boolean) {
    val textResources = rememberAppText()
    if (!controller.showPreview) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(max = 480.dp)
        Box(width.testTag("generator-preview-section")) {
            EditorSection(textResources(R.string.generator_preview)) {
                val bitmap = controller.preview
                if (bitmap == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Image(
                    bitmap.asImageBitmap(),
                    textResources(R.string.generator_preview_description),
                    Modifier.fillMaxWidth().aspectRatio(86f / 54f).testTag("generator-preview"),
                )
            }
        }
    }
}
