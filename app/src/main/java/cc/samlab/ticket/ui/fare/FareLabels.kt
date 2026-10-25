package cc.samlab.ticket.ui.fare

import cc.samlab.ticket.R

internal val FareGrade.label: Int get() = when (this) {
    FareGrade.FAST -> R.string.fare_fast
    FareGrade.EXPRESS -> R.string.fare_express
    FareGrade.LOCAL -> R.string.fare_local
}

internal val FareAir.label: Int get() = when (this) {
    FareAir.NONE -> R.string.fare_air_none
    FareAir.ORDINARY -> R.string.fare_air_ordinary
    FareAir.NEW -> R.string.fare_air_new
}

internal val FareSeat.label: Int get() = when (this) {
    FareSeat.HARD_SEAT -> R.string.seat_hard
    FareSeat.SOFT_SEAT -> R.string.seat_soft
    FareSeat.HARD_LOWER -> R.string.fare_hard_lower
    FareSeat.HARD_MIDDLE -> R.string.fare_hard_middle
    FareSeat.HARD_UPPER -> R.string.fare_hard_upper
    FareSeat.SOFT_LOWER -> R.string.fare_soft_lower
    FareSeat.SOFT_UPPER -> R.string.fare_soft_upper
}

internal val FareSeat.shortLabel: Int get() = when (this) {
    FareSeat.HARD_SEAT -> R.string.seat_hard
    FareSeat.SOFT_SEAT -> R.string.seat_soft
    FareSeat.HARD_LOWER -> R.string.fare_hard_lower_short
    FareSeat.HARD_MIDDLE -> R.string.fare_hard_middle_short
    FareSeat.HARD_UPPER -> R.string.fare_hard_upper_short
    FareSeat.SOFT_LOWER -> R.string.fare_soft_lower_short
    FareSeat.SOFT_UPPER -> R.string.fare_soft_upper_short
}
