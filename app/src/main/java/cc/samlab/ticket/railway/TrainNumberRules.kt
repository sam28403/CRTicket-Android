package cc.samlab.ticket.railway

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString

/** Mirrors web/src/utils/trainNumberRules.js; future numbering rules are not active yet. */
object TrainNumberRules {
    private val currentRule = Regex("(?:\\d{4}|5\\d{4}|[GCDZTKLYS]\\d{1,4})", RegexOption.IGNORE_CASE)

    fun isValid(value: String): Boolean = currentRule.matches(value)

    fun error(value: String): String = when {
        value.isEmpty() -> appString(R.string.train_error_required)
        isValid(value) -> ""
        else -> appString(R.string.train_error_invalid)
    }
}
