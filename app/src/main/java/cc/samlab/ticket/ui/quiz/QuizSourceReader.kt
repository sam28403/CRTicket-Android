package cc.samlab.ticket.ui.quiz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

internal object QuizSourceReader {
    @Volatile private var cached: QuizSources? = null

    fun load(context: Context): QuizSources = cached ?: synchronized(this) {
        cached ?: context.assets.open("quiz-sources.bin").use(::read).also { cached = it }
    }

    fun read(input: InputStream): QuizSources {
        val json = GZIPInputStream(input).bufferedReader().use { JSONObject(it.readText()) }
        require(json.getInt("schema") == 1)
        return QuizSources(
            version = json.getString("version"),
            stations = json.getJSONArray("stations").rows { row ->
                QuizStation(row.getString(0), row.getDouble(1), row.getDouble(2), row.optString(3))
            }.sortedBy { it.name },
            trains = json.getJSONArray("trains").objects { row ->
                val schedule = row.getJSONObject("schedule")
                val rule = schedule.optJSONObject("rule")
                QuizTrain(
                    row.getString("train"),
                    row.getJSONArray("stops").rows { stop ->
                        QuizStop(stop.getString(0), stop.intOrNull(1), stop.intOrNull(2),
                            if (stop.isNull(3)) null else stop.getDouble(3))
                    },
                    QuizSchedule(
                        schedule.optString("startDate").dateOrNull(),
                        schedule.optString("endDate").dateOrNull(),
                        rule?.getInt("period") ?: 0, rule?.getLong("mask") ?: 0,
                    ),
                )
            }.sortedBy { it.code },
            lines = json.getJSONArray("lines").objects { row ->
                QuizLine(row.getString("name"), row.getJSONArray("stops").rows { stop ->
                    stop.getString(0) to if (stop.isNull(1)) null else stop.getDouble(1)
                })
            }.sortedBy { it.name },
            routes = json.getJSONArray("routes").objects { row ->
                val trains = row.getJSONArray("trains")
                QuizRoute(
                    LocalDate.parse(row.getInt("start").toString(), DateTimeFormatter.BASIC_ISO_DATE),
                    LocalDate.parse(row.getInt("end").toString(), DateTimeFormatter.BASIC_ISO_DATE),
                    List(trains.length()) { trains.getString(it) },
                )
            },
        )
    }
}

private fun String.dateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
private fun JSONArray.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)
private fun <T> JSONArray.rows(convert: (JSONArray) -> T): List<T> = List(length()) { convert(getJSONArray(it)) }
private fun <T> JSONArray.objects(convert: (JSONObject) -> T): List<T> = List(length()) { convert(getJSONObject(it)) }
