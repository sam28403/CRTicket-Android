package cc.samlab.ticket.monitor

import android.app.Activity
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

    fun stop(id: String) = change(id) { TicketMonitorService.stop(activity) }

    private fun change(id: String, action: () -> Unit) {
        try {
            action()
            state(id)
        } catch (error: Exception) {
            reply(id, JSONObject().put("success", false)
                .put("message", error.message ?: "无法更新余票监控"))
        }
    }

    private fun reply(id: String, result: JSONObject) =
        callback("crticketTicketMonitorResult", id, result)
}
