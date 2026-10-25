package cc.samlab.ticket.ui.fare

import java.util.Locale

internal enum class FareGrade { FAST, EXPRESS, LOCAL }
internal enum class FareAir { NONE, ORDINARY, NEW }
internal enum class FareSeat(val soft: Boolean = false, val berthRate: Int = 0) {
    HARD_SEAT,
    SOFT_SEAT(soft = true),
    HARD_LOWER(berthRate = 7619),
    HARD_MIDDLE(berthRate = 7033),
    HARD_UPPER(berthRate = 6447),
    SOFT_LOWER(soft = true, berthRate = 11429),
    SOFT_UPPER(soft = true, berthRate = 10257),
}

internal data class FareQuote(val seat: FareSeat, val price: Double)

/** Port of upstream ordinaryFare.js at 60c2c56. Model and limitations are in docs/ordinary-fare.md. */
internal object OrdinaryFare {
    const val MAX_DISTANCE = 5000
    private val distanceBands = listOf(
        200 to 10, 400 to 20, 700 to 30, 1100 to 40, 1600 to 50,
        2200 to 60, 2900 to 70, 3700 to 80, 4600 to 90, Int.MAX_VALUE to 100,
    )
    private val taperBands = listOf(
        200 to 10, 500 to 9, 1000 to 8, 1500 to 7, 2500 to 6, Int.MAX_VALUE to 5,
    )

    fun gradeForTrain(value: String): FareGrade? {
        val train = value.trim().uppercase(Locale.ROOT)
        val match = Regex("^([ZNVY])([1-9]\\d{0,3})$").matchEntire(train) ?: return null
        val prefix = match.groupValues[1]
        val number = match.groupValues[2].toInt()
        return when {
            prefix in listOf("Z", "N") && number <= 1998 -> FareGrade.FAST
            prefix == "Y" && (number <= 998 || number in 1001..9998) -> FareGrade.FAST
            prefix == "V" -> when (number) {
                in 1..5998 -> FareGrade.FAST
                in 6001..6998 -> FareGrade.EXPRESS
                in 7001..7998 -> FareGrade.LOCAL
                else -> null
            }
            else -> null
        }
    }

    fun fareDistance(distance: Int, minimum: Int = 20): Int {
        val km = maxOf(distance, minimum)
        var start = 0
        for ((end, step) in distanceBands) {
            if (km <= end) return maxOf(start + (km - start - 1) / step * step + step / 2, minimum)
            start = end
        }
        error("Distance outside model")
    }

    private fun rawFare(distance: Int, minimum: Int, rate: Int): Long {
        val km = fareDistance(distance, minimum)
        var previous = 0
        var total = 0L
        for ((end, factor) in taperBands) {
            total += (minOf(km, end) - previous).toLong() * factor * rate
            if (km <= end) break
            previous = end
        }
        return total
    }

    fun calculate(grade: FareGrade, air: FareAir, distance: Int): List<FareQuote> {
        require(distance in 1..10000)
        fun component(micros: Long): Int {
            val whole = ((micros + 500_000) / 1_000_000).toInt()
            return if (air == FareAir.NEW) whole + (whole + 1) / 2 else whole
        }
        val insuranceJiao = (rawFare(distance, 20, 5861) + 4_999_999) / 5_000_000
        val insuranceMicros = insuranceJiao * 100_000
        val insuranceHalf = (insuranceJiao + 4) / 5
        val speedFactor = when (grade) {
            FareGrade.FAST -> 2
            FareGrade.EXPRESS -> 1
            FareGrade.LOCAL -> 0
        }
        val speed = component(rawFare(distance, 100, 1172)) * speedFactor
        val cooling = if (air == FareAir.NONE) 0 else component(rawFare(distance, 20, 1465))
        return FareSeat.entries.map { seat ->
            val ticket = component(rawFare(distance, 20, if (seat.soft) 11722 else 5861) + insuranceMicros)
            val berth = if (seat.berthRate > 0) component(rawFare(distance, 400, seat.berthRate)) else 0
            val ticketFeeHalf = if (ticket > 5) 2 else 1
            val waitingFee = if (!seat.soft && distance > 200) 1 else 0
            val berthFee = if (seat.berthRate > 0) 10 else 0
            val priceHalf = (ticket + speed + cooling + berth + waitingFee + berthFee) * 2 +
                ticketFeeHalf - insuranceHalf
            FareQuote(seat, priceHalf / 2.0)
        }
    }
}
