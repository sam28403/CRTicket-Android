package cc.samlab.ticket.ui.quiz

import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuizScreen(
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    onBack: () -> Unit,
    active: Boolean = true,
    onDone: () -> Unit = onBack,
    onReport: (LocalDate, Int) -> Unit = { _, _ -> },
    shareProfile: QuizShareProfile? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current
    val state = remember(context.applicationContext) {
        DailyQuizState(DailyQuizRepository(context.applicationContext))
    }
    val scope = rememberCoroutineScope()
    var resetDialog by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val onIncomplete: (Int) -> Unit = { remaining ->
        Toast.makeText(context, resources.getString(R.string.quiz_unfinished, remaining), Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(active) {
        if (active) while (true) {
            state.load(quizToday())
            delay(30_000)
        }
    }
    LaunchedEffect(state.pendingCorrect, active) {
        val id = state.pendingCorrect
        if (active && id != null) {
            delay(650)
            state.move(1, expectedCorrect = id, onIncomplete = onIncomplete)
        }
    }

    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.quiz_title), fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                        }
                    },
                    actions = {
                        if (state.session?.completed == true) IconButton(
                            onClick = { sharing = true },
                            enabled = !state.busy && !sharing && shareProfile != null,
                            modifier = Modifier.testTag("quiz-share"),
                        ) {
                            Icon(Icons.Outlined.Share, stringResource(R.string.quiz_share))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                val session = state.session
                when {
                    state.failed -> Column(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(R.string.quiz_error))
                        TextButton(onClick = { scope.launch { state.load(quizToday()) } }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    session == null || state.resetting -> Box(
                        Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator()
                            if (state.resetting) Text(stringResource(R.string.quiz_reset_loading))
                        }
                    }
                    else -> QuizContent(
                        session, state.busy, state.wrongFeedback, Modifier.weight(1f),
                        onAnswer = { page, choice ->
                            scope.launch {
                                state.answer(page, choice) {
                                    view.performHapticFeedback(
                                        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                                        else HapticFeedbackConstants.LONG_PRESS,
                                    )
                                }
                            }
                        },
                        onMove = { offset -> scope.launch { state.move(offset, onIncomplete = onIncomplete) } },
                        onDone = onDone,
                        onReport = { onReport(session.date, it) },
                        onReset = if (BuildConfig.BUILD_TYPE == "ci") {
                            { resetDialog = true }
                        } else null,
                    )
                }
            }
        }
        if (resetDialog && BuildConfig.BUILD_TYPE == "ci") QuizResetDialog(
            onDismiss = { resetDialog = false },
            onReset = { replaceQuestions ->
                resetDialog = false
                scope.launch { state.reset(replaceQuestions) }
            },
        )
        val shareSession = state.session
        if (sharing && shareSession?.completed == true && shareProfile != null) {
            QuizShareCapture(shareSession, shareProfile) { sharing = false }
        }
    }
}

