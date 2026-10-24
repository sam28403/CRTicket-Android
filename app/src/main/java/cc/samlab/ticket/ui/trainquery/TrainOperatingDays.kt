package cc.samlab.ticket.ui.trainquery

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal data class TrainOperatingDays(val dates: Map<LocalDate, Boolean>) {
    val months: List<YearMonth> = dates.keys.map(YearMonth::from).distinct().sorted()
}

internal fun trainOperatingDays(rows: List<Pair<String, String>>): TrainOperatingDays {
    val dates = rows.mapNotNull { (date, flag) ->
        val day = runCatching {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
        }.getOrNull() ?: return@mapNotNull null
        val running = when (flag) {
            "1" -> true
            "0" -> false
            else -> return@mapNotNull null
        }
        day to running
    }.groupBy({ it.first }, { it.second }).mapNotNull { (date, flags) ->
        flags.distinct().singleOrNull()?.let { date to it }
    }.toMap()
    return TrainOperatingDays(dates)
}

internal fun operatingCalendarCells(month: YearMonth): List<LocalDate?> {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    return List(cellCount) { index ->
        (index - offset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }
}
