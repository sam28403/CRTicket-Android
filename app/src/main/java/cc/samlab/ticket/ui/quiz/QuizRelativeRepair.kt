package cc.samlab.ticket.ui.quiz

internal fun repairQuizRelativeQuestions(session: QuizSession, sources: QuizSources): QuizSession {
    val stations = sources.stations.associateBy { it.name }
    val neighborhoods by lazy { QuizGeographyIndex.neighbors(sources.stations) }
    val alternatives by lazy {
        DailyQuizGenerator(sources).generate(
            session.date, excludedIds = session.questions.map { it.id }.toSet(),
        ).iterator()
    }
    val used = session.questions.map { it.id }.toMutableSet()
    val changed = mutableSetOf<Int>()
    val questions = session.questions.mapIndexed { index, question ->
        if (question.category != 6) return@mapIndexed question
        val direction = QuizDirection.entries.firstOrNull {
            question.id.startsWith("relative:${it.name}:")
        }
        val reference = stations[question.title.arguments.firstOrNull()]
        val options = question.options.mapNotNull { stations[it.label] }
        if (reference != null && direction != null &&
            quizComparableRelativeChoices(reference, options, direction)) return@mapIndexed question
        changed.add(index)
        val replacement = if (reference != null && direction != null) {
            quizRelativeQuestion(reference, neighborhoods.getValue(reference.name), direction)
        } else null
        replacement ?: alternatives.asSequence().first { used.add(it.id) }
    }
    if (changed.isEmpty()) return session
    return session.copy(
        questions = questions,
        answers = session.answers.mapIndexed { index, answer -> if (index in changed) -1 else answer },
        answerTimes = session.answerTimes.mapIndexed { index, time -> if (index in changed) null else time },
        current = if (session.completed) changed.min() else session.current,
        completed = false,
    )
}
