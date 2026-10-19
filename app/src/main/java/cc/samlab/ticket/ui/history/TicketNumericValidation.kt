package cc.samlab.ticket.ui.history

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
    else if (credit) "积分须大于0，且为以00或50结尾的整数"
    else "票价须大于0，且为整数或以.5结尾"
}

internal fun ticketDistanceError(value: String): String? {
    if (value.isEmpty()) return null
    val text = value.trim()
    val number = text.toLongOrNull()
    return if (number != null && number in 1..10_000_000 && Regex("^\\d+$").matches(text)) null
    else "里程须为正整数"
}
