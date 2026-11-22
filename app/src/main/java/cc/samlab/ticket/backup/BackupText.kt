package cc.samlab.ticket.backup

internal fun backupTextChunk(text: String, offset: Int): String {
    if (offset !in text.indices) return ""
    var end = offset + minOf(64 * 1024, text.length - offset)
    if (end < text.length && text[end - 1].isHighSurrogate()) end--
    return text.substring(offset, end)
}
