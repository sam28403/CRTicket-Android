package cc.samlab.ticket.ui.statistics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.YearMonth

internal class StatisticsUiState {
    var empty by mutableStateOf(true)
        private set
    var loading by mutableStateOf(true)
        private set
    var monthRequest by mutableStateOf<String?>(null)
        private set
    var monthPickerSession by mutableStateOf(0)
        private set

    fun chooseMonth(initial: String) {
        val month = runCatching { YearMonth.parse(initial) }.getOrNull()
            ?.takeIf { it.year in 1..9999 } ?: YearMonth.now()
        monthPickerSession++
        monthRequest = month.toString()
    }

    fun dismissMonth() {
        monthRequest = null
    }

    fun update(empty: Boolean, loading: Boolean) {
        this.empty = empty
        this.loading = loading
    }
}
