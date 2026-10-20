package cc.samlab.ticket.ui.history

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import cc.samlab.ticket.railway.TrainNumberRules
import java.time.LocalDate

/** Mirrors historyValidation.js and the local API's ticketValidation.js field limits. */
internal fun validateTicketFields(
    fields: Map<String, String>,
    credit: Boolean = false,
): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    val required = mapOf(
        "number" to appString(R.string.ticket_error_number),
        "trainNo" to appString(R.string.train_error_required),
        "from" to appString(R.string.ticket_error_from),
        "to" to appString(R.string.ticket_error_to),
        "date" to appString(R.string.ticket_error_date),
        "time" to appString(R.string.ticket_error_time),
        "seatType" to appString(R.string.ticket_error_seat_type),
        "seatNo" to appString(R.string.ticket_error_seat_number),
        "sellPlace" to appString(R.string.ticket_error_sell_place),
    )
    required.forEach { (key, message) ->
        if (fields[key].isNullOrBlank()) errors[key] = message
    }
    val train = fields["trainNo"].orEmpty()
    if (train.isNotBlank()) {
        TrainNumberRules.error(train).takeIf { it.isNotEmpty() }?.let {
            errors["trainNo"] = it
        }
    }
    ticketPriceError(fields["price"].orEmpty(), credit)?.let { errors["price"] = it }
    ticketDistanceError(fields["distance"].orEmpty())?.let { errors["distance"] = it }
    val date = Regex("^(\\d{4})(?:年|-|/)(\\d{1,2})(?:月|-|/)(\\d{1,2})日?$")
        .matchEntire(fields["date"].orEmpty())
    val validDate = date != null && runCatching {
        val (year, month, day) = date.destructured
        LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    }.isSuccess
    if (!validDate) errors["date"] = appString(R.string.ticket_error_date)
    if (!Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(fields["time"].orEmpty())) {
        errors["time"] = appString(R.string.ticket_error_time)
    }
    val textFields = listOf(
        "number", "trainNo", "from", "to", "date", "time", "seatType", "seatNo",
        "sellPlace", "gate", "theme", "message",
    )
    textFields.forEach { key ->
        val limit = if (key == "message") 2000 else 200
        if (fields[key].orEmpty().length > limit) {
            errors[key] = appString(R.string.ticket_error_length, limit)
        }
    }
    return errors
}
