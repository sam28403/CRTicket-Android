package cc.samlab.ticket.ui.report

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveReportScreen(
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    onExternal: (String) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(ReportKind.BUG) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var includeDevice by rememberSaveable { mutableStateOf(false) }
    val report = IssueReport(
        kind, title, description, steps, expected, BuildConfig.VERSION_NAME,
        if (includeDevice) {
            "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        } else null,
    )
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            // WebViewLayout already reserves the IME area for the native host.
            Column(Modifier.fillMaxSize().testTag("report-screen")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                    Text(
                        stringResource(R.string.report_error),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().testTag("report-viewport"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.report_intro),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            stringResource(R.string.report_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportKind.entries.forEach { option ->
                                FilterChip(
                                    selected = kind == option,
                                    onClick = { kind = option },
                                    label = { Text(option.title) },
                                )
                            }
                        }
                        ReportField(title, { title = it.take(120) }, R.string.report_title, "report-title", true)
                        ReportField(description, { description = it }, R.string.report_description, "report-description")
                        ReportField(steps, { steps = it }, R.string.report_steps, "report-steps")
                        ReportField(expected, { expected = it }, R.string.report_expected, "report-expected")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.report_device))
                                Text(
                                    stringResource(R.string.report_device_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val deviceLabel = stringResource(R.string.report_device)
                            Switch(
                                checked = includeDevice,
                                onCheckedChange = { includeDevice = it },
                                modifier = Modifier.testTag("report-device")
                                    .semantics { contentDescription = deviceLabel },
                            )
                        }
                        ReportActions(report, onExternal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    tag: String,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(20.dp),
    )
}
