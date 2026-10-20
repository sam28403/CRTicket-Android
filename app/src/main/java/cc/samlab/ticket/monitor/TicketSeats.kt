package cc.samlab.ticket.monitor

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString

object TicketSeats {
    val columns get() = linkedMapOf(
        "second" to appString(R.string.seat_second_class),
        "first" to appString(R.string.seat_first_class),
        "premiumSleeper" to appString(R.string.seat_premium_first),
        "business" to appString(R.string.seat_business),
        "premium" to appString(R.string.seat_premium),
        "advancedSleeper" to appString(R.string.seat_advanced_sleeper),
        "softSleeper" to appString(R.string.seat_soft_sleeper),
        "hardSleeper" to appString(R.string.seat_hard_sleeper),
        "softSeat" to appString(R.string.seat_soft),
        "hardSeat" to appString(R.string.seat_hard),
        "standing" to appString(R.string.seat_standing),
        "other" to appString(R.string.seat_other),
    )
    private val indices = mapOf(
        "business" to 32, "premium" to 25, "first" to 31, "second" to 30,
        "advancedSleeper" to 21, "softSleeper" to 23, "hardSleeper" to 28,
        "softSeat" to 24, "hardSeat" to 29, "standing" to 26,
        "other" to 22, "premiumSleeper" to 20,
    )
    private val codes = mapOf(
        '9' to "business", 'P' to "premium", 'M' to "first", 'O' to "second",
        'S' to "second", '6' to "advancedSleeper", 'A' to "advancedSleeper",
        '4' to "softSleeper", 'I' to "softSleeper", 'F' to "softSleeper",
        '3' to "hardSleeper", 'J' to "hardSleeper", '2' to "softSeat",
        '1' to "hardSeat", 'D' to "premiumSleeper",
    )

    fun available(value: String): Boolean = value in setOf("有", ">20") ||
        (Regex("\\d+").matches(value) && (value.toLongOrNull() ?: 0) > 0)

    fun parse(fields: List<String>): Map<String, String> {
        val detail = fields.getOrNull(39).orEmpty()
        val counts = mutableMapOf<String, MutableList<Int>>()
        if (Regex("(?:[A-Z0-9]\\d{9})*").matches(detail)) {
            detail.chunked(10).forEach { part ->
                val encoded = part.substring(6).toInt()
                val key = if (encoded >= 3000) "standing" else codes[part[0]]
                if (key != null) counts.getOrPut(key) { mutableListOf() }
                    .add(if (encoded >= 3000) encoded - 3000 else encoded)
            }
        }
        return indices.mapValues { (key, index) ->
            val raw = fields.getOrNull(index).orEmpty().ifEmpty { "--" }
            when (raw) {
                "无" -> "0"
                "有" -> {
                    val count = counts[key]?.singleOrNull()
                    if (count != null && count >= 21 && count < 3000 &&
                        (key == "standing" || count != 21)) count.toString() else ">20"
                }
                else -> raw
            }
        }
    }
}
