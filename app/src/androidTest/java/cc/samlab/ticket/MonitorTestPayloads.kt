package cc.samlab.ticket

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object MonitorTestPayloads {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun tickets(): String = JSONObject()
        .put("from", "VNP")
        .put("to", "AOH")
        .put("fromName", "北京南")
        .put("toName", "上海虹桥")
        .put("date", LocalDate.now(zone).plusDays(1).toString())
        .put("interval", 10)
        // Query the real upstream without replacing an existing availability alert.
        .put("filters", JSONObject().put("trainFilters", JSONArray().put("TEST000")))
        .toString()

    fun train(arrived: Boolean = false): String {
        val now = ZonedDateTime.now(zone).withSecond(0).withNano(0)
        val start = now.minusHours(1)
        val intermediate = if (arrived) now.minusMinutes(30) else now.plusMinutes(16)
        val end = if (arrived) now.minusMinutes(10) else now.plusMinutes(45)
        val base = start.toLocalDate()

        fun day(time: ZonedDateTime) = ChronoUnit.DAYS.between(base, time.toLocalDate())

        fun stop(station: String, arrival: ZonedDateTime?, departure: ZonedDateTime?) =
            JSONObject().put("station", station).put("train", "G97").apply {
                arrival?.let {
                    put("arrival", clock.format(it))
                    put("arrivalDay", day(it))
                }
                departure?.let {
                    put("departure", clock.format(it))
                    put("departureDay", day(it))
                }
            }

        return JSONObject()
            .put("train", "G97")
            .put("queryTrain", "G97")
            .put("date", base.toString())
            .put("stops", JSONArray()
                .put(stop("恢复测试起点", null, start))
                .put(stop("恢复测试中间站", intermediate, intermediate.plusMinutes(2)))
                .put(stop("恢复测试终点", end, null)))
            .toString()
    }
}
