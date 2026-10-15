package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun ArrivalSettingsContent(controller: NativeSettingsController, visible: Boolean) {
    val state = controller.state
    val enabled = state.loaded && !controller.busy && state.arrivalSupported
    val onChange: (Boolean) -> Unit = {
        controller.request("arrival", JSONObject().put("enabled", it))
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val illustrationWidth = ((maxHeight - 160.dp).coerceIn(64.dp, 280.dp) * 1.38f)
        Column(
            modifier = Modifier.fillMaxWidth()
                .testTag("arrival-settings-content")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = illustrationWidth).fillMaxWidth()) {
                    ArrivalIllustration(visible)
                }
            }
            Surface(
                onClick = { onChange(!state.arrivalEnabled) },
                enabled = enabled,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 88.dp)
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "启用到站检测",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    SettingsSwitch(state.arrivalEnabled, enabled, "开启到站检测", onChange)
                }
            }
            ArrivalInformation(controller)
        }
    }
}

@Composable
private fun ArrivalInformation(controller: NativeSettingsController) {
    val state = controller.state
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        ArrivalInformationText("进入车站约 300 米范围时发送到访通知，点按通知可查看当前车站大屏。")
        ArrivalInformationText("位置信息只用于本机匹配，不上传到应用服务器，也不保存出行轨迹。")
        ArrivalInformationText(
            "需要精确定位、始终允许定位、通知权限，以及可用的 Google Play 服务。" +
                "使用低功耗地理围栏，提醒可能受系统省电或定位信号影响而延迟。",
        )
        if (state.loaded && (!state.arrivalSupported ||
                (state.arrivalEnabled && !state.arrivalActive))) {
            ArrivalInformationText(state.arrivalMessage)
            if (state.arrivalEnabled && state.arrivalSupported) {
                TextButton(
                    enabled = !controller.busy,
                    onClick = { controller.request("arrival", JSONObject().put("enabled", true)) },
                ) { Text("重新开启") }
            }
        }
        TextButton(onClick = {
            controller.permissions.refresh()
            controller.ui.openPermissions()
        }) { Text("管理相关权限") }
    }
}

@Composable
private fun ArrivalInformationText(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
}
