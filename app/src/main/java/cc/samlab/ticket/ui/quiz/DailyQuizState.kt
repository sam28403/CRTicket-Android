package cc.samlab.ticket.ui.quiz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

internal class DailyQuizState(
    private val repository: DailyQuizRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    var session by mutableStateOf<QuizSession?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var resetting by mutableStateOf(false)
        private set
    var pendingCorrect by mutableStateOf<String?>(null)
        private set
    var wrongFeedback by mutableStateOf<String?>(null)
        private set
    private val lock = Mutex()

    suspend fun load(date: LocalDate) = lock.withLock {
        if (session?.date == date && !failed) return@withLock
        session = null
        pendingCorrect = null
        wrongFeedback = null
        attempt { session = repository.load(date) }
    }

    suspend fun answer(page: Int, choice: Int, onWrong: () -> Unit) = lock.withLock {
        val current = session ?: return@withLock
        if (current.completed || current.current != page || current.answers[page] >= 0) return@withLock
        val next = current.copy(
            answers = current.answers.mapIndexed { index, old -> if (index == page) choice else old },
            answerTimes = current.answerTimes.mapIndexed { index, old ->
                if (index == page) nowMillis() else old
            },
        )
        attempt {
            repository.save(next)
            session = next
            val question = current.questions[page]
            if (choice == question.correctIndex) pendingCorrect = question.id
            else {
                wrongFeedback = question.id
                onWrong()
            }
        }
    }

    suspend fun move(
        offset: Int,
        expectedCorrect: String? = null,
        onIncomplete: (Int) -> Unit = {},
    ) = lock.withLock {
        val current = session ?: return@withLock
        if (current.completed || (expectedCorrect != null && pendingCorrect != expectedCorrect)) return@withLock
        val page = (current.current + offset).coerceIn(0, current.questions.size)
        if (page == current.questions.size && current.remaining > 0) {
            pendingCorrect = null
            onIncomplete(current.remaining)
            return@withLock
        }
        val next = current.copy(current = page, completed = page == current.questions.size)
        attempt {
            repository.save(next)
            session = next
            pendingCorrect = null
            wrongFeedback = null
        }
    }

    suspend fun reset(replaceQuestions: Boolean = false) = lock.withLock {
        if (cc.samlab.ticket.BuildConfig.BUILD_TYPE != "ci") return@withLock
        val current = session?.takeIf { it.completed } ?: return@withLock
        resetting = true
        try {
            attempt {
                session = repository.reset(current, replaceQuestions)
                pendingCorrect = null
                wrongFeedback = null
            }
        } finally {
            resetting = false
        }
    }

    private suspend fun attempt(action: suspend () -> Unit) {
        busy = true
        failed = false
        try { action() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { failed = true } finally { busy = false }
    }
}
