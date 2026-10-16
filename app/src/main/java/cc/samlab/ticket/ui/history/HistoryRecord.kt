package cc.samlab.ticket.ui.history

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

internal enum class HistorySort(val label: String) {
    NEWEST("日期从近到远"),
    OLDEST("日期从远到近"),
    PRICE_HIGH("票价从高到低"),
    PRICE_LOW("票价从低到高"),
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
