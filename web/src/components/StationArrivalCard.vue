<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  supportsStationArrival, getStationArrivalState,
  enableStationArrival, disableStationArrival,
} from '@/android/stationArrival.js'

const available = supportsStationArrival()
const state = ref({ enabled: false, active: false, supported: true })
const busy = ref(false)
const message = ref('正在读取到站提醒设置…')
const controller = new AbortController()
let timer

async function refresh() {
  if (!available || busy.value) return
  try {
    state.value = await getStationArrivalState(controller.signal)
    message.value = state.value.message
  } catch (error) {
    if (error.code !== 'ERR_CANCELED') message.value = '无法读取到站提醒设置，请重新打开应用'
  }
}

async function change(enable) {
  if (busy.value) return
  busy.value = true
  try {
    const result = await (enable
      ? enableStationArrival(controller.signal)
      : disableStationArrival(controller.signal))
    state.value = result
    message.value = result.message
    if (!result.success) ElMessage.warning(result.message || '未开启到站提醒')
  } catch (error) {
    if (error.code !== 'ERR_CANCELED') ElMessage.error(error.message || '到站提醒设置失败')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  refresh()
  if (available) {
    timer = setInterval(refresh, 15000)
    window.addEventListener('focus', refresh)
  }
})
onBeforeUnmount(() => {
  clearInterval(timer)
  controller.abort()
  window.removeEventListener('focus', refresh)
})
</script>

<template>
  <el-card v-if="available" class="arrival-card" shadow="never">
    <template #header><h3>到站提醒</h3></template>
    <p>进入坐标表中任一车站 300 米范围，通知“欢迎到访XX站”，点按即可查看当天车站大屏。</p>
    <p class="arrival-detail">
      需要精确定位、始终允许定位及到站通知。使用低功耗地理围栏，离开监控范围后更新附近车站，
      仅在初始化或更新围栏时短暂获取位置，平时被动接收系统已有位置，不定时主动定位。
      系统仍可能记录后台位置访问，省电策略可能让提醒延迟几分钟。
      位置仅用于手机本机匹配，不保存出行轨迹。
    </p>
    <div class="arrival-actions">
      <el-tag :type="state.active ? 'success' : 'info'">
        {{ state.active ? '已开启' : state.enabled ? '等待定位或权限恢复' : '未开启' }}
      </el-tag>
      <el-button
        v-if="!state.enabled"
        type="primary"
        :loading="busy"
        :disabled="!state.supported"
        @click="change(true)"
      >开启到站提醒</el-button>
      <template v-else>
        <el-button v-if="!state.active" :loading="busy" @click="change(true)">重新开启</el-button>
        <el-button :loading="busy" @click="change(false)">关闭到站提醒</el-button>
      </template>
    </div>
    <p class="arrival-status" role="status">{{ message }}</p>
    <el-text v-if="state.stations" type="info" size="small">
      已收录 {{ state.stations.toLocaleString() }} 个站点坐标，同一坐标的不同语言名称合并提醒。
    </el-text>
  </el-card>
</template>

<style scoped>
.arrival-card {
  margin-top: 24px;
}
h3 {
  margin: 0;
}
p {
  line-height: 1.7;
}
.arrival-detail, .arrival-status {
  color: var(--el-text-color-secondary);
}
.arrival-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.arrival-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
