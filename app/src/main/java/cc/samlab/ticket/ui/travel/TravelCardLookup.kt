package cc.samlab.ticket.ui.travel

import android.content.Context
import android.webkit.WebView
import cc.samlab.ticket.ui.trainlist.TrainListRow
import cc.samlab.ticket.ui.trainlist.parseTrainListRows
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

internal data class TravelTrainCardData(val row: TrainListRow, val queryTrain: String, val queryDate: String)

internal data class TravelCardData(
    val date: String = "",
    val trains: Map<TravelBlock.Train, TravelTrainCardData> = emptyMap(),
    val stations: Map<String, JSONObject> = emptyMap(),
)

internal class TravelCardLookup(private val web: WebView) {
    private var sequence = 0
    private val pending = mutableMapOf<Int, CompletableDeferred<JSONObject>>()

    suspend fun load(context: Context, blocks: List<TravelBlock>): TravelCardData {
        val trains = blocks.filterIsInstance<TravelBlock.Train>().distinct()
        val stations = blocks.filterIsInstance<TravelBlock.Station>().map { it.name }.distinct()
        if (trains.isEmpty() && stations.isEmpty()) return TravelCardData()
        val id = ++sequence
        val response = CompletableDeferred<JSONObject>()
        pending[id] = response
        val params = JSONObject().put("requestId", id)
            .put("stations", JSONArray(stations))
            .put("trains", JSONArray().apply {
                trains.forEachIndexed { index, train ->
                    put(JSONObject().put("key", index).put("train", train.code)
                        .put("from", train.from).put("to", train.to))
                }
            })
        val result = try {
            web.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('crticket-travel-cards-request',{detail:$params}))",
                null,
            )
            withTimeoutOrNull(15_000) { response.await() }
        } finally {
            pending.remove(id)
        } ?: return TravelCardData()
        if (result.optBoolean("error")) return TravelCardData()
        val trainRows = result.optJSONArray("trains") ?: JSONArray()
        val trainData = parseTrainListRows(context, trainRows).mapIndexedNotNull { index, row ->
            val source = trainRows.getJSONObject(index)
            trains.getOrNull(source.optInt("key", -1))?.let {
                it to TravelTrainCardData(
                    row, source.optString("queryTrain", row.train), source.optString("queryDate"),
                )
            }
        }.toMap()
        val stationRows = result.optJSONArray("stations") ?: JSONArray()
        val stationData = (0 until stationRows.length()).associate { index ->
            val row = stationRows.getJSONObject(index)
            row.getString("key") to row
        }
        return TravelCardData(result.optString("date"), trainData, stationData)
    }

    fun receive(result: JSONObject) {
        pending[result.optInt("requestId")]?.complete(result)
    }
}
