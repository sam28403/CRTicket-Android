package cc.samlab.ticket.ui.history

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString

internal fun acceptsTicketNumberInput(value: String, decimal: Boolean): Boolean {
    val pattern = if (decimal) "^[0-9]*(?:\\.[0-9]*)?$" else "^[0-9]*$"
    return Regex(pattern).matches(value)
}

internal fun ticketPriceError(value: String, credit: Boolean): String? {
    val text = value.trim()
    val number = text.toDoubleOrNull()
    val format = if (credit) Regex("^\\d*(?:00|50)$")
    else Regex("^(?:\\d+(?:\\.(?:0+|50*))?|\\.50*)$")
    return if (number != null && number > 0 && number <= 10_000_000 && format.matches(text)) null
    else if (credit) appString(R.string.ticket_error_credit)
    else appString(R.string.ticket_error_price)
}

internal fun ticketDistanceError(value: String): String? {
    if (value.isEmpty()) return null
    val text = value.trim()
    val number = text.toLongOrNull()
    return if (number != null && number in 1..10_000_000 && Regex("^\\d+$").matches(text)) null
    else appString(R.string.ticket_error_distance)
}
