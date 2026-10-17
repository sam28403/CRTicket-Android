<template>
  <div class="user-page" data-pull-refresh-disabled>
    <header v-if="!nativeStatistics" class="operations-toolbar">
      <button class="operations-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20 12H4m7-7-7 7 7 7" />
        </svg>
      </button>
      <h1>运转数据</h1>
    </header>
    <main class="user-main" v-loading="statisticsLoading || mapLoading">
      <div v-if="hasTicketData" class="operations-grid">
        <section class="chart-section station-section" aria-labelledby="station-title">
          <h2 id="station-title">最常到达站点统计</h2>
          <div ref="statisticsChartRef" class="chart-container" role="img" aria-label="站点出发与到达次数柱状图" />
        </section>
        <section class="chart-section" aria-labelledby="city-title">
          <h2 id="city-title">最常访问城市统计</h2>
          <div ref="cityChartRef" class="city-chart" role="img" aria-label="城市访问次数饼图" />
          <ul class="city-legend" aria-label="城市访问次数">
            <li v-for="city in cityData" :key="city.name">
              <span class="legend-dot" :style="{ background: city.color }" aria-hidden="true" />
              <span class="legend-name">{{ city.name }}</span>
              <span class="legend-value">{{ city.value }} 次</span>
            </li>
          </ul>
        </section>
        <section class="chart-section" aria-labelledby="calendar-title">
          <div class="calendar-header">
            <h2 id="calendar-title">月度运转日历</h2>
            <OperationMonthSelector v-model="selectedMonth" />
          </div>
          <div ref="calendarChartRef" class="calendar-container" role="img" aria-label="月度运转日历热力图" />
          <ul v-if="monthlyEntries.length" class="calendar-records" aria-label="每日运转次数">
            <li v-for="entry in monthlyEntries" :key="entry.date">
              <span>{{ entry.label }}</span>
              <span class="calendar-count">{{ entry.count }}次</span>
            </li>
          </ul>
          <p v-else-if="!monthlyLoading" class="calendar-empty">本月暂无运转记录</p>
        </section>
        <section class="chart-section map-section" aria-labelledby="map-title">
          <h2 id="map-title">历史运转轨迹</h2>
          <div ref="mapChartRef" class="map-container" aria-label="历史运转轨迹地图" />
          <el-empty v-if="!mapLoading && ticketHistory.length === 0" description="暂无轨迹数据" />
        </section>
      </div>
      <el-empty v-else-if="!statisticsLoading && !mapLoading && !nativeEmptyState" description="暂无运转数据" />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";
import { onMounted, onBeforeUnmount, ref, computed, watch } from "vue";
import api from "@/api.js";

import { parseStations } from "@/utils/stations.js";
import { useUserStore } from "@/stores/user.js";

import OperationMonthSelector from '@/components/statistics/OperationMonthSelector.vue';
import { useOperationCharts } from '@/components/statistics/useOperationCharts.js';
import { native } from '@/android/platform.js';

const nativeEmptyState = typeof native?.statisticsState === 'function';
const nativeStatistics = typeof native?.supportsStatisticsPage === 'function';
const router = useRouter()
const userStore = useUserStore()
userStore.init()

const goBack = () => router.push('/home');

const getCurrentUser = () => {
  if (!userStore.isLogin || localStorage.getItem("login") !== "true") return null

  const userRaw = localStorage.getItem("user");
  if (!userRaw) return null;

  try {
    return JSON.parse(userRaw);
  } catch {
    return null;
  }
}

// 解析站点数据
const stations = parseStations()

// 构建站点名称到城市名的映射
const stationNameToCityMap = new Map();
stations.forEach(s => {
  if (s.name && s.city) {
    stationNameToCityMap.set(s.name, s.city);
  }
});

// 统计图表相关
const statisticsChartRef = ref(null)
const cityChartRef = ref(null)
const calendarChartRef = ref(null)
const mapChartRef = ref(null)
const statisticsLoading = ref(true)
const mapLoading = ref(true)
const statisticsData = ref({ departures: [], arrivals: [] })
const ticketHistory = ref([])
const selectedMonth = ref('')
const monthlyData = ref([])
const monthlyLoading = ref(false)
const monthlyEntries = computed(() => monthlyData.value
  .filter(item => item.count > 0 && item.travel_date.startsWith(selectedMonth.value + '-'))
  .sort((a, b) => a.travel_date.localeCompare(b.travel_date))
  .map(item => ({
    date: item.travel_date,
    label: item.travel_date.slice(5, 7) + '月' + item.travel_date.slice(8, 10) + '日',
    count: item.count,
  })));
let monthlyRequest = 0;

const hasTicketData = computed(() => {
  return (
    statisticsData.value.departures.length > 0 ||
    statisticsData.value.arrivals.length > 0 ||
    ticketHistory.value.length > 0
  );
})

const getCityName = (stationName) => {
  return stationNameToCityMap.get(stationName) || ''
}

const cityColors = ['#5470c6', '#32866e', '#a663a1', '#b77821', '#518ab5',
  '#a3525e', '#6f7e39', '#8464bf', '#347e8a', '#b26435'];
const cityData = computed(() => {
  const counts = new Map();
  for (const item of [...statisticsData.value.departures, ...statisticsData.value.arrivals]) {
    const city = getCityName(item.station);
    if (city) counts.set(city, (counts.get(city) || 0) + item.count);
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([name, value], index) => ({ name, value, color: cityColors[index] }));
});

useOperationCharts({
  statisticsChartRef, cityChartRef, calendarChartRef, mapChartRef,
  statisticsData, ticketHistory, selectedMonth, monthlyData, cityData,
});

async function loadStatistics() {
  const user = getCurrentUser();
  if (!user) { statisticsLoading.value = false; return; }

  try {
    statisticsLoading.value = true;
    const response = await api.get(`/user/statistics?userId=${user.id}`);
    if (mounted && response.data.success) {
      statisticsData.value = response.data.data;
    }
  } catch (err) {
    console.error(err);
  } finally {
    statisticsLoading.value = false;
  }
}

async function loadMonthlyData() {
  if (!selectedMonth.value) return;

  const user = getCurrentUser();
  if (!user) return;

  const request = ++monthlyRequest;
  const [year, month] = selectedMonth.value.split('-');
  monthlyLoading.value = true;
  monthlyData.value = [];

  try {
    const response = await api.get(`/user/monthly-tickets?userId=${user.id}&year=${year}&month=${month}`);
    if (mounted && request === monthlyRequest && response.data.success) {
      monthlyData.value = response.data.data.tickets.map(t => ({
        ...t,
        travel_date: t.travel_date.replace(/(\d{4})年(\d{2})月(\d{2})日/, '$1-$2-$3')
      }));
    }
  } catch (err) {
    console.error(err);
  } finally {
    if (request === monthlyRequest) monthlyLoading.value = false;
  }
}

async function loadTicketHistory() {
  const user = getCurrentUser();
  if (!user) { mapLoading.value = false; return; }

  try {
    mapLoading.value = true;
    const response = await api.get(`/user/ticket-history?userId=${user.id}`);
    if (mounted && response.data.success) {
      ticketHistory.value = response.data.data;
    }
  } catch (err) {
    console.error(err);
  } finally {
    mapLoading.value = false;
  }
}

watch([hasTicketData, statisticsLoading, mapLoading], ([hasData, statisticsBusy, mapBusy]) => {
  native?.statisticsState?.(!hasData, statisticsBusy || mapBusy);
}, { immediate: true, flush: 'post' });

watch(selectedMonth, loadMonthlyData);

let mounted = true;
onBeforeUnmount(() => { mounted = false; });
onMounted(() => {
  const now = new Date()
  selectedMonth.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  loadStatistics()
  loadTicketHistory()
})
</script>


<style scoped src="@/components/statistics/operations.css"></style>
