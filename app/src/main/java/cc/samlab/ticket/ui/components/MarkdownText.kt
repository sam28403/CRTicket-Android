package cc.samlab.ticket.ui.components

import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import org.commonmark.node.Node

@Composable
internal fun rememberMarkdownRenderer(): Markwon {
    val context = LocalContext.current
    return remember(context) {
        Markwon.builder(context)
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .build()
    }
}

@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    selectable: Boolean = false,
) {
    val renderer = rememberMarkdownRenderer()
    MarkdownTextView(modifier, textStyle, selectable) { renderer.setMarkdown(it, markdown.trim()) }
}

@Composable
internal fun MarkdownText(
    markdown: Node,
    renderer: Markwon,
    modifier: Modifier = Modifier,
) {
    val rendered = remember(markdown, renderer) { renderer.render(markdown) }
    MarkdownTextView(modifier, MaterialTheme.typography.bodyLarge, false) {
        renderer.setParsedMarkdown(it, rendered)
    }
}

@Composable
private fun MarkdownTextView(
    modifier: Modifier,
    textStyle: TextStyle,
    selectable: Boolean,
    setContent: (TextView) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { viewContext ->
            TextView(viewContext).apply {
                setPadding(0, 0, 0, 0)
            }
        },
        update = { view ->
            view.setTextIsSelectable(selectable)
            view.movementMethod = TableAwareMovementMethod.create()
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.textSize = textStyle.fontSize.value
            view.setLineSpacing(0f, 1.35f)
            setContent(view)
        },
    )
}
