package cc.samlab.ticket.ui.settings

import android.content.Context
import cc.samlab.ticket.R
import org.json.JSONObject

// The WebView performs settings I/O; its native-page messages share Android resources.
internal fun settingsTextResources(context: Context): String {
    val resources = mapOf(
        "read_failed" to R.string.settings_user_read_failed,
        "arrival_unsupported" to R.string.arrival_unsupported,
        "save_failed" to R.string.settings_save_failed,
        "saved" to R.string.settings_saved,
        "arrival_failed" to R.string.arrival_change_failed,
        "arrival_updated" to R.string.arrival_updated,
        "export_empty" to R.string.settings_export_empty,
        "export_sheet" to R.string.settings_export_sheet,
        "export_filename" to R.string.settings_export_filename,
        "backup_filename" to R.string.settings_backup_filename,
        "export_size_limit" to R.string.settings_export_size_limit,
        "export_read_failed" to R.string.settings_export_read_failed,
        "file_save_failed" to R.string.files_save_failed,
        "unknown_action" to R.string.settings_unknown_action,
        "file_saved" to R.string.settings_file_saved,
        "file_cancelled" to R.string.settings_file_cancelled,
        "operation_failed" to R.string.settings_operation_failed,
        "backup_no_tickets" to R.string.settings_backup_no_tickets,
        "backup_limit" to R.string.settings_backup_limit,
        "import_failed" to R.string.settings_import_failed,
        "import_complete" to R.string.settings_import_complete,
        "backup_crypto_unavailable" to R.string.backup_crypto_unavailable,
        "backup_crypto_invalid" to R.string.backup_crypto_invalid,
        "backup_crypto_version" to R.string.backup_crypto_version,
        "backup_crypto_failed" to R.string.backup_crypto_failed,
        "backup_crypto_size" to R.string.backup_crypto_size,
        "backup_password_invalid" to R.string.backup_password_invalid,
    )
    return JSONObject().apply {
        resources.forEach { (key, id) -> put(key, context.getString(id)) }
    }.toString()
}
