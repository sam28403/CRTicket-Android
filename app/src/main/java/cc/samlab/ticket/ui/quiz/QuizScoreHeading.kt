package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.settings.SettingsAvatar

@Composable
internal fun QuizScoreHeading(perfect: Boolean, profile: QuizShareProfile?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().testTag("quiz-score-heading"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile != null) SettingsAvatar(profile.avatar, profile.name)
        else Surface(shape = CircleShape, color = colors.primary) {
            Icon(
                if (perfect) Icons.Outlined.EmojiEvents else Icons.Outlined.DoneAll,
                null, Modifier.padding(12.dp).size(24.dp), tint = colors.onPrimary,
            )
        }
        Text(
            profile?.name ?: stringResource(
                if (perfect) R.string.quiz_summary_perfect else R.string.quiz_summary_heading,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
