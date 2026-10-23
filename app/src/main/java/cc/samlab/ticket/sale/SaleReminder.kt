package cc.samlab.ticket.sale

import cc.samlab.ticket.ui.sale.saleZone
import java.time.Instant
import java.time.LocalTime

internal data class SaleReminder(val station: String, val saleAt: Long, val bureau: String) {
    val id: String get() = "$station@$saleAt"
    val instant: Instant get() = Instant.ofEpochMilli(saleAt)
    val time: LocalTime get() = instant.atZone(saleZone).toLocalTime()
}

internal fun todayStationSale(time: LocalTime, now: Instant): Long? {
    val local = now.atZone(saleZone)
    if (time.isBefore(LocalTime.of(8, 0))) return null
    if (!local.toLocalTime().isBefore(LocalTime.of(18, 0))) return null
    val sale = local.toLocalDate().atTime(time).atZone(saleZone)
    if (!sale.toInstant().isAfter(now) || time.isAfter(LocalTime.of(18, 0))) return null
    return sale.toInstant().toEpochMilli()
}

internal fun isTodayReminder(row: SaleReminder, now: Instant): Boolean =
    row.saleAt == todayStationSale(row.time, now)

internal fun reminderGroups(rows: List<SaleReminder>): Map<Long, List<SaleReminder>> =
    rows.distinctBy { it.id }.sortedWith(compareBy<SaleReminder> { it.saleAt }.thenBy { it.station })
        .groupBy { it.saleAt }
