package cc.samlab.ticket.railway

/** Mirrors web/src/utils/trainNumberRules.js; future numbering rules are not active yet. */
object TrainNumberRules {
    private val currentRule = Regex("(?:\\d{4}|5\\d{4}|[GCDZTKLYS]\\d{1,4})", RegexOption.IGNORE_CASE)

    fun error(value: String): String = when {
        value.isEmpty() -> "请输入车次"
        currentRule.matches(value) -> ""
        else -> "请输入正确的车次"
    }
}
