package cc.samlab.ticket.ui.sale

import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId

internal val saleZone: ZoneId = ZoneId.of("Asia/Shanghai")

internal data class SaleStation(
    val name: String,
    val abbreviation: String,
    val pinyin: String,
    val bureau: String,
    val city: String = "",
)

internal data class SaleTime(val name: String, val time: LocalTime)

internal fun saleRemaining(
    time: LocalTime,
    now: Instant,
    date: LocalDate = now.atZone(saleZone).toLocalDate(),
): Long {
    val millis = java.time.Duration.between(now, date.atTime(time)
        .atZone(saleZone).toInstant()).toMillis()
    return ((millis + 999) / 1000).coerceAtLeast(0)
}

internal fun saleCountdown(seconds: Long): String = String.format(
    java.util.Locale.ROOT, "%02d:%02d:%02d",
    seconds / 3600, seconds / 60 % 60, seconds % 60,
)
