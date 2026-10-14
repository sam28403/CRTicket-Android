<script setup>
import ThemeSelect from '@/components/ThemeSelect.vue'
import AppBrand from '@/components/AppBrand.vue'
import { Refresh } from '@element-plus/icons-vue'
import LeftTicketCard from '@/components/LeftTicketCard.vue'
import { queryStationSearch } from '@/composables/useTicketShared.js'
import { useTicketMonitor } from '@/composables/useTicketMonitor.js'

const {
  from, to, date, interval, monitoring, loading, rows, queriedAt, error,
  nextAt, events, columns, trainFilters, seatFilters, timeRange,
  onlyAvailable, highSpeedOnly, ordinaryOnly, selectedDepartureStations,
  selectedArrivalStations, departureStations, arrivalStations, filtered,
  ticketCount, nativeMonitoring, restoring, query, stop, disabledDate,
  swapStations, selectTrainType,
} = useTicketMonitor()

const timeMarks = { 0: '0点', 6: '6点', 12: '12点', 18: '18点', 24: '24点' }
const formatHour = value => `${String(value).padStart(2, '0')}:00`
const time = value => value
  ? new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
  : '—'
</script>

<template>
  <div class="monitor-page">
    <el-header class="top-header">
      <AppBrand />
      <div class="page-header-actions"><ThemeSelect /></div>
      </el-header>

    <main class="monitor-content">
      <header class="page-heading">
        <h1>
          余票查询与监控
          <el-tag type="warning">Beta</el-tag>
        </h1>
        <nav class="monitor-navigation" aria-label="页面导航">
          <RouterLink to="/">生成车票</RouterLink>
          <a href="https://www.12306.cn" target="_blank" rel="noopener noreferrer">
            打开 12306 官网 ↗
          </a>
        </nav>
      </header>

      <el-card shadow="never">
        <el-form label-position="top" @submit.prevent="query()">
          <div class="form-grid">
            <div class="station-route-fields">
              <el-form-item label="出发站">
                <el-autocomplete
                  v-model="from"
                  :disabled="restoring"
                  :fetch-suggestions="queryStationSearch"
                  placeholder="站名 / 拼音 / 首字母"
                  clearable
                />
              </el-form-item>

              <div class="swap-stations">
                <el-button
                  circle
                  title="换向"
                  aria-label="交换出发站和到达站"
                  :disabled="restoring"
                  @click="swapStations"
                >
                  <el-icon><Refresh /></el-icon>
                </el-button>
                <span>换向</span>
              </div>

              <el-form-item label="到达站">
                <el-autocomplete
                  v-model="to"
                  :disabled="restoring"
                  :fetch-suggestions="queryStationSearch"
                  placeholder="站名 / 拼音 / 首字母"
                  clearable
                />
              </el-form-item>
            </div>

            <el-form-item label="出发日期">
              <el-date-picker
                v-model="date"
                type="date"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
                :editable="false"
                :disabled="restoring"
              />
            </el-form-item>

            <el-form-item label="监控间隔（秒）">
              <el-input-number
                v-model="interval"
                :min="10"
                :max="3600"
                :step="10"
                :disabled="monitoring || restoring"
              />
            </el-form-item>
          </div>

          <div class="actions">
            <el-button type="primary" :loading="loading" :disabled="monitoring" @click="query()">
              查询余票
            </el-button>
            <el-button type="success" :disabled="loading || monitoring" @click="query(true)">
              开始监控
            </el-button>
            <el-button :disabled="restoring || (!monitoring && !loading)" @click="stop">停止</el-button>
            <span role="status">
              {{ monitoring ? '监控中' : '未监控' }} · 上次成功：{{ time(queriedAt) }}
              <template v-if="nextAt"> · 下次查询：{{ time(nextAt) }}</template>
            </span>
          </div>
        </el-form>

        <p class="hint">
          {{ nativeMonitoring ? '应用切换到后台或锁屏后继续监控。' : '监控需保持页面打开。' }}
          余票以 12306 实时结果为准。
        </p>
      </el-card>

      <el-alert
        v-if="error"
        :title="error"
        :description="monitoring
          ? '监控会自动重试，上次结果可能过期。'
          : queriedAt ? '上次结果可能过期，请检查网络后重试。' : '请检查查询条件或服务状态后重试。'"
        type="error"
        show-icon
        :closable="false"
        role="alert"
      />

      <section v-if="rows.length" class="station-filters" aria-label="车站筛选">
        <div class="station-filter-row">
          <strong>出发车站：</strong>
          <button
            type="button"
            class="all-stations-button"
            :class="{ active: !selectedDepartureStations.length }"
            @click="selectedDepartureStations = []"
          >
            全部
          </button>
          <el-checkbox-group v-model="selectedDepartureStations">
            <el-checkbox
              v-for="station in departureStations"
              :key="station"
              :value="station"
            >
              {{ station }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div class="station-filter-row">
          <strong>到达车站：</strong>
          <button
            type="button"
            class="all-stations-button"
            :class="{ active: !selectedArrivalStations.length }"
            @click="selectedArrivalStations = []"
          >
            全部
          </button>
          <el-checkbox-group v-model="selectedArrivalStations">
            <el-checkbox
              v-for="station in arrivalStations"
              :key="station"
              :value="station"
            >
              {{ station }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div class="time-filter-row">
          <strong>出发时间：</strong>
          <span class="time-range-value">
            {{ formatHour(timeRange[0]) }}–{{ formatHour(timeRange[1]) }}
          </span>
          <el-slider
            v-model="timeRange"
            range
            show-stops
            :min="0"
            :max="24"
            :step="1"
            :marks="timeMarks"
            :format-tooltip="formatHour"
            aria-label="出发时间范围"
          />
        </div>
      </section>

      <section class="results">
        <div class="result-header">
          <h2>
            查询结果
            <small>{{ filtered.length }} 趟 · 关注席别有票 {{ ticketCount }} 趟</small>
          </h2>
          <div class="filters">
            <el-input-tag
              v-model="trainFilters"
              class="train-filter-input"
              placeholder="输入车次后回车，如 G1"
              clearable
              aria-label="筛选车次"
            />
            <el-select
              v-model="seatFilters"
              multiple
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="全部席别"
              popper-class="monitor-seat-options"
              aria-label="关注席别（可多选，清空关注全部）"
            >
              <el-option
                v-for="[key, label] in columns"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>
            <div class="train-type-switches" aria-label="车种筛选">
              <span>高速动车</span>
              <el-switch
                v-model="highSpeedOnly"
                aria-label="仅显示高速动车"
                @change="selectTrainType('highSpeed', $event)"
              />
              <span>普速列车</span>
              <el-switch
                v-model="ordinaryOnly"
                aria-label="仅显示普速列车"
                @change="selectTrainType('ordinary', $event)"
              />
            </div>
            <el-checkbox v-model="onlyAvailable">仅看有票</el-checkbox>
          </div>
        </div>

        <div v-if="filtered.length" class="ticket-grid" :aria-busy="loading">
          <LeftTicketCard
            v-for="row in filtered"
            :key="row.id"
            :train="row"
            :columns="columns"
          />
        </div>
        <el-empty
          v-else
          :description="loading ? '正在查询…' : !queriedAt ? '请选择车站和日期，开始查询' : rows.length ? '没有符合筛选条件的车次' : '该日期区间暂无车次'"
        />
        <p class="hint">
          <span class="seat-plenty">绿色：大于 20 张</span> ·
          <span class="seat-limited">橙色：1–20 张</span> ·
          <span class="seat-empty">红色：0 张</span>
        </p>
      </section>

      <section class="events">
        <h2>
          有票提醒
          <small v-if="events.length">{{ events.length }} 条</small>
        </h2>
        <p v-if="!events.length" class="hint">暂无提醒</p>
        <ul v-else>
          <li v-for="(event, index) in events" :key="index">
            <time>{{ time(event.at) }}</time>
            {{ event.text }}
          </li>
        </ul>
      </section>
    </main>
  </div>
</template>

<style scoped src="../assets/styles/ticketMonitor.css"></style>

<style>
.monitor-seat-options .el-select-dropdown__item {
  padding-right: 20px;
  padding-left: 20px;
  font-family: 'MonitorFangzheng', serif;
}
</style>
