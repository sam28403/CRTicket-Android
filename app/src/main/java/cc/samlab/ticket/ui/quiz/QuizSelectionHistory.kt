package cc.samlab.ticket.ui.quiz

/** Compact semantic fingerprints survive CI resets without retaining full old question sets. */
internal fun quizSelectionKeys(question: QuizQuestion): Set<String> = buildSet {
    add("q:${quizHash(question.id).toString(16)}")
    val options = question.options
    if (options.all { it.kind != QuizOptionKind.TEXT } || question.category == 9) {
        val labels = options.map { "${it.kind}:${it.label}" }.sorted()
        // Sharing three of four objects is still essentially the same comparison.
        for (omitted in labels.indices) {
            val group = labels.filterIndexed { index, _ -> index != omitted }.joinToString("\u001f")
            add("o:${quizHash(group).toString(16)}")
        }
    }
}

internal fun quizSelectionKeys(questions: List<QuizQuestion>): Set<String> =
    questions.flatMapTo(mutableSetOf(), ::quizSelectionKeys)
