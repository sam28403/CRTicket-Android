package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R

/** Keep selected options and progress when only the answer changes. */
internal fun repairQuizStationCounts(
    session: QuizSession,
    counts: Map<String, Int>,
    replacements: () -> List<QuizQuestion>,
): QuizSession {
    val used = session.questions.map { it.id }.toMutableSet()
    val alternatives by lazy { replacements().iterator() }
    val replaced = mutableSetOf<Int>()
    val questions = session.questions.mapIndexed { index, question ->
        if (question.category != 3) return@mapIndexed question
        val values = question.options.map { counts[it.label] ?: 0 }
        val maximum = question.id.startsWith("station-count:true:")
        val extreme = if (maximum) values.max() else values.min()
        val winner = values.indices.filter { values[it] == extreme }
        if (counts.isNotEmpty() && winner.size == 1 && quizComparableCounts(values)) {
            question.copy(
                correctIndex = winner.single(),
                statement = QuizText(R.string.quiz_f_station_count,
                    listOf(question.options[winner.single()].label, extreme.toString())),
            )
        } else {
            val replacement = alternatives.asSequence().first { it.category != 3 && used.add(it.id) }
            replaced.add(index)
            replacement
        }
    }
    val answers = session.answers.mapIndexed { i, answer -> if (i in replaced) -1 else answer }
    val times = session.answerTimes.mapIndexed { i, time -> if (i in replaced) null else time }
    val current = if (session.completed && replaced.isNotEmpty()) replaced.min() else session.current
    return session.copy(questions = questions, answers = answers, answerTimes = times,
        current = current, completed = session.completed && replaced.isEmpty())
}
