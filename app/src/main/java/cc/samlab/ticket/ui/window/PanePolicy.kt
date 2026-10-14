package cc.samlab.ticket.ui.window

/** Coordinates are relative to the usable content area, in pixels. */
internal data class PaneBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

internal data class PanePlan(val first: PaneBounds, val second: PaneBounds? = null)

internal fun panePlan(
    width: Int,
    height: Int,
    density: Float,
    fold: PaneBounds? = null,
    verticalFold: Boolean = true,
    firstPaneFraction: Float = 0.5f,
): PanePlan {
    val full = PaneBounds(0, 0, width, height)
    if (fold != null) {
        // A zero-width separating crease is still a boundary between panes.
        val before = if (verticalFold) full.copy(right = fold.left.coerceIn(0, width))
            else full.copy(bottom = fold.top.coerceIn(0, height))
        val after = if (verticalFold) full.copy(left = fold.right.coerceIn(0, width))
            else full.copy(top = fold.bottom.coerceIn(0, height))
        if (before.width > 0 && before.height > 0 && after.width > 0 && after.height > 0) {
            return PanePlan(before, after)
        }
    }
    if (width / density >= 840) {
        val gutter = (24 * density).toInt()
        val middle = (width * firstPaneFraction.coerceIn(0.25f, 0.75f)).toInt()
        return PanePlan(full.copy(right = middle - gutter / 2), full.copy(left = middle + gutter / 2))
    }
    return PanePlan(full)
}
