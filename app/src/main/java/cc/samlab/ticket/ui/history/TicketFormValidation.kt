package cc.samlab.ticket.ui.history

import cc.samlab.ticket.railway.TrainNumberRules
import java.time.LocalDate

/** Mirrors historyValidation.js and the local API's ticketValidation.js field limits. */
internal fun validateTicketFields(
    fields: Map<String, String>,
    credit: Boolean = false,
): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    val required = mapOf(
        "number" to "请填写票号 / 订单号",
        "trainNo" to "请输入车次",
        "from" to "请填写出发站",
        "to" to "请填写到达站",
        "date" to "请选择有效的开车日期",
        "time" to "请选择有效的开车时间",
        "seatType" to "请选择席别",
        "seatNo" to "请填写座位号",
        "sellPlace" to "请填写售票地点",
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
    if (!validDate) errors["date"] = "请选择有效的开车日期"
    if (!Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(fields["time"].orEmpty())) {
        errors["time"] = "请选择有效的开车时间"
    }
    val textFields = listOf(
        "number", "trainNo", "from", "to", "date", "time", "seatType", "seatNo",
        "sellPlace", "gate", "theme", "message",
    )
    textFields.forEach { key ->
        val limit = if (key == "message") 2000 else 200
        if (fields[key].orEmpty().length > limit) {
            errors[key] = "最多可输入 $limit 个字符"
        }
    }
    return errors
}
