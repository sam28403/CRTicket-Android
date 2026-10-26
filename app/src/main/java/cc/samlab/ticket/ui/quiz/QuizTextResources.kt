package cc.samlab.ticket.ui.quiz

import android.content.Context

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R

internal val quizTemplates = mapOf(
    "extreme-q" to R.string.quiz_q_extreme,
    "extreme-f" to R.string.quiz_f_extreme,
    "relative-q" to R.string.quiz_q_relative,
    "relative-f" to R.string.quiz_f_relative,
    "train-metric-q" to R.string.quiz_q_train_metric,
    "train-metric-f" to R.string.quiz_f_train_metric,
    "closest-q" to R.string.quiz_q_closest,
    "closest-f" to R.string.quiz_f_closest,
    "station-most-q" to R.string.quiz_q_station_most,
    "station-least-q" to R.string.quiz_q_station_least,
    "station-count-f" to R.string.quiz_f_station_count,
    "line-distance-q" to R.string.quiz_q_line_distance,
    "line-distance-f" to R.string.quiz_f_line_distance,
    "line-member-q" to R.string.quiz_q_line_member,
    "line-member-f" to R.string.quiz_f_line_member,
    "next-q" to R.string.quiz_q_next_train,
    "next-f" to R.string.quiz_f_next_train,
    "vehicle-q" to R.string.quiz_q_vehicle,
    "vehicle-f" to R.string.quiz_f_vehicle,
    "travel-q" to R.string.quiz_q_travel,
    "travel-f" to R.string.quiz_f_travel,
)
private val quizTokens = mapOf(
    "@quiz_max_distance" to R.string.quiz_max_distance,
    "@quiz_max_duration" to R.string.quiz_max_duration,
    "@quiz_max_speed" to R.string.quiz_max_speed,
    "@quiz_north" to R.string.quiz_north,
    "@quiz_south" to R.string.quiz_south,
    "@quiz_west" to R.string.quiz_west,
    "@quiz_east" to R.string.quiz_east,
    "@train_list_sort_mileage" to R.string.train_list_sort_mileage,
    "@train_list_sort_duration" to R.string.train_list_sort_duration,
    "@train_list_sort_speed" to R.string.train_list_sort_speed,
)

@Composable
internal fun QuizText.render(): String {
    val values = arguments.map { argument ->
        quizTokens[argument]?.let { stringResource(it) } ?: argument
    }
    return stringResource(resource, *values.toTypedArray())
}

internal fun QuizText.resolve(context: Context): String {
    val values = arguments.map { argument -> quizTokens[argument]?.let(context::getString) ?: argument }
    return context.getString(resource, *values.toTypedArray())
}
