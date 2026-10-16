package cc.samlab.ticket.ui.home

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.material3.ColorScheme
import java.util.WeakHashMap

/** Dynamic palettes require many resource lookups; reuse them until resources change. */
internal object HomeColorSchemeCache {
    private class Entry(val configuration: Configuration) {
        val schemes = mutableMapOf<Int, ColorScheme>()
    }

    private val resources = WeakHashMap<Resources, Entry>()

    @Synchronized
    fun get(
        source: Resources,
        dark: Boolean,
        pureBlack: Boolean,
        build: () -> ColorScheme,
    ): ColorScheme {
        val configuration = source.configuration
        val entry = resources[source]?.takeIf { it.configuration == configuration }
            ?: Entry(Configuration(configuration)).also { resources[source] = it }
        val key = (if (dark) 1 else 0) or (if (pureBlack) 2 else 0)
        return entry.schemes.getOrPut(key, build)
    }
}
