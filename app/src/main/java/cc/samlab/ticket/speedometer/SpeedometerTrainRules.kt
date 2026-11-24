package cc.samlab.ticket.speedometer

import cc.samlab.ticket.railway.TrainNumberRules

private val limitedTrainNumber = Regex("[XZ][1-9][0-9]{0,3}", RegexOption.IGNORE_CASE)

internal fun isSpeedometerTrainValid(train: String): Boolean = when (val prefix = train.firstOrNull()?.uppercaseChar()) {
    'X', 'Z' -> {
        val maximum = if (prefix == 'X') 9998 else 1998
        limitedTrainNumber.matches(train) && train.drop(1).toInt() <= maximum
    }
    'G', 'C', 'D', 'T', 'K', 'Y', 'S', 'N', 'V' -> TrainNumberRules.isValid(train)
    else -> false
}

internal fun speedometerMaximum(train: String): Int = when (train.firstOrNull()?.uppercaseChar()) {
    'X' -> 400
    'G' -> 350
    'T' -> 300
    'D', 'K' -> 250
    'C' -> {
        val number = train.drop(1).toIntOrNull() ?: 0
        if (number in 1..1000 || number in 4001..4700) 160 else 350
    }
    'S' -> 200
    'Z' -> 160
    'N' -> 140
    else -> 120
}
