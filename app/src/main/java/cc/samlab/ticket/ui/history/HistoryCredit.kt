package cc.samlab.ticket.ui.history

internal fun switchHistoryCreditPrice(price: String, useCredit: Boolean): String {
    val amount = price.toBigDecimalOrNull() ?: return price
    val shifted = if (useCredit) amount.movePointRight(2) else amount.movePointLeft(2)
    return shifted.stripTrailingZeros().toPlainString()
}
