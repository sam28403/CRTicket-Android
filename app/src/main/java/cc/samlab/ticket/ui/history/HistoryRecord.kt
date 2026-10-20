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

internal fun sortHistory(rows: List<HistoryRecord>, sort: HistorySort): List<HistoryRecord> {
    fun dateKey(row: HistoryRecord): String {
        val parts = Regex("\\d+").findAll(row.date).map { it.value.padStart(2, '0') }
        return parts.joinToString("")
    }
    return when (sort) {
        HistorySort.NEWEST -> rows.sortedByDescending(::dateKey)
        HistorySort.OLDEST -> rows.sortedBy(::dateKey)
        HistorySort.PRICE_HIGH -> rows.sortedByDescending { it.price }
        HistorySort.PRICE_LOW -> rows.sortedBy { it.price }
    }
}

internal fun historyAmount(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
