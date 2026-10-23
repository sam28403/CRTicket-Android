package cc.samlab.ticket.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

internal data class HomeDestination(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
    val route: String,
)

internal val ticketDestinations = listOf(
    HomeDestination(
        R.string.home_ticket, R.string.home_ticket_description,
        R.drawable.ic_home_ticket, "/",
    ),
    HomeDestination(
        R.string.home_history, R.string.home_history_description,
        R.drawable.ic_home_history, "/history",
    ),
    HomeDestination(
        R.string.home_data, R.string.home_data_description,
        R.drawable.ic_home_data, "/user",
    ),
    HomeDestination(
        R.string.home_monitor, R.string.home_monitor_description,
        R.drawable.ic_home_monitor, "/monitor",
    ),
    HomeDestination(
        R.string.home_train, R.string.home_train_description,
        R.drawable.ic_home_train, "/train",
    ),
    HomeDestination(
        R.string.home_list, R.string.home_list_description,
        R.drawable.ic_home_list, "/list",
    ),
    HomeDestination(
        R.string.home_station, R.string.home_station_description,
        R.drawable.ic_home_station, "/station",
    ),
    HomeDestination(
        R.string.sale_title, R.string.sale_description,
        R.drawable.ic_home_sale_time, "/sale-time",
    ),
)

internal fun homeEntryShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) 24.dp else 4.dp,
    topEnd = if (index == 0) 24.dp else 4.dp,
    bottomStart = if (index == count - 1) 24.dp else 4.dp,
    bottomEnd = if (index == count - 1) 24.dp else 4.dp,
)
