<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getTrainTrackingState,
  requestTrainTrackingPermission,
  startTrainTracking,
  stopTrainTracking,
  supportsTrainTracking,
  trackingPayload,
} from '@/android/trainTracking.js'

const props = defineProps({
  result: { type: Object, required: true },
  queryTrain: { type: String, default: '' },
  querySource: { type: String, default: '' },
})

const supported = supportsTrainTracking()
const busy = ref(false)
const ready = ref(false)
const active = ref(null)
const controller = new AbortController()
let timer
const tracking = computed(() => active.value?.active &&
  active.value.train === props.result.train && active.value.date === props.result.date &&
  active.value.queryTrain === (props.queryTrain || props.result.train))

async function refresh() {
  if (!supported || busy.value || document.hidden) return
  try {
    active.value = await getTrainTrackingState(controller.signal)
    ready.value = true
  } catch (error) {
    if (error.code !== 'ERR_CANCELED') ready.value = false
  }
}

async function toggle() {
  if (busy.value || !ready.value) return
  const wasTracking = tracking.value
  // Keep the selected schedule stable if the user navigates during a permission prompt.
  const payload = trackingPayload(props.result, props.queryTrain, props.querySource)
  busy.value = true
  try {
    if (!wasTracking) {
      const permission = await requestTrainTrackingPermission(controller.signal)
      if (!permission.granted) {
        ElMessage.warning(permission.message || '请开启列车追踪通知权限')
        return
      }
    }
    const result = wasTracking
      ? await stopTrainTracking(controller.signal)
      : await startTrainTracking(payload, controller.signal)
    if (!result.success) throw new Error(result.message || '列车追踪操作失败')
    active.value = await getTrainTrackingState(controller.signal)
    ElMessage.success(wasTracking
      ? '已停止列车追踪'
      : result.finished ? '已显示列车终到通知' : '已开启列车追踪')
  } catch (error) {
    if (error.code !== 'ERR_CANCELED') ElMessage.error(error.message || '列车追踪操作失败')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, 60000)
  document.addEventListener('visibilitychange', refresh)
  window.addEventListener('focus', refresh)
})

onBeforeUnmount(() => {
  controller.abort()
  clearInterval(timer)
  document.removeEventListener('visibilitychange', refresh)
  window.removeEventListener('focus', refresh)
})
</script>

<template>
  <el-button
    v-if="supported"
    type="primary"
    size="small"
    :loading="busy"
    :disabled="!ready"
    :aria-label="`${tracking ? '停止追踪' : '追踪'} ${result.train}`"
    @click="toggle"
  >
    {{ tracking ? '停止追踪' : '追踪' }}
  </el-button>
</template>
