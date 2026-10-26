package cc.samlab.ticket.ui.quiz

import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

/** A semantic ID owns one of 32 fixed days; changing distractors cannot bypass the cooldown. */
internal fun quizHash(value: String): Long {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.take(7).fold(0L) { result, byte -> result * 256 + (byte.toInt() and 255) }
}

internal fun quizEligible(id: String, date: LocalDate): Boolean =
    quizHash(id) % 32 == Math.floorMod(date.toEpochDay(), 32L)

internal fun <T> quizPicks(values: List<T>, seed: String, count: Int): List<T> {
    if (values.isEmpty()) return emptyList()
    val indices = mutableSetOf<Int>()
    val output = mutableListOf<T>()
    var round = 0
    while (output.size < minOf(count, values.size)) {
        val index = (quizHash("$seed:${round++}") % values.size).toInt()
        if (indices.add(index)) output.add(values[index])
    }
    return output
}

internal fun quizQuestion(
    id: String,
    category: Int,
    title: QuizText,
    correct: QuizChoice,
    others: List<QuizChoice>,
    statement: QuizText,
): QuizQuestion? {
    val wrong = others.distinctBy { it.label }.filter { it.label != correct.label }.take(3)
    if (wrong.size != 3) return null
    val options = (wrong + correct).sortedWith(compareBy<QuizChoice> {
        quizHash("$id:option:${it.label}")
    }.thenBy { it.label })
    return QuizQuestion(id, category, title, options, options.indexOf(correct), statement)
}

internal fun quizNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(Locale.ROOT, "%.1f", value)

internal fun quizClock(minutes: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minutes / 60 % 24, minutes % 60)
