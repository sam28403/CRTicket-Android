package cc.samlab.ticket.update

internal data class ReleaseVersion(
    val numbers: List<Int>,
    val preview: List<String>,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        numbers.zip(other.numbers).forEach { (left, right) ->
            if (left != right) return left.compareTo(right)
        }
        if (preview.isEmpty() || other.preview.isEmpty()) {
            return (if (preview.isEmpty()) 1 else 0)
                .compareTo(if (other.preview.isEmpty()) 1 else 0)
        }
        preview.zip(other.preview).forEach { (left, right) ->
            val a = left.toIntOrNull()
            val b = right.toIntOrNull()
            val result = when {
                a != null && b != null -> a.compareTo(b)
                a != null -> -1
                b != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return preview.size.compareTo(other.preview.size)
    }

    companion object {
        fun parse(value: String): ReleaseVersion? {
            val normalized = value.replace(Regex("-[0-9a-f]{7,40}-\\d{6}(?:-ci)?$"), "")
            val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
                .matchEntire(normalized) ?: return null
            val numbers = (1..3).map { match.groupValues[it].toIntOrNull() ?: return null }
            val suffix = match.groupValues[4]
            return ReleaseVersion(
                numbers,
                if (suffix.isEmpty()) emptyList() else suffix.split('.'),
            )
        }
    }
}
