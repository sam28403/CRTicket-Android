package cc.samlab.ticket.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import cc.samlab.ticket.ui.ThemePreference
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme

class ErrorReportActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_THEME = "error_report_theme"

        internal fun intent(context: Context): Intent =
            Intent(context, ErrorReportActivity::class.java).putExtra(
                EXTRA_THEME,
                context.getSharedPreferences("android", Context.MODE_PRIVATE)
                    .getString("themePreference", "classic-system"),
            )
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = runCatching { ErrorReports.file(this).readText() }.getOrDefault("")
        // The diagnostics process may still cache preferences from an earlier report.
        val preference = intent.getStringExtra(EXTRA_THEME)
        setContent {
            MaterialExpressiveTheme(colorScheme = homeColorScheme(
                this,
                ThemePreference.resolveDark(preference, androidx.compose.foundation.isSystemInDarkTheme()),
                preference?.startsWith("contrast-") == true,
            )) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.failure_title), style = MaterialTheme.typography.headlineMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                    ClipData.newPlainText(getString(R.string.home_title), report),
                                )
                            }) { Text(stringResource(R.string.failure_copy)) }
                            Button(onClick = {
                                startActivity(Intent(this@ErrorReportActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                })
                                finish()
                            }) { Text(stringResource(R.string.failure_restart)) }
                        }
                        SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Text(report, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
