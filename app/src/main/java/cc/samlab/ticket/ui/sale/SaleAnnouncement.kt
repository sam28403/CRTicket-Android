package cc.samlab.ticket.ui.sale

import java.time.LocalTime

internal fun parseSaleAnnouncement(html: String): Map<String, LocalTime> {
    val text = html.replace(Regex("<!--[\\s\\S]*?-->"), "")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("&nbsp;|&#160;|&#xA0;", RegexOption.IGNORE_CASE), " ")
    val headings = Regex("(\\d{1,2})\\s*:\\s*(\\d{2})\\s*起售车站")
        .findAll(text).toList()
    require(headings.size == 41) { "Incomplete sale schedule" }
    val result = linkedMapOf<String, LocalTime>()
    headings.forEachIndexed { index, heading ->
        val time = LocalTime.of(heading.groupValues[1].toInt(), heading.groupValues[2].toInt())
        require(time.hour in 8..18 && time.minute % 15 == 0 && (time.hour != 18 || time.minute == 0))
        val end = headings.getOrNull(index + 1)?.range?.first
            ?: text.indexOf('。', heading.range.last + 1).takeIf { it >= 0 }
            ?: error("Missing schedule terminator")
        text.substring(heading.range.last + 1, end)
            .split(Regex("[、，,。\\s\\u00a0]+"))
            .filter { it.matches(Regex("[\\p{IsHan}A-Za-z0-9·（）()]+")) }
            .forEach { name -> result[name] = time }
    }
    require(result.size >= 2000 && result.values.toSet().size == 41)
    return result
}
