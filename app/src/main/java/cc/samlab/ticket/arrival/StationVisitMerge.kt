package cc.samlab.ticket.arrival

/** Reimporting a backup must not erase newer stations or move the first visit forward. */
internal fun mergeStationVisits(
    current: List<StationVisit>,
    imported: List<StationVisit>,
): List<StationVisit> = (current + imported)
    .groupBy { it.station }
    .values
    .map { visits ->
        visits.minWith(compareBy<StationVisit> { it.firstArrival }.thenBy { it.historicalReminder })
    }
    .sortedByDescending { it.firstArrival }
