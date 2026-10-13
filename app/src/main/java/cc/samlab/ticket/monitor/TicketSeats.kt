package cc.samlab.ticket.monitor

object TicketSeats {
    val columns = linkedMapOf(
        "second" to "二等座", "first" to "一等座", "premiumSleeper" to "优选一等座",
        "business" to "商务座", "premium" to "特等座", "advancedSleeper" to "高级软卧",
        "softSleeper" to "软卧 / 动卧", "hardSleeper" to "硬卧", "softSeat" to "软座",
        "hardSeat" to "硬座", "standing" to "无座", "other" to "其他",
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
