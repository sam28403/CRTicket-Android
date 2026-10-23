package cc.samlab.ticket.ui.sale

import java.time.Instant
import java.time.ZonedDateTime
import java.time.LocalTime

internal fun saleDayComplete(now: Instant): Boolean =
    !now.atZone(saleZone).toLocalTime().isBefore(LocalTime.of(18, 0))

internal fun rankSaleSlot(
    traffic: List<Pair<String, Int>>,
    schedule: Map<String, LocalTime>,
    now: Instant,
): List<SaleTime> {
    val slot = nextSaleSlot(now).toLocalTime()
    return traffic.asSequence().filter { it.second > 0 && schedule[it.first] == slot }
        .distinctBy { it.first }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .take(10).map { SaleTime(it.first, slot) }.toList()
}

internal fun nextSaleSlot(now: Instant): ZonedDateTime {
    val local = now.atZone(saleZone)
    val first = local.toLocalDate().atTime(8, 0).atZone(saleZone)
    val last = local.toLocalDate().atTime(18, 0).atZone(saleZone)
    if (local.isBefore(first)) return first
    if (!local.isBefore(last)) return first.plusDays(1)
    return local.withSecond(0).withNano(0)
        .plusMinutes((15 - local.minute % 15).toLong())
}
