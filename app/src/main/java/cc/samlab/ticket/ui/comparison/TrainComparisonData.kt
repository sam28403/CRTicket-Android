package cc.samlab.ticket.ui.comparison

import android.content.Context
import cc.samlab.ticket.ui.trainlist.TrainListRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

internal data class TrainNumberComparison(
    val old: String,
    val from: String,
    val to: String,
    val new: String,
) {
    val card = TrainListRow(new, from, to, "", "", "", "", 0, null, null, null)
}

internal fun validComparisonNumber(value: String): Boolean =
    Regex("(?:[0-9]{4}|[ZTKNV][0-9]{1,4})").matches(value)

internal fun validComparisonInput(value: String): Boolean =
    Regex("(?:[0-9]{0,4}|[ZTKNV][0-9]{0,4})").matches(value)

internal fun normalizeComparisonNumber(value: String): String =
    value.trim().uppercase(Locale.ROOT)

internal class TrainComparisonIndex(rows: List<TrainNumberComparison>) {
    val rows = rows.sortedWith(
        compareBy<TrainNumberComparison> { "ZNV".indexOf(it.new.first()) }
            .thenBy { it.new.drop(1).toInt() }
            .thenBy { it.old },
    )
    private val matches = buildMap<String, MutableList<Int>> {
        this@TrainComparisonIndex.rows.forEachIndexed { index, row ->
            setOf(row.old, row.new).forEach { number ->
                getOrPut(number) { mutableListOf() }.add(index)
            }
        }
    }

    fun find(number: String): List<Int> = matches[number].orEmpty()
}

internal suspend fun loadTrainComparison(context: Context): TrainComparisonIndex =
    withContext(Dispatchers.IO) {
        val data = context.assets.open("train-number-comparison.json")
            .bufferedReader().use { JSONArray(it.readText()) }
        TrainComparisonIndex(List(data.length()) { index ->
            val row = data.getJSONArray(index)
            TrainNumberComparison(
                row.getString(0), row.getString(1), row.getString(2), row.getString(3),
            )
        })
    }
