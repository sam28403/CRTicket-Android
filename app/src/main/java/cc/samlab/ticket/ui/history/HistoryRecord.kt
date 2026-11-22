package cc.samlab.ticket.ui.history

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString

internal data class HistoryRecord(
    val id: String,
    val date: String,
    val time: String,
    val train: String,
    val from: String,
    val to: String,
    val seatType: String,
    val seatNo: String,
    val number: String,
    val price: Double,
    val credit: Boolean,
)

internal enum class HistorySort(private val textId: Int) {
    NEWEST(R.string.history_sort_newest),
    OLDEST(R.string.history_sort_oldest),
    PRICE_HIGH(R.string.history_sort_price_high),
    PRICE_LOW(R.string.history_sort_price_low);

    val label: String get() = appString(textId)
}

private val departureTimePattern = Regex("(\\d{1,2}):(\\d{2})")

private fun departureMinutes(time: String): Int? {
    val (hours, minutes) = departureTimePattern.matchEntire(time)?.destructured ?: return null
    val hour = hours.toInt()
    val minute = minutes.toInt()
    return (hour * 60 + minute).takeIf { hour < 24 && minute < 60 }
}

internal fun sortHistory(rows: List<HistoryRecord>, sort: HistorySort): List<HistoryRecord> {
    fun dateKey(row: HistoryRecord): String {
        val parts = Regex("\\d+").findAll(row.date).map { it.value.padStart(2, '0') }
        return parts.joinToString("")
    }
    fun byDeparture(dateOrder: Comparator<HistoryRecord>, direction: Int): Comparator<HistoryRecord> =
        dateOrder.thenBy { departureMinutes(it.time)?.times(direction) ?: Int.MAX_VALUE }

    return when (sort) {
        HistorySort.NEWEST -> rows.sortedWith(byDeparture(compareByDescending(::dateKey), -1))
        HistorySort.OLDEST -> rows.sortedWith(byDeparture(compareBy(::dateKey), 1))
        HistorySort.PRICE_HIGH -> rows.sortedByDescending { it.price }
        HistorySort.PRICE_LOW -> rows.sortedBy { it.price }
    }
}

internal fun historyAmount(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
