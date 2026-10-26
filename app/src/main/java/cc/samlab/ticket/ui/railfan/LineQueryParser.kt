package cc.samlab.ticket.ui.railfan

internal data class RailwayLineStop(val name: String, val kilometres: String?)

/** Parses the source's cumulative mileage column, never the adjacent-station distance. */
internal object LineQueryParser {
    private val rows = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val cells = Regex("<td\\b[^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tags = Regex("<[^>]*>")
    private val distance = Regex("(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?")

    fun stops(html: String, line: String): List<RailwayLineStop> = rows.findAll(html).mapNotNull { row ->
        val values = cells.findAll(row.groupValues[1]).map { plain(it.groupValues[1]) }.toList()
        if (values.size < 9 || values[0] != line || values[1].isBlank()) return@mapNotNull null
        val km = values[7].replace(Regex("(?i)\\s*km$"), "").trim()
        RailwayLineStop(values[1], km.takeIf { distance.matches(it) }?.replace(",", ""))
    }.toList()

    fun catalog(html: String): List<String> {
        val list = Regex(
            "<datalist\\b[^>]*id=[\"']?line[\"']?[^>]*>(.*?)</datalist>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.get(1) ?: return emptyList()
        return Regex("<option\\b[^>]*>(.*?)</option>", RegexOption.IGNORE_CASE)
            .findAll(list).map { plain(it.groupValues[1]) }.filter { it.isNotBlank() }.distinct().toList()
    }

    private fun plain(html: String): String = tags.replace(html, "")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&#39;", "'").trim()
}

internal fun matchingRailwayLines(lines: List<String>, input: String): List<String> {
    val keyword = input.trim()
    if (keyword.isEmpty()) return emptyList()
    val common = listOf("京沪线", "京广线", "京哈线", "京九线")
    return lines.filter { it.contains(keyword, ignoreCase = true) }
        .sortedWith(
            compareBy<String> { it != keyword }
                .thenBy { !it.startsWith(keyword) }
                .thenBy { common.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
        )
}
