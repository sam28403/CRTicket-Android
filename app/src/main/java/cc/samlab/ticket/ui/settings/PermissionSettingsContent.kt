package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun PermissionSettingsContent(controller: PermissionSettingsController, embedded: Boolean) {
    val state = controller.state
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            (if (embedded) Modifier else Modifier.widthIn(max = 640.dp)).fillMaxWidth()
                .testTag("permissions-content").verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            SettingsGroup("通知", first = true) {
                val count = if (state.promotedNotifications != null) 2 else 1
                SettingsRow("通知权限", if (state.notifications) "已允许 · 行程、余票及到站提醒"
                    else "未允许 · 点击前往系统设置", 0, count, true, controller::openNotifications)
                state.promotedNotifications?.let { allowed ->
                    SettingsRow("实时动态", if (allowed) "已允许 · 在系统中突出显示行程进度"
                        else "未允许 · 点击前往系统设置", 1, count, true, controller::openPromotedNotifications)
                }
            }
            SettingsGroup("定位") {
                val location = when {
                    state.fineLocation -> "已允许精确位置"
                    state.coarseLocation -> "仅允许大概位置"
                    else -> "未允许"
                }
                SettingsRow("位置信息", "$location · 用于识别附近车站", 0, 2, true,
                    controller::openLocationPermissions)
                SettingsRow("后台定位", if (state.backgroundLocation) "已允许 · 离开应用后也可检测到站"
                    else "未允许 · 可在系统的位置信息权限中选择始终允许", 1, 2, true,
                    controller::openLocationPermissions)
            }
            SettingsGroup("后台运行") {
                SettingsRow("电池优化", if (state.unrestrictedBattery) "已忽略电池优化"
                    else "正在优化 · 可能影响持续监控和提醒", 0, 1, true, controller::openBatteryOptimization)
            }
            SettingsGroup("系统设置") {
                SettingsRow("全部应用权限", "在系统应用信息中管理 CRTicket 的权限", 0, 1, true,
                    controller::openAppPermissions)
            }
        }
    }
}
