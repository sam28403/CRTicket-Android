package cc.samlab.ticket

import android.app.Application
import androidx.core.content.ContextCompat
import cc.samlab.ticket.i18n.AppStrings

class TicketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStrings.resolve = { id, arguments ->
            val localized = ContextCompat.getContextForLanguage(this)
            if (arguments.isEmpty()) localized.getString(id) else localized.getString(id, *arguments)
        }
    }
}
