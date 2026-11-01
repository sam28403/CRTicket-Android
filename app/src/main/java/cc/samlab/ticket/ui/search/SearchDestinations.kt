package cc.samlab.ticket.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.ui.settings.MapSource
import cc.samlab.ticket.ui.settings.StorageKind
import androidx.compose.ui.platform.LocalContext
import cc.samlab.ticket.i18n.AppLanguages
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.help.helpTopics
import cc.samlab.ticket.ui.home.ticketDestinations
import cc.samlab.ticket.ui.home.railfanDestinations

@Composable
internal fun additionalSearchSettings(onNavigate: (String) -> Unit): List<SearchSetting> {
    val languages = AppLanguages.supported(LocalContext.current)
    return listOf(
        SearchSetting("language-options", stringResource(R.string.settings_language), "",
            keywords = languages.joinToString("\n") { it.nativeName + " " + it.tag }
                + "\n" + stringResource(R.string.language_system),
        ) { onNavigate("/settings/language") },
        SearchSetting("theme-options", stringResource(R.string.settings_theme), "",
            keywords = listOf(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
            ).joinToString("\n"),
        ) { onNavigate("/settings") },
        SearchSetting("page-0", stringResource(R.string.settings_map), "",
            keywords = MapSource.entries.map { stringResource(it.title) }.joinToString("\n"),
        ) { onNavigate("/settings/map") },
        SearchSetting("page-1", stringResource(R.string.settings_storage), "",
            keywords = listOf(
                *StorageKind.entries.map { stringResource(it.title) }.toTypedArray(),
                stringResource(R.string.storage_categories),
                stringResource(R.string.storage_clear),
                stringResource(R.string.storage_saved),
                stringResource(R.string.storage_personal),
                stringResource(R.string.storage_confirm_title),
                stringResource(R.string.storage_timetable_confirm),
                stringResource(R.string.storage_confirm_message),
                stringResource(R.string.storage_cleanable),
                stringResource(R.string.status_loading),
                stringResource(R.string.storage_clear_all),
            ).joinToString("\n"),
        ) { onNavigate("/settings/storage") },
        SearchSetting("page-2", stringResource(R.string.settings_updates), "",
            keywords = listOf(
                stringResource(R.string.settings_updates),
                stringResource(R.string.update_check),
                stringResource(R.string.update_checking),
                stringResource(R.string.update_previews),
                stringResource(R.string.update_automatic),
            ).joinToString("\n"),
        ) { onNavigate("/settings") },
        SearchSetting("page-3", stringResource(R.string.railfan_tools), "",
            keywords = listOf(
                stringResource(R.string.railfan_tools),
                stringResource(R.string.settings_advanced),
                stringResource(R.string.railfan_tools_description),
            ).joinToString("\n"),
        ) { onNavigate("/settings") },
        SearchSetting("page-4", stringResource(R.string.settings_arrival), "",
            keywords = listOf(
                stringResource(R.string.arrival_enable),
                stringResource(R.string.settings_arrival_switch),
                stringResource(R.string.arrival_range_description),
                stringResource(R.string.arrival_privacy_description),
                stringResource(R.string.arrival_requirements_description),
                stringResource(R.string.arrival_restart),
                stringResource(R.string.arrival_manage_permissions),
            ).joinToString("\n"),
        ) { onNavigate("/settings/arrival") },
        SearchSetting("page-5", stringResource(R.string.settings_sale_reminders), "",
            keywords = listOf(
                stringResource(R.string.sale_alarm_permission),
                stringResource(R.string.sale_mode_alarm),
                stringResource(R.string.sale_mode_notification),
                stringResource(R.string.sale_reminders),
                stringResource(R.string.sale_alarm_warning),
                stringResource(R.string.action_confirm),
                stringResource(R.string.action_cancel),
            ).joinToString("\n"),
        ) { onNavigate("/settings/sale-reminders") },
        SearchSetting("page-6", stringResource(R.string.monitor_settings_title), "",
            keywords = listOf(
                stringResource(R.string.filter_all),
                stringResource(R.string.monitor_toggle),
                stringResource(R.string.monitor_interval),
                stringResource(R.string.duration_seconds),
                stringResource(R.string.monitor_station_time_group),
                stringResource(R.string.monitor_departure_filter),
                stringResource(R.string.monitor_arrival_filter),
                stringResource(R.string.monitor_departure_time_filter),
                stringResource(R.string.monitor_train_seat_group),
                stringResource(R.string.monitor_train_search),
                stringResource(R.string.monitor_seat_filter),
                stringResource(R.string.monitor_seat_all),
                stringResource(R.string.monitor_train_type_filter),
                stringResource(R.string.monitor_train_high_speed),
                stringResource(R.string.monitor_train_ordinary),
                stringResource(R.string.monitor_train_all),
                stringResource(R.string.monitor_available_only),
                stringResource(R.string.monitor_available_only_description),
            ).joinToString("\n"),
        ) { onNavigate("/monitor/settings") },
        SearchSetting("page-7", stringResource(R.string.settings_permissions), "",
            keywords = listOf(
                stringResource(R.string.permissions_notifications_group),
                stringResource(R.string.permissions_notifications),
                stringResource(R.string.permissions_notifications_allowed),
                stringResource(R.string.permissions_not_allowed_settings),
                stringResource(R.string.permissions_live_updates),
                stringResource(R.string.permissions_live_updates_allowed),
                stringResource(R.string.settings_sale_reminders),
                stringResource(R.string.sale_mode_alarm),
                stringResource(R.string.permissions_exact_alarms),
                stringResource(R.string.permissions_dnd),
                stringResource(R.string.permissions_allowed),
                stringResource(R.string.permissions_location_group),
                stringResource(R.string.permissions_location_precise),
                stringResource(R.string.permissions_location_approximate),
                stringResource(R.string.permissions_not_allowed),
                stringResource(R.string.permissions_location),
                stringResource(R.string.permissions_background_location),
                stringResource(R.string.permissions_background_location_allowed),
                stringResource(R.string.permissions_background_location_denied),
                stringResource(R.string.permissions_background_group),
                stringResource(R.string.permissions_battery),
                stringResource(R.string.permissions_battery_unrestricted),
                stringResource(R.string.permissions_battery_optimized),
                stringResource(R.string.permissions_system_group),
                stringResource(R.string.permissions_all),
                stringResource(R.string.permissions_all_description),
            ).joinToString("\n"),
        ) { onNavigate("/settings/permissions") },
    ) + (ticketDestinations + railfanDestinations).map { destination ->
        SearchSetting(destination.route, stringResource(destination.title),
            stringResource(destination.description)) { onNavigate(destination.route) }
    } + listOf(
        SearchSetting("about", stringResource(R.string.home_about), "") { onNavigate("/about") },
        SearchSetting("help", stringResource(R.string.home_help),
            stringResource(R.string.home_help_description)) { onNavigate("/help") },
    )
}

@Composable
internal fun searchHelp(onNavigate: (String) -> Unit): List<SearchSetting> =
    helpTopics.map { topic ->
        SearchSetting("help-${topic.id}", stringResource(topic.title), "",
            keywords = listOf(
                stringResource(topic.question),
                stringResource(topic.answer),
                stringResource(topic.rules),
            ).joinToString("\n"),
        ) { onNavigate(topic.route) }
    }
