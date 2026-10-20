package cc.samlab.ticket.i18n

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

internal class AppText(private val resources: Resources) {
    operator fun invoke(@StringRes id: Int, vararg arguments: Any): String =
        if (arguments.isEmpty()) resources.getString(id)
        else resources.getString(id, *arguments)
}

/** Capture the composition's resources for UI text and event callbacks. */
@Composable
internal fun rememberAppText(): AppText {
    val configuration = LocalConfiguration.current
    val resources = LocalContext.current.resources
    return remember(resources, configuration) { AppText(resources) }
}
