package cc.samlab.ticket.backup

import org.json.JSONObject

private val generatorTextFields = setOf(
    "number", "from", "to", "trainNo", "date", "time", "seatType", "seatNo",
    "sellPlace", "gate", "message", "theme",
)

internal fun validateGeneratorDraft(value: JSONObject) {
    for (key in value.keys()) {
        val field = value.get(key)
        when {
            key in generatorTextFields -> require(field is String && field.length <= if (key == "message") 2000 else 200)
            key in setOf("price", "distance") -> require(
                field is String && field.length <= 200 || field is Number && field.toDouble().isFinite(),
            )
            key in setOf("useCredit", "hasConditioner") -> require(
                field is Number && field.toDouble() in listOf(0.0, 1.0),
            )
            else -> error("Invalid generator draft field")
        }
    }
}
