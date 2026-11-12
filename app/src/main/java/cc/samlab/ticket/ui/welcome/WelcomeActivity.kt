package cc.samlab.ticket.ui.welcome

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme

class WelcomeActivity : AppCompatActivity() {
    private val backupPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) completeWelcome(uri)
    }

    companion object {
        private const val EXTRA_RELEASE_NOTES_ONLY = "release_notes_only"

        fun releaseNotesIntent(context: Context): Intent =
            Intent(context, WelcomeActivity::class.java)
                .putExtra(EXTRA_RELEASE_NOTES_ONLY, true)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CRTicket)
        super.onCreate(savedInstanceState)
        val releaseNotesOnly = intent.getBooleanExtra(EXTRA_RELEASE_NOTES_ONLY, false)
        val firstUse = !releaseNotesOnly && WelcomeState.needsTerms(this)
        val changes = runCatching {
            assets.open("changelogs/${WelcomeState.version}.md")
                .bufferedReader().use { it.readText() }
        }.getOrElse { getString(R.string.welcome_changes_fallback) }
        setContent {
            MaterialExpressiveTheme(colorScheme = homeColorScheme(this, cc.samlab.ticket.ui.ThemePreference.isDark(this),
                cc.samlab.ticket.ui.ThemePreference.isPureBlack(this))) {
                WelcomeScreen(
                    firstUse = firstUse,
                    changes = changes,
                    releaseNotesOnly = releaseNotesOnly,
                    onExit = { if (releaseNotesOnly) finish() else finishAndRemoveTask() },
                    onImportBackup = {
                        try {
                            backupPicker.launch("application/json")
                        } catch (_: Exception) {
                            Toast.makeText(
                                this, R.string.settings_file_picker_failed, Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) {
                    if (releaseNotesOnly) {
                        finish()
                        return@WelcomeScreen
                    }
                    completeWelcome()
                }
            }
        }
    }

    private fun completeWelcome(backup: Uri? = null) {
        WelcomeState.complete(this)
        startActivity(Intent(this, MainActivity::class.java).apply {
            intent.extras?.let { putExtras(it) }
            backup?.let {
                putExtra(MainActivity.EXTRA_WELCOME_BACKUP, it.toString())
                clipData = ClipData.newRawUri("backup", it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        })
        finish()
    }
}
