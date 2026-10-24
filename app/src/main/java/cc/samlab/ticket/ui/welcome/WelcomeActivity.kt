package cc.samlab.ticket.ui.welcome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme

class WelcomeActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_RELEASE_NOTES_ONLY = "release_notes_only"

        fun releaseNotesIntent(context: Context): Intent =
            Intent(context, WelcomeActivity::class.java)
                .putExtra(EXTRA_RELEASE_NOTES_ONLY, true)
    }

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
            MaterialTheme(colorScheme = homeColorScheme(LocalContext.current, isSystemInDarkTheme())) {
                WelcomeScreen(
                    firstUse = firstUse,
                    changes = changes,
                    releaseNotesOnly = releaseNotesOnly,
                    onExit = { if (releaseNotesOnly) finish() else finishAndRemoveTask() },
                ) {
                    if (releaseNotesOnly) {
                        finish()
                        return@WelcomeScreen
                    }
                    WelcomeState.complete(this)
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        intent.extras?.let { putExtras(it) }
                    })
                    finish()
                }
            }
        }
    }
}
