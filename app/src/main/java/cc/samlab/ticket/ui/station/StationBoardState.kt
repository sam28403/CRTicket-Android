package cc.samlab.ticket.ui.station

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A destination owns its data as well as its scroll positions during transitions. */
internal class StationBoardState(
    initialStation: String,
    private val dispatch: (StationBoardState, String, String) -> Unit,
) {
    var station by mutableStateOf(initialStation)
    var loading by mutableStateOf(false)
    var error by mutableStateOf("")
    var date by mutableStateOf("")
    var resultStation by mutableStateOf("")
    var tabs by mutableStateOf<Map<StationTab, List<StationBoardRow>>>(emptyMap())
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var revision by mutableStateOf(0)
    private var presentation by mutableStateOf(StationBoardPresentation())
    private var presentationDate = ""
    var selectedTab: StationTab
        get() = presentation.selectedTab
        set(value) { presentation.selectedTab = value }
    var follow: Boolean
        get() = presentation.follow
        set(value) { presentation.follow = value }
    val gridState get() = presentation.grids.getValue(selectedTab)

    fun editStation(value: String) {
        station = value.take(30)
        suggestions = emptyList()
        dispatch(this, "suggest", station)
    }

    fun selectStation(value: String) {
        station = value
        suggestions = emptyList()
    }

    fun request() {
        if (loading) return
        suggestions = emptyList()
        dispatch(this, "query", station)
    }

    fun preparePresentation(date: String) {
        if (presentationDate.isNotEmpty() && presentationDate != date) {
            presentation = StationBoardPresentation()
        }
        presentationDate = date
    }
}
