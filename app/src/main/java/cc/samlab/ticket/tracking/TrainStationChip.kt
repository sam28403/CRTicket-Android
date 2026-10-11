package cc.samlab.ticket.tracking

object TrainStationChip {
    private const val WINDOW_SIZE = 4
    private const val FRAME_MILLIS = 3_000L

    fun text(station: String, arrival: Long, now: Long): String {
        if (station.length <= WINDOW_SIZE) return station
        // System chips do not expose marquee controls. Slide a short text window instead.
        val offset = ((now - arrival).coerceAtLeast(0) / FRAME_MILLIS %
            (station.length - WINDOW_SIZE + 1)).toInt()
        return station.substring(offset, offset + WINDOW_SIZE)
    }

    fun nextFrameDelay(station: String, arrival: Long, now: Long): Long? {
        if (station.length <= WINDOW_SIZE) return null
        return FRAME_MILLIS - (now - arrival).coerceAtLeast(0) % FRAME_MILLIS
    }
}
