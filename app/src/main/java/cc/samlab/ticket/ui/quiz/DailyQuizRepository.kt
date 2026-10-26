package cc.samlab.ticket.ui.quiz

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

internal fun quizToday(): LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))

internal class DailyQuizRepository(private val context: Context) {
    private val folder = File(context.filesDir, "daily-quiz")

    suspend fun load(date: LocalDate): QuizSession = lock.withLock {
        withContext(Dispatchers.IO) {
            read(date) ?: run {
                val sources = QuizSourceReader.load(context)
                val vehicles = loadQuizVehicles(context, sources, date)
                val questions = DailyQuizGenerator(sources).generate(date, vehicles)
                QuizSession(date, sources.version, questions).also(::write)
            }
        }
    }

    suspend fun save(session: QuizSession) = lock.withLock {
        withContext(Dispatchers.IO) { write(session) }
    }

    suspend fun saved(date: LocalDate): QuizSession? = lock.withLock {
        withContext(Dispatchers.IO) { read(date) }
    }

    suspend fun reset(current: QuizSession, replaceQuestions: Boolean): QuizSession = lock.withLock {
        check(cc.samlab.ticket.BuildConfig.BUILD_TYPE == "ci")
        withContext(Dispatchers.IO) {
            val next = if (replaceQuestions) {
                val sources = QuizSourceReader.load(context)
                val vehicles = loadQuizVehicles(context, sources, current.date)
                val questions = DailyQuizGenerator(sources).generate(
                    current.date, vehicles, current.questions.map { it.id }.toSet(),
                )
                QuizSession(current.date, sources.version, questions)
            } else current.resetProgress()
            write(next)
            next
        }
    }

    private fun read(date: LocalDate): QuizSession? = runCatching {
        QuizSessionCodec.decode(file(date).openRead().bufferedReader().use { it.readText() }, date)
    }.getOrNull()

    private fun file(date: LocalDate): AtomicFile = AtomicFile(File(folder, "$date.json"))

    private fun write(session: QuizSession) {
        check(folder.isDirectory || folder.mkdirs())
        val target = file(session.date)
        val stream = target.startWrite()
        try {
            stream.write(QuizSessionCodec.encode(session).toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }

    private companion object { val lock = Mutex() }
}
