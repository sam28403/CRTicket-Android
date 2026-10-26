package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuizReportScreen(
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    onExternal: (String) -> Unit,
    date: String,
    questionIndex: String,
) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales.toLanguageTags()
    var session by remember(date, questionIndex) { mutableStateOf<QuizSession?>(null) }
    var loaded by remember(date, questionIndex) { mutableStateOf(false) }
    var reason by rememberSaveable(date, questionIndex) { mutableStateOf("") }
    val index = questionIndex.toIntOrNull() ?: -1
    LaunchedEffect(date, questionIndex) {
        session = try {
            val day = LocalDate.parse(date)
            DailyQuizRepository(context.applicationContext).saved(day)?.takeIf { quizCanReport(it, index) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        loaded = true
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize().testTag("quiz-report-screen")) {
                TopAppBar(
                    title = { Text(stringResource(R.string.quiz_report_error), fontFamily = projectTitleFont) },
                    navigationIcon = {
                        FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                val saved = session
                when {
                    !loaded -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    saved == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.quiz_report_unavailable))
                    }
                    else -> {
                        val details = remember(saved, index, language) { quizIssueReport(context, saved, index, "") }
                        QuizReportForm(details.copy(reason = reason), { reason = it }, onExternal)
                    }
                }
            }
        }
    }
}
