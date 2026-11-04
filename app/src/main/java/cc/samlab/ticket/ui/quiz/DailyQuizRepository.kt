package cc.samlab.ticket.ui.quiz

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal fun quizToday(): LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))

internal class DailyQuizRepository(
    private val context: Context,
    private val liveData: suspend (LocalDate) -> QuizLiveData? = { null },
) {
    private val folder = File(context.filesDir, "daily-quiz")

    suspend fun load(date: LocalDate): QuizSession = lock.withLock {
        withContext(Dispatchers.IO) {
            val live = requireNotNull(liveData(date)) { "Fresh quiz data unavailable" }
            val sources = live.verifiedSources(QuizSourceReader.load(context), quizToday())
            require(date == live.date)
            val history = recentHistory(date)
            val vehicles = loadQuizVehicles(context, sources, date)
            check(date == quizToday()) { "Quiz date changed" }
            val version = "live-v1:${sources.version}"
            val original = read(date)
            val generator = DailyQuizGenerator(sources)
            fun questions(extra: Set<String> = emptySet()) = generator.generate(
                date, vehicles, seenKeys = history + extra, stationCounts = live.stationCounts,
            )
            val session = if (original == null || original.sourceVersion != version ||
                original.questions.any { question ->
                    quizSelectionKeys(question).any { it in history } ||
                        (question.category == 9 && vehicles.none { it == question })
                }) {
                val next = questions(original?.seenKeys.orEmpty())
                quizRefreshSession(original, date, version, next)
            } else {
                val eligible = repairQuizPassengerStations(original, sources) { questions() }
                val relative = repairQuizRelativeQuestions(eligible, sources)
                repairQuizStationCounts(relative, live.stationCounts) {
                    questions(quizSelectionKeys(original.questions))
                }
            }
            check(date == quizToday()) { "Quiz date changed" }
            session.also(::write)
        }
    }

    suspend fun save(session: QuizSession) = lock.withLock {
        withContext(Dispatchers.IO) { write(session) }
    }

    suspend fun saved(date: LocalDate): QuizSession? = lock.withLock {
        withContext(Dispatchers.IO) { read(date) }
    }

    suspend fun reset(current: QuizSession, replaceQuestions: Boolean): QuizSession = lock.withLock {
        check(cc.samlab.ticket.BuildConfig.CI_BUILD)
        // Finish an accepted reset even if leaving the screen cancels its caller.
        // Keep the repository lock until the new session is persisted so reloads wait for it.
        withContext(Dispatchers.IO + NonCancellable) {
            val next = if (replaceQuestions) {
                val live = requireNotNull(liveData(current.date)) { "Fresh quiz data unavailable" }
                val sources = live.verifiedSources(QuizSourceReader.load(context), quizToday())
                val vehicles = loadQuizVehicles(context, sources, current.date)
                val history = current.seenKeys + quizSelectionKeys(current.questions)
                val questions = DailyQuizGenerator(sources).generate(
                    current.date, vehicles,
                    seenKeys = history + recentHistory(current.date),
                    variationSeed = UUID.randomUUID().toString(),
                    stationCounts = live.stationCounts,
                )
                QuizSession(
                    current.date, "live-v1:${sources.version}", questions,
                    seenKeys = history + quizSelectionKeys(questions),
                )
            } else current.resetProgress()
            require(next.seenKeys.size <= 20_000)
            check(current.date == quizToday()) { "Quiz date changed" }
            write(next)
            next
        }
    }

    private fun recentHistory(date: LocalDate): Set<String> = buildSet {
        for (day in quizHistoryDates(date)) {
            val target = file(day)
            if (!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) continue
            // Never silently lose the cooldown when an existing history file is unreadable.
            val session = QuizSessionCodec.decode(
                target.openRead().bufferedReader().use { it.readText() }, day,
            )
            addAll(quizSelectionKeys(session.questions))
            addAll(session.seenKeys)
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
