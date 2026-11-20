package cc.samlab.ticket.ui.travel

import java.util.Locale
import org.commonmark.node.Document
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text

internal sealed interface TravelBlock {
    data class Markdown(val document: Document) : TravelBlock
    data class Train(
        val code: String,
        val from: String = "",
        val to: String = "",
        val metrics: TrainMetrics? = null,
    ) : TravelBlock
    data class Station(val name: String) : TravelBlock
    data class Picture(val destination: String, val description: String) : TravelBlock
}

internal data class TrainMetrics(val stations: Int, val minutes: Int, val mileage: String)

private val trainCode = Regex("[A-Z]*[0-9]+[A-Z]*(?:/[A-Z]*[0-9]+[A-Z]*)*", RegexOption.IGNORE_CASE)
private val decimalMileage = Regex("[0-9]+(?:\\.[0-9]+)?")

internal fun travelBlocks(document: Node): List<TravelBlock> = buildList {
    var text = Document()

    fun flushText() {
        if (text.firstChild != null) {
            add(TravelBlock.Markdown(text))
            text = Document()
        }
    }

    while (true) {
        val node = document.firstChild ?: break
        node.unlink()
        val card = if (node is Paragraph) {
            (node.firstChild as? Text)?.takeIf { it.next == null }?.literal?.let(::travelCard)
        } else null
        if (card != null) {
            flushText()
            add(card)
        } else if (node is Paragraph && node.children().any { it is Image }) {
            var paragraph = Paragraph()
            for (inline in node.children().toList()) {
                if (inline is Image) {
                    if (paragraph.firstChild != null) text.appendChild(paragraph)
                    flushText()
                    add(TravelBlock.Picture(inline.destination, inline.imageDescription()))
                    paragraph = Paragraph()
                } else {
                    paragraph.appendChild(inline)
                }
            }
            if (paragraph.firstChild != null) text.appendChild(paragraph)
        } else {
            text.appendChild(node)
        }
    }
    flushText()
}

private fun travelCard(text: String): TravelBlock? {
    val marker = text.trim()
    if (!marker.startsWith('{') || !marker.endsWith('}')) return null
    val body = marker.substring(1, marker.lastIndex)
    if ('{' in body || '}' in body) return null
    val parts = body.split(',', '，').map(String::trim)
    if (parts.any(String::isBlank)) return null
    val name = parts.first()
    val isTrain = name.length <= 40 && trainCode.matches(name)
    return when {
        parts.size == 1 && !isTrain -> TravelBlock.Station(name)
        parts.size == 1 -> TravelBlock.Train(name.uppercase(Locale.ROOT))
        parts.size == 3 && isTrain ->
            TravelBlock.Train(name.uppercase(Locale.ROOT), parts[1], parts[2])
        parts.size == 6 && isTrain -> {
            val stations = parts[3].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val minutes = parts[4].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val mileage = parts[5].takeIf(decimalMileage::matches) ?: return null
            val distance = mileage.toDoubleOrNull() ?: return null
            if (!(distance * 60 / minutes).isFinite()) return null
            TravelBlock.Train(name.uppercase(Locale.ROOT), parts[1], parts[2], TrainMetrics(stations, minutes, mileage))
        }
        else -> null
    }
}

private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }

private fun Node.imageDescription(): String =
    if (this is Text) literal else children().joinToString("") { it.imageDescription() }
