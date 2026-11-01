package cc.samlab.ticket.ui.railfan

import android.content.Context
import org.json.JSONObject

internal object VehiclePhotoMetadata {
    private fun key(query: VehicleSearch) = "metadata:${VehiclePhotoParser.identity(query.keyword)}"

    fun read(context: Context, query: VehicleSearch): VehiclePhotoInfo? = runCatching {
        if (VehiclePhotoParser.searchKeyword(query.keyword) == null) return null
        val data = VehiclePhotoCache.read(context, key(query)) ?: return null
        val json = JSONObject(data.toString(Charsets.UTF_8))
        val age = System.currentTimeMillis() - json.getLong("savedAt")
        if (age !in 0..86_400_000L) return null
        VehiclePhotoInfo(
            json.getString("vehicle"), json.getString("imageUrl"), json.getString("sourceUrl"),
            json.getString("date"), json.getString("allocation"), json.getString("line"),
            json.getString("city"), json.getString("train"), json.getString("author"),
        ).takeIf {
            VehiclePhotoParser.identity(it.vehicle) == VehiclePhotoParser.identity(query.keyword)
        }
    }.getOrNull()

    fun write(context: Context, query: VehicleSearch, info: VehiclePhotoInfo, token: Long) {
        if (VehiclePhotoParser.searchKeyword(query.keyword) == null) return
        val json = JSONObject().put("savedAt", System.currentTimeMillis())
            .put("vehicle", info.vehicle).put("imageUrl", info.imageUrl).put("sourceUrl", info.sourceUrl)
            .put("date", info.date).put("allocation", info.allocation).put("line", info.line)
            .put("city", info.city).put("train", info.train).put("author", info.author)
        VehiclePhotoCache.write(context, key(query), json.toString().toByteArray(), token)
    }
}
