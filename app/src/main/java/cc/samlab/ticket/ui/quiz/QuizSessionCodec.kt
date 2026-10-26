package cc.samlab.ticket.ui.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal object QuizSessionCodec {
    fun encode(session: QuizSession): String = JSONObject().apply {
        put("schema", 1)
        put("date", session.date.toString())
        put("sourceVersion", session.sourceVersion)
        put("current", session.current)
        put("completed", session.completed)
        put("answers", JSONArray(session.answers))
        put("answerTimes", JSONArray(session.answerTimes))
        put("seenKeys", JSONArray(session.seenKeys.sorted()))
        put("questions", JSONArray().apply {
            session.questions.forEach { question -> put(JSONObject().apply {
                put("id", question.id)
                put("category", question.category)
                put("title", question.title.json())
                put("statement", question.statement.json())
                put("correctIndex", question.correctIndex)
                put("options", JSONArray().apply {
                    question.options.forEach { choice -> put(JSONObject().apply {
                        put("label", choice.label)
                        put("kind", choice.kind.name)
                        put("from", choice.from)
                        put("to", choice.to)
                    }) }
                })
            }) }
        })
    }.toString()

    fun decode(text: String, date: LocalDate): QuizSession {
        require(text.length <= 512 * 1024)
        val json = JSONObject(text)
        require(json.getInt("schema") == 1 && json.getString("date") == date.toString())
        val rows = json.getJSONArray("questions")
        require(rows.length() == 20)
        val questions = List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val choices = row.getJSONArray("options")
            require(choices.length() == 4)
            QuizQuestion(
                row.getString("id"), row.getInt("category"), row.getJSONObject("title").quizText(),
                List(4) { option -> choices.getJSONObject(option).let {
                    QuizChoice(it.getString("label"), QuizOptionKind.valueOf(it.getString("kind")),
                        it.optString("from"), it.optString("to"))
                } },
                row.getInt("correctIndex"), row.getJSONObject("statement").quizText(),
            ).also {
                require(it.correctIndex in 0..3 && it.category in 1..10)
                require(it.options.map { choice -> choice.label }.distinct().size == 4)
            }
        }
        require(questions.map { it.id }.distinct().size == 20)
        val values = json.getJSONArray("answers")
        require(values.length() == questions.size)
        val answers = List(values.length()) { values.getInt(it).also { answer -> require(answer in -1..3) } }
        val times = json.optJSONArray("answerTimes")
        require(times == null || times.length() == questions.size)
        val answerTimes = List(questions.size) { index ->
            if (times == null || times.isNull(index)) null
            else times.getLong(index).also { require(it > 0 && answers[index] >= 0) }
        }
        val completed = json.getBoolean("completed")
        val current = json.getInt("current")
        require(current in 0..questions.size)
        require(completed == (current == questions.size))
        // Older versions allowed skipped questions in a completed session.
        val unfinished = answers.indexOfFirst { it < 0 }
        val resume = completed && unfinished >= 0
        val history = json.optJSONArray("seenKeys")
        require(history == null || history.length() <= 20_000)
        val seenKeys = if (history == null) emptySet() else List(history.length()) { index ->
            history.getString(index).also { require(it.matches(Regex("[qo]:[0-9a-f]{1,14}"))) }
        }.toSet()
        return QuizSession(
            date, json.getString("sourceVersion"), questions, answers,
            if (resume) unfinished else current, completed && !resume, answerTimes, seenKeys,
        )
    }

    private fun QuizText.json(): JSONObject = JSONObject()
        .put("template", quizTemplates.entries.first { it.value == resource }.key)
        .put("arguments", JSONArray(arguments))

    private fun JSONObject.quizText(): QuizText {
        val arguments = getJSONArray("arguments")
        require(arguments.length() <= 8)
        return QuizText(quizTemplates.getValue(getString("template")),
            List(arguments.length()) { arguments.getString(it).also { text -> require(text.length <= 512) } })
    }
}
