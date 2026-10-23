package cc.samlab.ticket.ui.help

import cc.samlab.ticket.R

internal data class HelpTopic(
    val id: String,
    val title: Int,
    val question: Int,
    val answer: Int,
    val rules: Int,
) {
    val route get() = "/help/$id"
}

internal val helpTopics = listOf(
    HelpTopic("tickets", R.string.help_tickets_title,
        R.string.help_tickets_question, R.string.help_tickets_answer, R.string.help_tickets_rules),
    HelpTopic("query", R.string.help_query_title,
        R.string.help_query_question, R.string.help_query_answer, R.string.help_query_rules),
    HelpTopic("backup", R.string.help_backup_title,
        R.string.help_backup_question, R.string.help_backup_answer, R.string.help_backup_rules),
    HelpTopic("arrival", R.string.help_arrival_title,
        R.string.help_arrival_question, R.string.help_arrival_answer, R.string.help_arrival_rules),
    HelpTopic("monitor", R.string.help_monitor_title,
        R.string.help_monitor_question, R.string.help_monitor_answer, R.string.help_monitor_rules),
    HelpTopic("sale", R.string.help_sale_title,
        R.string.help_sale_question, R.string.help_sale_answer, R.string.help_sale_rules),
    HelpTopic("speed", R.string.help_speed_title,
        R.string.help_speed_question, R.string.help_speed_answer, R.string.help_speed_rules),
    HelpTopic("vehicle", R.string.help_vehicle_title,
        R.string.help_vehicle_question, R.string.help_vehicle_answer, R.string.help_vehicle_rules),
    HelpTopic("general", R.string.help_general_title,
        R.string.help_general_question, R.string.help_general_answer, R.string.help_general_rules),
)
