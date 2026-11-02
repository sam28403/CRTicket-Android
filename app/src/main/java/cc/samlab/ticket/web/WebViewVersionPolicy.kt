package cc.samlab.ticket.web

internal object WebViewVersionPolicy {
    const val MINIMUM_MAJOR = 111

    fun isUnsupported(version: String?): Boolean {
        val major = version?.substringBefore('.')?.toIntOrNull() ?: return false
        return major > 0 && major < MINIMUM_MAJOR
    }
}
