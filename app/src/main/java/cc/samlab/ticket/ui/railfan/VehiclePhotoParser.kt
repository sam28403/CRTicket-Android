package cc.samlab.ticket.ui.railfan

internal data class VehiclePhotoInfo(
    val vehicle: String,
    val imageUrl: String,
    val sourceUrl: String,
    val date: String,
    val allocation: String,
    val line: String,
    val city: String,
    val train: String,
    val author: String,
)

internal object VehiclePhotoParser {
    fun identity(value: String): String = value.uppercase()
        .replace(Regex("[^A-Z0-9]"), "")
        .replace(Regex("^CRH?"), "")

    fun searchKeyword(input: String): String? {
        val match = Regex("^([0-9]+[A-Z][A-Z0-9]*?)([0-9]{4})$")
            .matchEntire(identity(input)) ?: return null
        var model = match.groupValues[1]
        val prefix = if (model.startsWith("400") || model.startsWith("300") || model.startsWith("200")) {
            model = model.replace(Regex("^((?:400|300)(?:AF|BF))([A-Z]+)$"), "$1-$2")
            "CR"
        } else {
            if (model == "1AA" || model == "6AA") model = model.dropLast(1) + "-A"
            "CRH"
        }
        return "$prefix$model-${match.groupValues[2]}"
    }

    private fun text(value: String): String = value
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .trim()

    fun parse(html: String): List<VehiclePhotoInfo> = html
        .split(Regex("<div\\s+class=[\"']Item[\"']>", RegexOption.IGNORE_CASE))
        .drop(1).mapNotNull { block ->
            val heading = Regex("<h1>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = Regex("title=[\"']([^\"']+)", RegexOption.IGNORE_CASE)
                .find(heading)?.groupValues?.get(1) ?: text(heading)
            val vehicle = Regex("[A-Z][A-Z0-9-]*[0-9]{4}")
                .find(title)?.value ?: return@mapNotNull null
            val image = Regex("<img[^>]+src=[\"']([^\"']+)", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            val detail = Regex("href=[\"'](ProView[0-9]*\\.asp\\?ProId=[0-9]+)", RegexOption.IGNORE_CASE)
                .find(heading)?.groupValues?.get(1) ?: return@mapNotNull null
            if (!image.startsWith("/xt/uploadfiles/")) return@mapNotNull null
            fun field(label: String): String = Regex("$label[：:]([^<\\r\\n]*)")
                .find(block)?.groupValues?.get(1)?.let(::text).orEmpty()
            VehiclePhotoInfo(
                vehicle, "http://www.xiaguanzhan.com${image.removePrefix("/xt")}",
                "http://www.xiaguanzhan.com/$detail",
                field("拍摄日期"), field("拍摄配属"), field("拍摄线路"),
                field("拍摄城市"), field("拍摄车次"), field("拍摄作者"),
            )
        }

    fun select(
        input: String,
        records: List<String>,
        photos: List<VehiclePhotoInfo>,
    ): VehiclePhotoInfo? {
        val known = records.flatMap { it.split('+') }.map(::identity).distinct()
        if (known.size > 1) return null
        val keyword = identity(input)
        val matches = photos.filter {
            if (keyword.any { char -> char in 'A'..'Z' }) identity(it.vehicle) == keyword
            else identity(it.vehicle).endsWith(keyword)
        }
        if (matches.map { identity(it.vehicle) }.distinct().size != 1) return null
        return matches.firstOrNull()?.takeIf {
            known.isEmpty() || identity(it.vehicle) == known.single()
        }
    }
}
