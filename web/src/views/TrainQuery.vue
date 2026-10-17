<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useNativeTrainQuery, trainQueryToast } from '@/composables/useNativeTrainQuery.js'
import AppBrand from '@/components/AppBrand.vue'
import TrainRouteMap from '@/components/TrainRouteMap.vue'
import StationLink from '@/components/StationLink.vue'
import TrainTrackingButton from '@/components/TrainTrackingButton.vue'
import api, { timetableTimeout } from '@/api.js'
import { isTrainQueryDateInRange, trainQueryRange } from '@/utils/trainQueryDate.js'
import { displayTrainCode, trainInfoForDisplay } from '@/utils/trainDisplay.js'
import { trainQuerySourceLocation } from '@/utils/trainQuerySource.js'

defineOptions({ name: 'TrainQuery' })

const train = ref('')
const date = ref(trainQueryRange().today)
const loading = ref(false)
const switchingSource = ref(false)
const exporting = ref(false)
const error = ref('')
const result = ref(null)
const route = useRoute()
const router = useRouter()
const offlineMode = computed(() => route.query.source === 'llt')
const offlineRecord = ref('')
const resultQueryCode = ref('')
const resultQuerySource = ref('')
const trackingButton = ref(null)
const nativeQuery = useNativeTrainQuery({
  train, date, loading, error, result, offlineMode, trackingButton, exporting,
  query, switchSource, exportData,
})
function toast(message) {
  if (!trainQueryToast(message)) ElMessage.info(message)
}
let controller
let requestId = 0
let previousRouteQuery

async function switchSource() {
  if (switchingSource.value) return
  const location = trainQuerySourceLocation({
    train: train.value,
    date: date.value,
    offline: !offlineMode.value,
    offlineRecord: offlineRecord.value,
  })
  if (location.query.date !== date.value) {
    toast('所选日期超出在线查询范围，已切换到今天')
  }
  switchingSource.value = true
  try {
    await router.replace(location)
  } finally {
    switchingSource.value = false
  }
}

async function exportData() {
  if (!result.value?.stops.length || exporting.value) return
  const snapshot = result.value
  exporting.value = true
  try {
    const { downloadTrainInfoPdf } = await import('@/utils/trainInfoPdf.js')
    await downloadTrainInfoPdf(snapshot)
  } catch (err) {
    toast(err.message || '导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

function disabledDate(value) {
  const localDate = [
    value.getFullYear(),
    String(value.getMonth() + 1).padStart(2, '0'),
    String(value.getDate()).padStart(2, '0'),
  ].join('-')
  return !isTrainQueryDateInRange(localDate)
}

async function query() {
  if (loading.value) return
  const entered = train.value.trim().toUpperCase()
  const code = offlineMode.value && offlineRecord.value && entered === displayTrainCode(offlineRecord.value)
    ? offlineRecord.value
    : entered
  const validCode = offlineMode.value
    ? /^[A-Z]?\d{1,5}[A-Z]?(?:\/[A-Z]?\d{1,5}[A-Z]?)*$/.test(code) && code.length <= 40
    : /^[A-Z]?\d{1,5}$/.test(code)
  if (!validCode || !date.value) {
    toast('请输入完整车次并选择始发日期')
    return
  }
  if (!isTrainQueryDateInRange(date.value)) {
    toast('请选择今天前 2 天至后 14 天内的始发日期')
    return
  }
  if (offlineMode.value) offlineRecord.value = code
  const selectedDate = date.value
  const source = offlineMode.value ? 'llt' : undefined
  train.value = displayTrainCode(code)
  loading.value = true
  error.value = ''
  result.value = null
  controller = new AbortController()
  const id = ++requestId
  try {
    // 同步网址时跳过路由监听的重查，保留当前请求和完整离线记录号。
    previousRouteQuery = [code, selectedDate, source]
    await router.replace({
      path: '/train',
      query: { train: code, date: selectedDate, source },
    })
    if (id !== requestId) return
    const { data } = await api.get(source === 'llt' ? '/llt-trains/detail' : '/train-info', {
      params: { train: code, date: selectedDate },
      timeout: timetableTimeout,
      signal: controller.signal,
    })
    if (!data.success) throw new Error(data.message || '查询失败')
    if (id === requestId) {
      result.value = trainInfoForDisplay(data)
      resultQueryCode.value = code
      resultQuerySource.value = source === 'llt' ? 'llt' : ''
      toast(`数据来源：${source === 'llt' ? '路路通' : '12306'}`)
    }
  } catch (err) {
    if (id === requestId && err.code !== 'ERR_CANCELED') {
      if (source !== 'llt') {
        toast('在线查询不可用，已切换为路路通离线查询')
        loading.value = false
        await switchSource()
        return
      }
      error.value = err.response?.data?.message || (
        err.code === 'ECONNABORTED'
          ? '查询超时，请稍后重试'
          : '查询失败，请检查手机网络后重试'
      )
    }
  } finally {
    if (id === requestId) loading.value = false
  }
}

watch(
  () => [route.path, route.query.train, route.query.date, route.query.source],
  ([path, code, selectedDate, source]) => {
    if (path !== '/train') return
    const routeQuery = [code, selectedDate, source]
    // 缓存页面仍会收到其他路由的变化，返回同一查询时保留表单和结果。
    if (previousRouteQuery && routeQuery.every((value, index) => value === previousRouteQuery[index])) {
      return
    }
    previousRouteQuery = routeQuery
    controller?.abort()
    requestId++
    loading.value = false
    result.value = null
    error.value = ''
    const record = typeof code === 'string' ? code.trim().toUpperCase() : ''
    if (offlineMode.value) offlineRecord.value = record
    train.value = displayTrainCode(record)
    date.value = typeof selectedDate === 'string' ? selectedDate : trainQueryRange().today
    if (train.value && /^\d{4}-\d{2}-\d{2}$/.test(date.value)) query()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  requestId++
  controller?.abort()
})
</script>

<template>
  <div v-if="!nativeQuery" class="train-query-page">
    <el-header class="top-header">
      <AppBrand />
    </el-header>
    <main class="query-content">
      <div class="page-heading">
        <h1>车次查询</h1>
        <div class="page-header-actions">
          <el-button @click="$router.push('/station')">车站大屏</el-button>
          <el-button type="info" @click="$router.push('/list')">
            车次列表
          </el-button>
        </div>
      </div>
      <el-card shadow="never">
        <el-form class="query-form" label-position="top" @submit.prevent="query">
          <el-form-item label="车次">
            <el-input
              v-model="train"
              :disabled="loading"
              :placeholder="offlineMode ? '输入离线库中的完整车次' : '例如 G1、D3068、1461'"
              clearable
              :maxlength="offlineMode ? 40 : 6"
            />
          </el-form-item>
          <el-form-item label="始发日期">
            <el-date-picker
              v-model="date"
              :disabled="loading"
              type="date"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledDate"
              :editable="false"
              :clearable="false"
            />
          </el-form-item>
          <el-form-item class="query-action">
            <div class="query-buttons">
              <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
              <el-button native-type="button" :disabled="switchingSource" @click="switchSource">
                {{ offlineMode ? '切换为在线查询' : '切换为路路通离线' }}
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="query-message" />
      <el-card v-loading="loading" shadow="never" class="results" element-loading-text="正在查询车次…">
        <el-alert
          v-for="warning in result?.warnings || []"
          :key="warning"
          :title="warning"
          type="info"
          :closable="false"
          show-icon
          class="query-message"
        />
        <template v-if="result?.stops.length">
          <div class="result-heading">
            <div class="result-title-row">
              <strong>
                {{ result.train }}　<StationLink :name="result.from" /> → <StationLink :name="result.to" />
              </strong>
              <TrainTrackingButton
                ref="trackingButton"
                :key="`${resultQuerySource}-${resultQueryCode}-${result.date}`"
                :result="result"
                :query-train="resultQueryCode"
                :query-source="resultQuerySource"
              />
            </div>
          </div>
          <el-table class="desktop-stops" :data="result.stops" stripe border style="width: 100%">
            <el-table-column prop="no" label="站序" width="70" align="center" />
            <el-table-column prop="train" label="车次" min-width="100" />
            <el-table-column prop="station" label="站名" min-width="120">
              <template #default="{ row }"><StationLink :name="row.station" /></template>
            </el-table-column>
            <el-table-column label="到达时间" min-width="150">
              <template #default="{ row, $index }">
                {{ $index === 0 ? '始发站' : row.arrival || '—' }}
                <el-tag
                  v-if="$index > 0 && row.arrival && row.arrivalDay > 0"
                  size="small"
                  effect="plain"
                  class="arrival-day"
                >
                  +{{ row.arrivalDay }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="出发时间" min-width="100">
              <template #default="{ row, $index }">
                {{ $index === result.stops.length - 1 ? '终到站' : row.departure || '—' }}
              </template>
            </el-table-column>
            <el-table-column label="停留时长" min-width="100">
              <template #default="{ row }">{{ row.stay == null ? '—' : `${row.stay} 分钟` }}</template>
            </el-table-column>
            <el-table-column label="里程（km）" min-width="110">
              <template #default="{ row }">{{ row.mileage ?? '暂无' }}</template>
            </el-table-column>
          </el-table>
          <ol class="mobile-stops" aria-label="列车经停信息">
            <li v-for="(stop, index) in result.stops" :key="`${stop.no}-${index}`" class="mobile-stop">
              <div class="stop-heading">
                <strong>
                  <span class="stop-number">{{ stop.no }}</span>
                  <StationLink :name="stop.station" />
                </strong>
                <el-text size="small">{{ stop.train }}</el-text>
              </div>
              <dl class="stop-details">
                <div>
                  <dt>到达时间</dt>
                  <dd>
                    {{ index === 0 ? '始发站' : stop.arrival || '—' }}
                    <el-tag
                      v-if="index > 0 && stop.arrival && stop.arrivalDay > 0"
                      size="small"
                      effect="plain"
                      class="arrival-day"
                    >
                      +{{ stop.arrivalDay }}
                    </el-tag>
                  </dd>
                </div>
                <div><dt>出发时间</dt><dd>{{ index === result.stops.length - 1 ? '终到站' : stop.departure || '—' }}</dd></div>
                <div><dt>停留时长</dt><dd>{{ stop.stay == null ? '—' : `${stop.stay} 分钟` }}</dd></div>
                <div><dt>里程</dt><dd>{{ stop.mileage == null ? '暂无' : `${stop.mileage} km` }}</dd></div>
              </dl>
            </li>
          </ol>
          <div class="export-actions">
            <el-button type="primary" :loading="exporting" :disabled="loading" @click="exportData">导出数据</el-button>
          </div>
        </template>
        <el-empty
          v-else
          :description="loading
            ? '正在获取时刻表'
            : error
              ? '查询未完成，请重试'
              : result
                ? '该日期未查询到此车次'
                : '输入车次和始发日期查询时刻表'"
        />
      </el-card>
      <TrainRouteMap v-if="result?.stops.length" :train="result.train" :stops="result.stops" />
    </main>
  </div>
  <TrainTrackingButton
    v-else-if="result?.stops.length"
    ref="trackingButton"
    :key="`${resultQuerySource}-${resultQueryCode}-${result.date}`"
    :result="result"
    :query-train="resultQueryCode"
    :query-source="resultQuerySource"
    native-only
  />
</template>

<style scoped>
.train-query-page {
  min-height: 100vh;
  color: var(--el-text-color-primary);
}
.top-header {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 50px;
  padding: 5px 15px;
  background: var(--app-surface, rgb(10 10 0 / 0.1));
}
.top-header h2 {
  margin: 0;
  font-family: 'Roboto', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 20px;
}
.query-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.page-heading, .result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
h1 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  font-size: 22px;
  font-weight: 500;
}
.query-buttons {
  display: flex;
  gap: 12px;
}
.query-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}
.query-form {
  display: flex;
  align-items: flex-end;
  gap: 20px;
  flex-wrap: wrap;
}
.result-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.result-title-row strong {
  min-width: 0;
  overflow-wrap: anywhere;
}
.result-title-row :deep(.el-button) {
  flex-shrink: 0;
}
.tracking-hint {
  width: 100%;
}
.query-form .el-form-item {
  width: 240px;
  margin-bottom: 16px;
}
.query-form .query-action {
  width: auto;
}
.query-form :deep(.el-date-editor) {
  width: 100%;
}
.results {
  margin-top: 20px;
  min-height: 280px;
}
.query-message {
  margin: 16px 0;
}
.arrival-day {
  margin-left: 6px;
}
.export-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
.mobile-stops {
  display: none;
}
@media (max-width: 800px) {
  .desktop-stops {
    display: none;
  }
  .mobile-stops {
    display: block;
    padding: 0;
    margin: 0;
    list-style: none;
  }
  .mobile-stop {
    padding: 16px 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .mobile-stop:first-child {
    padding-top: 0;
  }
  .stop-heading {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
  }
  .stop-heading strong {
    min-width: 0;
    overflow-wrap: anywhere;
  }
  .stop-heading > :last-child {
    flex-shrink: 0;
  }
  .stop-number {
    color: var(--el-text-color-secondary);
    font-weight: normal;
    margin-right: 10px;
  }
  .stop-details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 16px;
    margin: 12px 0 0;
  }
  .stop-details dt {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    margin-bottom: 4px;
  }
  .stop-details dd {
    margin: 0;
    overflow-wrap: anywhere;
  }
  .result-heading {
    overflow-wrap: anywhere;
  }
}
@media (max-width: 600px) {
  .top-header h2 {
    font-size: 15px;
  }
  .query-content {
    padding: 20px 12px;
  }
  .query-form {
    gap: 0;
  }
  .query-form .el-form-item {
    width: 100%;
  }
  .query-buttons {
    width: 100%;
  }
  .query-buttons :deep(.el-button) {
    flex: 1;
  }
  .results :deep(.el-card__body) {
    padding: 16px;
  }
  .export-actions .el-button {
    width: 100%;
  }
}
</style>
