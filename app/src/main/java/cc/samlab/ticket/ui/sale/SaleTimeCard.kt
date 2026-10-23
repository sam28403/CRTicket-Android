package cc.samlab.ticket.ui.sale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.projectTitleFont
import java.time.Instant
import java.time.LocalDate

@Composable
internal fun SaleTimeCard(
    row: SaleTime,
    bureau: String,
    now: Instant,
    date: LocalDate = now.atZone(saleZone).toLocalDate(),
    subscribed: Boolean = false,
    subscriptionEnabled: Boolean = true,
    onSubscribe: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val text = rememberAppText()
    val colors = MaterialTheme.colorScheme
    val remaining = saleRemaining(row.time, now, date)
    Surface(
        shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().testTag("sale-card:${row.name}"),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, Modifier.weight(1f), color = colors.primary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = projectTitleFont, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(12.dp), color = colors.secondaryContainer) {
                    Text(row.time.toString(), Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = projectTitleFont,
                        maxLines = 1,
                        color = colors.onSecondaryContainer)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (remaining > 0) Text(text(R.string.sale_remaining),
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(if (remaining > 0) saleCountdown(remaining) else text(R.string.sale_opened),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onSubscribe != null) {
                        FilledTonalIconButton(
                            onClick = onSubscribe,
                            enabled = subscriptionEnabled,
                            modifier = Modifier.size(40.dp).testTag("sale-subscribe:${row.name}"),
                        ) {
                            Icon(
                                if (subscribed) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                                text(if (subscribed) R.string.sale_reminder_cancel else R.string.sale_reminder_subscribe),
                                tint = when {
                                    !subscriptionEnabled -> colors.onSurface.copy(alpha = 0.38f)
                                    subscribed -> colors.primary
                                    else -> colors.onSurfaceVariant
                                },
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(bureau.ifBlank { text(R.string.sale_bureau_unknown) },
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
