package cc.samlab.ticket.ui.quiz

/** Use the whole timetable validity range, rather than today's services. */
internal fun QuizSources.passengerStationNames(): Set<String> = trains.asSequence()
    .filter { train ->
        val start = train.schedule.start
        val end = train.schedule.end
        start != null && end != null && start <= end &&
            (0L..minOf(63L, java.time.temporal.ChronoUnit.DAYS.between(start, end)))
                .any { train.schedule.runs(start.plusDays(it)) }
    }
    .flatMap { it.stops.asSequence() }
    .filter { it.arrival != null || it.departure != null }
    .map { it.station }
    .toSet()

internal fun QuizQuestion.usesExcludedStation(excluded: Set<String>): Boolean =
    options.any { it.label in excluded || it.from in excluded || it.to in excluded } ||
        title.arguments.any { it in excluded } || statement.arguments.any { it in excluded }

internal fun repairQuizPassengerStations(
    session: QuizSession,
    sources: QuizSources,
    replacements: () -> List<QuizQuestion>,
): QuizSession {
    val excluded = sources.stations.map { it.name }.toSet() - sources.passengerStationNames()
    val invalid = session.questions.indices.filter { session.questions[it].usesExcludedStation(excluded) }.toSet()
    if (invalid.isEmpty()) return session
    val used = session.questions.map { it.id }.toMutableSet()
    val alternatives = replacements().filter { used.add(it.id) }.iterator()
    return session.copy(
        questions = session.questions.mapIndexed { index, question ->
            if (index in invalid) alternatives.next() else question
        },
        answers = session.answers.mapIndexed { index, answer -> if (index in invalid) -1 else answer },
        answerTimes = session.answerTimes.mapIndexed { index, time -> if (index in invalid) null else time },
        current = if (session.completed) invalid.min() else session.current,
        completed = false,
    )
}
