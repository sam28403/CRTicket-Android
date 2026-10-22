package cc.samlab.ticket.speedometer

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

internal object SpeedometerStore {
    var current by mutableStateOf<SpeedRecording?>(null)
    var history by mutableStateOf<List<SpeedRecording>>(emptyList())
    var paused by mutableStateOf(false)
    var speed by mutableStateOf<Double?>(null)
    var signal by mutableStateOf(false)
    var error by mutableStateOf(false)
    private var file: AtomicFile? = null
    private val writer = Executors.newSingleThreadExecutor()

    @Synchronized
    fun initialize(context: Context) {
        if (file != null) return
        file = AtomicFile(File(context.filesDir, "speedometer.json"))
        history = runCatching {
            val array = JSONArray(file!!.openRead().bufferedReader().use { it.readText() })
            List(array.length()) { decode(array.getJSONObject(it)) }.filter { it.canSave }
        }.getOrDefault(emptyList())
    }

    fun checkpoint() {
        persist(listOfNotNull(current) + history, wait = false)
    }

    fun backup(context: Context): JSONArray {
        initialize(context)
        val records = listOfNotNull(current?.takeIf { it.canSave }) + history
        return JSONArray(records.distinctBy { it.id }.map(::encode))
    }

    fun importHistory(context: Context, rows: JSONArray) {
        initialize(context)
        val imported = List(rows.length()) { decode(rows.getJSONObject(it)) }
        val next = (history + imported).filter { it.id != current?.id }
            .distinctBy { it.id }.sortedByDescending { it.started }
        persist(listOfNotNull(current) + next, wait = true)
        history = next
    }

    private fun persist(records: List<SpeedRecording>, wait: Boolean) {
        val payload = JSONArray(records.map(::encode)).toString().toByteArray()
        val target = file ?: return
        val result = writer.submit {
            var stream: java.io.FileOutputStream? = null
            try {
                stream = target.startWrite()
                stream.write(payload)
                target.finishWrite(stream)
            } catch (error: Exception) {
                stream?.let { target.failWrite(it) }
                throw error
            }
        }
        if (wait) result.get()
    }

    fun finish() {
        current?.takeIf { it.canSave }?.let { history = listOf(it) + history }
        current = null
        speed = null
        signal = false
        checkpoint()
    }

    fun delete(id: String): SpeedRecording? {
        val record = history.firstOrNull { it.id == id } ?: return null
        history = history.filterNot { it.id == id }
        checkpoint()
        return record
    }

    fun restore(record: SpeedRecording) {
        if (history.any { it.id == record.id }) return
        history = (history + record).sortedByDescending { it.started }
        checkpoint()
    }

    private fun encode(record: SpeedRecording) = JSONObject().apply {
        put("id", record.id)
        put("train", record.train)
        put("vehicle", record.vehicle)
        put("from", record.from)
        put("to", record.to)
        put("started", record.started)
        put("elapsed", record.elapsed)
        put("meters", record.meters)
        put("points", JSONArray(record.points.map { point ->
            JSONArray(listOf(point.latitude, point.longitude, point.time, point.segment))
        }))
    }

    private fun decode(json: JSONObject): SpeedRecording {
        val points = json.getJSONArray("points")
        return SpeedRecording(
            json.getString("id"), json.getString("train"), json.getString("from"),
            json.getString("to"), json.getLong("started"), json.getLong("elapsed"),
            json.getDouble("meters"), List(points.length()) {
                val point = points.getJSONArray(it)
                SpeedPoint(point.getDouble(0), point.getDouble(1), point.getLong(2), point.getInt(3))
            },
            vehicle = json.optString("vehicle", ""),
        )
    }
}
