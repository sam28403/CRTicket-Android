package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.AppLanguages
import java.util.Locale

@Composable
internal fun LanguageSettingsContent() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val languages = remember(context) { AppLanguages.supported(context) }
    val selected = AppLanguages.selectedTag()
    val displayLocale = configuration.locales[0]
    LazyColumn(
        modifier = Modifier.fillMaxSize().selectableGroup().testTag("settings-language-list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "system") {
            LanguageChoice(
                AppLanguages.SYSTEM,
                stringResource(R.string.language_system),
                "", selected, 0, 1,
            )
            Spacer(Modifier.height(16.dp))
        }
        itemsIndexed(languages, key = { _, language -> language.tag }) { index, language ->
            val translatedName = Locale.forLanguageTag(language.tag).getDisplayName(displayLocale)
            LanguageChoice(
                language.tag, language.nativeName,
                translatedName.takeUnless { it.equals(language.nativeName, ignoreCase = true) }.orEmpty(),
                selected, index, languages.size,
            )
        }
    }
}

@Composable
private fun LanguageChoice(
    tag: String,
    title: String,
    subtitle: String,
    current: String,
    index: Int,
    count: Int,
) {
    val selected = current == tag
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerLowest,
        shape = RoundedCornerShape(
            topStart = if (index == 0) 28.dp else 4.dp,
            topEnd = if (index == 0) 28.dp else 4.dp,
            bottomStart = if (index == count - 1) 28.dp else 4.dp,
            bottomEnd = if (index == count - 1) 28.dp else 4.dp,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                .testTag("language-option:${tag.ifEmpty { "system" }}")
                .selectable(selected, role = Role.RadioButton) { AppLanguages.select(tag) }
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
internal fun selectedLanguageName(): String {
    val context = LocalContext.current
    // Re-read the framework preference when the system changes this Activity's configuration.
    val configuration = LocalConfiguration.current
    val selected = remember(configuration) { AppLanguages.selectedTag() }
    val languages = remember(context) { AppLanguages.supported(context) }
    return languages.firstOrNull { it.tag == selected }?.nativeName
        ?: stringResource(R.string.language_system)
}
