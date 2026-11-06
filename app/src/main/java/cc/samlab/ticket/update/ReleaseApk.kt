package cc.samlab.ticket.update

internal fun <T> selectReleaseApk(assets: List<T>, name: (T) -> String): T? {
    val regular = assets.filterNot { name(it).contains("offline", ignoreCase = true) }
    return regular.firstOrNull { name(it).contains("universal", ignoreCase = true) }
        ?: regular.singleOrNull()
        ?: assets.singleOrNull()
}
