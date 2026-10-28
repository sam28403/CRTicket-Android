package cc.samlab.ticket.railway

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString

/** Mirrors the active N/V ranges in web/src/utils/trainNumberRules.js. */
object TrainNumberRules {
    private val currentRule = Regex("(?:\\d{4}|5\\d{4}|[GCDZTKLYS]\\d{1,4})", RegexOption.IGNORE_CASE)

    private val additionalRule = Regex("([NV])([1-9]\\d{0,3})", RegexOption.IGNORE_CASE)

    fun isValid(value: String): Boolean {
        if (currentRule.matches(value)) return true
        val match = additionalRule.matchEntire(value) ?: return false
        val number = match.groupValues[2].toInt()
        return when (match.groupValues[1].uppercase()) {
            "N" -> number in 1..1998
            "V" -> number in 1..5998 || number in 6001..6998 || number in 7001..7998
            else -> false
        }
    }

    fun error(value: String): String = when {
        value.isEmpty() -> appString(R.string.train_error_required)
        isValid(value) -> ""
        else -> appString(R.string.train_error_invalid)
    }
}
