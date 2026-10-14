package cc.samlab.ticket.ui.settings

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.homeColorScheme
import org.json.JSONObject

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ExpressiveSettingsScreen(
    dark: Boolean,
    controller: NativeSettingsController,
    onBack: () -> Unit,
    pureBlack: Boolean = false,
    visible: Boolean = true,
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
    LaunchedEffect(visible) {
        if (visible) {
            scroll.scrollTo(0)
            appBar.heightOffset = appBar.heightOffsetLimit
            appBar.contentOffset = 0f
        }
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val state = controller.state
        val enabled = state.loaded && !controller.busy
        var dialog by remember { mutableStateOf("") }
        Surface(
            Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                SettingsHeader(scrollBehavior, onBack)
                if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth()
                            .testTag("settings-content").verticalScroll(scroll)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        SettingsGroup("个人资料", first = true) {
                            SettingsRow("显示名称", state.username, 0, 2, enabled, { dialog = "name" })
                            SettingsRow("头像", "选择图片，裁切为你的专属头像", 1, 2, enabled,
                                { dialog = "avatar" }) { SettingsAvatar(state.avatar, state.username) }
                        }
                        SettingsGroup("出行提醒") {
                            SettingsRow(
                                "到站检测",
                                if (state.arrivalActive) "已开启 · 到站时提醒你"
                                else if (state.arrivalEnabled) "等待定位或权限恢复" else "未开启",
                                0, 1, enabled, { dialog = "arrival" },
                            ) {
                                SettingsChevron()
                                VerticalDivider(Modifier.height(40.dp))
                                SettingsSwitch(
                                    state.arrivalEnabled, enabled && state.arrivalSupported, "开启到站检测",
                                ) {
                                    controller.request("arrival", JSONObject().put("enabled", it))
                                }
                            }
                        }
                        SettingsGroup("外观") {
                            SettingsRow("切换主题", settingsThemeModes.firstOrNull { it.first == state.themeMode }?.second
                                ?: "跟随系统", 0, 2, enabled, { dialog = "theme" })
                            SettingsRow("纯黑背景", "在暗色模式下使用纯黑背景", 1, 2, enabled,
                                { controller.changeTheme(pureBlack = !state.pureBlack) }) {
                                SettingsSwitch(state.pureBlack, enabled, "纯黑背景开关") {
                                    controller.changeTheme(pureBlack = it)
                                }
                            }
                        }
                        SettingsGroup("数据与备份") {
                            SettingsRow("导出 Excel", "将车票整理成电子表格", 0, 3, enabled,
                                { controller.request("exportExcel") })
                            SettingsRow("导出备份", "保存 JSON 备份，便于恢复和迁移", 1, 3, enabled,
                                { controller.request("exportBackup") })
                            SettingsRow("导入备份", "从 JSON 恢复车票，相同记录自动略过", 2, 3, enabled,
                                controller::chooseBackup)
                        }
                    }
                }
            }
        }
        SettingsDialogs(dialog, { dialog = "" }, controller)
    }
}

@Composable
private fun SettingsAvatar(data: String, name: String) {
    val bitmap = remember(data) {
        runCatching {
            val bytes = Base64.decode(data.substringAfter(','), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        if (bitmap != null) Image(bitmap, "当前头像", Modifier.clip(CircleShape))
        else Box(contentAlignment = Alignment.Center) {
            Text(name.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
