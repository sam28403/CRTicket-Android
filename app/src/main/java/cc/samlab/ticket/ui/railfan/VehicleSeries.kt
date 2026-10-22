package cc.samlab.ticket.ui.railfan

import java.util.Locale

internal enum class VehicleSeriesTone { RED, ORANGE, GREEN, BLACK, BLUE, PURPLE, SKY_BLUE, OLIVE }

internal fun normalizedVehicleNumber(number: String): String =
    number.trim().uppercase(Locale.ROOT).replace("-", "")

internal fun vehicleSeriesTone(number: String): VehicleSeriesTone {
    val normalized = normalizedVehicleNumber(number)
    return when {
        normalized == "CR400BFZ0524" -> VehicleSeriesTone.PURPLE
        normalized == "CR400BFC5162" -> VehicleSeriesTone.SKY_BLUE
        normalized.startsWith("CR400AF") || normalized.startsWith("CR300AF") -> VehicleSeriesTone.RED
        normalized.startsWith("CR400BF") || normalized.startsWith("CR300BF") -> VehicleSeriesTone.ORANGE
        listOf("YW", "RW", "YZ", "RZ", "SYW", "SYZ", "SRW").any(normalized::startsWith) ->
            VehicleSeriesTone.OLIVE
        normalized.startsWith("CR200") -> VehicleSeriesTone.GREEN
        normalized.startsWith("CRH1") -> VehicleSeriesTone.BLACK
        else -> VehicleSeriesTone.BLUE
    }
}

internal fun specialVehicleImage(number: String): String? = when (normalizedVehicleNumber(number)) {
    "CR400BFZ0524" -> "/assets/imgs/Trains/Cute/CR400BF-Z-0524.png"
    "CR400BFC5162" -> "/assets/imgs/Trains/Cute/CR400BF-C-5162.png"
    else -> null
}
