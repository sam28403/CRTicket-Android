package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.window.AdaptivePanes

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ExpressiveSettingsScreen(
    dark: Boolean,
    controller: NativeSettingsController,
    onBack: () -> Unit,
    pureBlack: Boolean = false,
    visible: Boolean = true,
    embedded: Boolean = false,
    page: SettingsPage = controller.ui.page,
    onOpenSaleReminders: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    val collapseRange = with(LocalDensity.current) {
        (SettingsExpandedHeight - SettingsCollapsedHeight).toPx()
    }
    val appBar = rememberTopAppBarState(
        initialHeightOffsetLimit = -collapseRange,
        initialHeightOffset = -collapseRange,
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = appBar,
        canScroll = { visible },
    )
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        Surface(
            Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                SettingsHeader(
                    scrollBehavior,
                    onBack,
                    embedded && page == SettingsPage.MAIN,
                    title = stringResource(page.title),
                )
                if (controller.busy && page != SettingsPage.LANGUAGE) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Box(Modifier.weight(1f)) {
                    when (page) {
                        SettingsPage.PERMISSIONS -> PermissionSettingsContent(controller.permissions, embedded)
                        SettingsPage.SALE_REMINDERS -> SaleReminderSettingsContent(visible, onOpenSaleReminders)
                        SettingsPage.ARRIVAL -> ArrivalSettingsContent(controller, visible)
                        SettingsPage.LANGUAGE -> LanguageSettingsContent()
                        SettingsPage.MAP -> MapSettingsContent(dark, visible)
                        SettingsPage.MAIN -> AdaptivePanes(
                            modifier = Modifier.fillMaxSize(),
                            single = {
                                SettingsPanel(scroll, "settings-content", embedded) {
                                    SettingsProfile(controller, controller.ui::open)
                                    SettingsPreferences(controller, controller.ui::open)
                                }
                            },
                            first = {
                                SettingsPanel(scroll, "settings-content", embedded) {
                                    SettingsProfile(controller, controller.ui::open)
                                }
                            },
                            second = {
                                SettingsPanel(rememberScrollState(), "settings-secondary", embedded) {
                                    SettingsPreferences(controller, controller.ui::open, first = true)
                                }
                            },
                        )
                    }
                }
            }
        }
        if (visible && page == SettingsPage.MAIN) {
            SettingsDialogs(controller.ui.dialog, controller.ui::dismiss, controller)
        }
        if (visible) BackupPermissionDialog(controller)
    }
}

@Composable
internal fun SettingsPanel(
    scroll: androidx.compose.foundation.ScrollState,
    tag: String,
    fullWidth: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            (if (fullWidth) Modifier else Modifier.widthIn(max = 640.dp)).fillMaxWidth()
                .testTag(tag).verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) { content() }
    }
}
