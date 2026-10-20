package cc.samlab.ticket.monitor

import android.app.Activity
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import org.json.JSONObject

class TicketMonitorBridge(
    private val activity: Activity,
    private val callback: (String, String, JSONObject) -> Unit,
) {
    fun restore() = TicketMonitorService.restore(activity)

    fun state(id: String) = reply(id, TicketMonitorStore.status(activity))

    fun start(id: String, payload: String) = change(id) {
        TicketMonitorService.start(activity, payload)
    }

    fun update(id: String, payload: String) = change(id) {
        TicketMonitorService.update(activity, payload)
    }

    fun reset(id: String) = change(id) {
        TicketMonitorService.stop(activity)
        TicketMonitorStore.save(activity, JSONObject())
    }

    fun refresh(id: String) = change(id) { TicketMonitorService.refresh(activity) }

    fun stop(id: String) = change(id) { TicketMonitorService.stop(activity) }

    private fun change(id: String, action: () -> Unit) {
        try {
            action()
            state(id)
        } catch (error: Exception) {
            reply(id, JSONObject().put("success", false)
                .put("message", error.message ?: appString(R.string.monitor_error_update)))
        }
    }

    private fun reply(id: String, result: JSONObject) =
        callback("crticketTicketMonitorResult", id, result)
}
