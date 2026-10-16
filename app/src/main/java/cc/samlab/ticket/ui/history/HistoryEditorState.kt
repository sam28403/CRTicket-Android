package cc.samlab.ticket.ui.history

import android.os.Bundle
import androidx.compose.runtime.*
import org.json.JSONObject

internal data class HistoryChoice(val value: String, val label: String, val disabled: Boolean = false)

internal class HistoryDraft(val id: String?, value: JSONObject) {
    val fields = mutableStateMapOf<String, String>().apply {
        value.keys().forEach { key -> put(key, if (value.isNull(key)) "" else value.optString(key)) }
    }
    var credit by mutableStateOf(value.optInt("useCredit") == 1)
    var conditioner by mutableStateOf(value.optInt("hasConditioner") == 1)

    init {
        val seat = fields["seatType"].orEmpty()
        if (seat.startsWith("新空调")) {
            fields["seatType"] = seat.removePrefix("新空调")
            conditioner = true
        }
    }

    fun value(key: String) = fields[key].orEmpty()

    fun json(): JSONObject = JSONObject(fields.toMap())
        .put("useCredit", if (credit) 1 else 0)
        .put("hasConditioner", if (conditioner) 1 else 0)
}

internal class HistoryEditorState {
    var draft by mutableStateOf<HistoryDraft?>(null)
        private set
    var deleting by mutableStateOf<HistoryRecord?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
    var backProgress by mutableStateOf(0f)
    val hasDetail get() = draft != null

    fun open(id: String?, value: JSONObject) {
        draft = HistoryDraft(id, value)
        error = ""
        backProgress = 0f
    }

    fun closeDetail(): Boolean {
        if (!hasDetail) return false
        if (!busy) draft = null
        backProgress = 0f
        error = ""
        return true
    }

    fun save(state: Bundle) {
        draft?.let {
            state.putString("historyDraft", it.json().toString())
            state.putString("historyDraftId", it.id)
        }
    }

    fun restore(state: Bundle?) {
        state?.getString("historyDraft")?.let {
            runCatching { open(state.getString("historyDraftId"), JSONObject(it)) }
        }
    }
}
