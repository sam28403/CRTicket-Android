<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { trainQueryToast } from '@/composables/useNativeTrainQuery.js'
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
  nativeOnly: { type: Boolean, default: false },
  result: { type: Object, required: true },
  queryTrain: { type: String, default: '' },
  querySource: { type: String, default: '' },
})

function toast(message) {
  if (!props.nativeOnly || !trainQueryToast(message)) ElMessage.info(message)
}

const supported = supportsTrainTracking()
const busy = ref(false)
const ready = ref(false)
const active = ref(null)
const controller = new AbortController()
let timer
let expiryTimer
const tracking = computed(() => active.value?.active &&
  active.value.train === props.result.train && active.value.date === props.result.date &&
  active.value.queryTrain === (props.queryTrain || props.result.train))

function scheduleExpiryRefresh() {
  clearTimeout(expiryTimer)
  const deadline = active.value?.expiresAt
  if (active.value?.active && Number.isFinite(deadline)) {
    expiryTimer = setTimeout(refresh, Math.min(
      2_147_483_647, Math.max(0, deadline - Date.now()) + 100,
    ))
  }
}

async function refresh() {
  if (!supported || busy.value || (!props.nativeOnly && document.hidden)) return
  try {
    active.value = await getTrainTrackingState(controller.signal)
    scheduleExpiryRefresh()
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
        toast(permission.message || '请开启列车追踪通知权限')
        return
      }
    }
    const result = wasTracking
      ? await stopTrainTracking(controller.signal)
      : await startTrainTracking(payload, controller.signal)
    if (!result.success) throw new Error(result.message || '列车追踪操作失败')
    active.value = await getTrainTrackingState(controller.signal)
    scheduleExpiryRefresh()
    toast(wasTracking
      ? '已停止列车追踪'
      : result.finished ? '已显示列车终到通知' : '已开启列车追踪')
  } catch (error) {
    if (error.code !== 'ERR_CANCELED') toast(error.message || '列车追踪操作失败')
  } finally {
    busy.value = false
    if (trackingPayload(props.result, props.queryTrain, props.querySource) !== payload) synchronizeSchedule()
  }
}

// Refresh an existing journey when predictions arrive or the switch is turned off.
async function synchronizeSchedule() {
  if (!tracking.value || busy.value) return
  const snapshot = trackingPayload(props.result, props.queryTrain, props.querySource)
  busy.value = true
  try {
    await startTrainTracking(snapshot, controller.signal)
    active.value = await getTrainTrackingState(controller.signal)
    scheduleExpiryRefresh()
  } catch {
    // The next successful prediction can retry; the running journey remains available.
  } finally {
    busy.value = false
    if (trackingPayload(props.result, props.queryTrain, props.querySource) !== snapshot) synchronizeSchedule()
  }
}

watch(() => trackingPayload(props.result, props.queryTrain, props.querySource), synchronizeSchedule)

onMounted(() => {
  refresh()
  timer = setInterval(refresh, 60000)
  document.addEventListener('visibilitychange', refresh)
  window.addEventListener('focus', refresh)
})

onBeforeUnmount(() => {
  controller.abort()
  clearInterval(timer)
  clearTimeout(expiryTimer)
  document.removeEventListener('visibilitychange', refresh)
  window.removeEventListener('focus', refresh)
})
defineExpose({ toggle, tracking, busy, ready })
</script>

<template>
  <el-button
    v-if="supported && !nativeOnly"
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
