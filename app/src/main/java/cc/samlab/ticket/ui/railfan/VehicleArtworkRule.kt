package cc.samlab.ticket.ui.railfan

internal data class VehicleArtworkRule(
    val prefixes: Set<String>,
    val ranges: List<IntRange>,
    val image: String,
)

internal class VehicleArtworkIndex(private val rules: List<VehicleArtworkRule>) {
    fun resolve(number: String): String? {
        val value = normalizedVehicleNumber(number)
        val match = Regex("^([A-Z][A-Z0-9]*?)(\\d{4})$").matchEntire(value) ?: return null
        val prefix = match.groupValues[1]
        val serial = match.groupValues[2].toInt()
        return rules.firstOrNull { rule ->
            prefix in rule.prefixes && rule.ranges.any { serial in it }
        }?.image
    }
}
