package cc.samlab.ticket.ui.quiz

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import cc.samlab.ticket.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal const val QUIZ_POINTS_PER_ANSWER = 5
internal const val QUIZ_DOWNLOAD_URL = "https://github.com/sam28403/CRTicket-Android/releases"

internal data class QuizShareProfile(val name: String, val avatar: String)

internal fun quizShareText(context: Context, session: QuizSession): String {
    val correct = session.questions.indices.count { session.answers[it] == session.questions[it].correctIndex }
    return context.getString(R.string.quiz_share_text, correct * QUIZ_POINTS_PER_ANSWER, QUIZ_DOWNLOAD_URL)
}

internal suspend fun quizShareIntent(context: Context, session: QuizSession, bitmap: Bitmap): Intent {
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "quiz-share").apply { check(mkdirs() || isDirectory) }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }
            ?.forEach { it.delete() }
        File(directory, "quiz-${session.date}-${UUID.randomUUID()}.png").also { target ->
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.quiz.share", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, quizShareText(context, session))
        clipData = ClipData.newUri(context.contentResolver, context.getString(R.string.quiz_share), uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
