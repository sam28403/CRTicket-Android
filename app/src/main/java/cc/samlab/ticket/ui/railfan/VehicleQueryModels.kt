package cc.samlab.ticket.ui.railfan

import java.util.Locale
import cc.samlab.ticket.railway.TrainNumberRules

internal data class VehicleSearch(val keyword: String, val byTrain: Boolean) {
    val path: String get() = "${if (byTrain) "train" else "emu"}/$keyword"

    companion object {
        fun forVehicle(input: String): VehicleSearch? {
            val keyword = input.trim().uppercase(Locale.ROOT)
            val fullNumber = Regex("^(?:CRH?)?\\d+[A-Z][A-Z0-9-]*-?\\d{4}$")
            return keyword.takeIf { fullNumber.matches(it) }?.let { VehicleSearch(it, false) }
        }

        fun forTrain(input: String): VehicleSearch? {
            val keyword = input.trim().uppercase(Locale.ROOT)
            return keyword.takeIf {
                it.firstOrNull() in listOf('G', 'D', 'C', 'S') && TrainNumberRules.isValid(it)
            }?.let { VehicleSearch(it, true) }
        }

        fun parse(input: String): VehicleSearch? {
            val keyword = input.trim().uppercase(Locale.ROOT)
            if (!Regex("^[-+\\w@]+$").matches(keyword)) return null
            return VehicleSearch(keyword, Regex("^0?[GDC]\\d{1,4}$").matches(keyword))
        }
    }
}

internal data class VehicleHistory(val vehicle: String, val train: String, val date: String) {
    val day: String get() = date.take(10)
    val time: String get() = date.drop(11).take(5)
    val displayVehicle: String get() = Regex("^(\\w+)(\\d{4})$")
        .replace(vehicle, "$1-$2")
    val modelKey: String get() = vehicle.replace(Regex("[-+]"), "")
        .replace(Regex("\\d{4}$"), "")
    val imageKey: String get() = specialVehicleImage(vehicle) ?: modelKey
}
