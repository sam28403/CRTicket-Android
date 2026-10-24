package cc.samlab.ticket.ui

import cc.samlab.ticket.BuildConfig

internal val appVersionLabel: String
    get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
