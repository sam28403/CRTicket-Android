package cc.samlab.ticket.i18n

import androidx.annotation.StringRes

/** Resource access for notification formatting, validation and other non-UI code. */
internal object AppStrings {
    var resolve: (Int, Array<out Any>) -> String = { _, _ ->
        error("String resources have not been initialized")
    }
}

internal fun appString(@StringRes id: Int, vararg arguments: Any): String =
    AppStrings.resolve(id, arguments)
