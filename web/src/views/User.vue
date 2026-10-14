<template>
  <div class="user-page">
    <el-container class="user-layout">
      <el-header class="top-header">
        <AppBrand />
      </el-header>
      <el-container>
        <el-aside width="220px" class="user-sidebar">
          <el-space direction="vertical" fill style="width: 100%">
            <el-button size="large" @click="goHome()">车票生成</el-button>
            <el-button size="large" @click="goHistory()">车票历史</el-button>
            <el-button size="large" type="primary">运转数据</el-button>
          </el-space>
        </el-aside>
        <el-main class="user-main">
          <section class="user-title-wrap">
            <div class="title-content">
              <div>
                <h1>运转数据</h1>
                <p>查看站点统计、月度运转日历与历史运转轨迹。</p>
              </div>
            </div>
          </section>

          <el-card class="statistics-card" shadow="never">
            <template #header>
              <div class="statistics-card-header">
                <h3>个人运转数据</h3>
              </div>
            </template>
            <template v-if="hasTicketData">
              <el-row :gutter="20" v-loading="statisticsLoading">
                <el-col :span="24">
                  <div ref="statisticsChartRef" class="chart-container"></div>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :xs="24" :sm="24" :md="12">
                  <div ref="cityChartRef" class="chart-container"></div>
                </el-col>
                <el-col :xs="24" :sm="24" :md="12">
                  <div class="calendar-wrapper">
                    <div class="calendar-header">
                      <span>月度运转日历</span>
                      <el-date-picker
                        v-model="selectedMonth"
                        type="month"
                        placeholder="选择月份"
                        format="YYYY-MM"
                        value-format="YYYY-MM"
                        :clearable="false"
                        size="small"
                        @change="loadMonthlyData"
                      />
                    </div>
                    <div ref="calendarChartRef" class="calendar-container"></div>
                  </div>
                </el-col>
              </el-row>

              <!-- 运转轨迹地图 -->
              <div class="map-section">
                <h4>历史运转轨迹</h4>
                <el-row :gutter="20">
                  <el-col :span="24">
                    <div ref="mapChartRef" class="map-container" v-loading="mapLoading"></div>
                  </el-col>
                </el-row>
                <el-empty v-if="!mapLoading && ticketHistory.length === 0" description="暂无轨迹数据"></el-empty>
              </div>
            </template>
            <el-empty v-else-if="!statisticsLoading && !mapLoading" description="暂无运转数据"></el-empty>
          </el-card>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import AppBrand from '@/components/AppBrand.vue'
import { useRouter } from "vue-router";
import { onMounted, ref, nextTick, computed, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as echarts from "echarts";
import { useTheme } from '@/composables/useTheme';
const { isDark, resolvedTheme } = useTheme();
const chartText = () => getComputedStyle(document.documentElement).getPropertyValue('--app-text').trim() || '#17324d';
const chartBackground = () =>
  getComputedStyle(document.documentElement).getPropertyValue('--app-surface').trim() || '#ffffff';
watch(resolvedTheme, () => {
  renderStationChart();
  renderCityChart();
  renderCalendarChart();
  renderMapChart();
}, { flush: 'post' });

import api from "@/api.js";

import { parseStations } from "@/utils/stations.js";
import stationCoordinates, { historicalStationCoordinates, loadChinaMap } from "@/utils/maps/stationCoordinates.js";
import { createRailwayGeo } from '@/utils/maps/railwayMap.js';
import { normalizeStationName } from '@/utils/stationName.js';
import { useUserStore } from "@/stores/user.js";

const router = useRouter()
const userStore = useUserStore()
userStore.init()

const goHome = () => {
  router.push('/')
}
const goHistory = () => {
  router.push('/history')
}

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
const statisticsLoading = ref(false)
const mapLoading = ref(false)
const statisticsData = ref({ departures: [], arrivals: [] })
const ticketHistory = ref([])
const selectedMonth = ref('')
const monthlyData = ref([])

const hasTicketData = computed(() => {
  return (
    statisticsData.value.departures.length > 0 ||
    statisticsData.value.arrivals.length > 0 ||
    ticketHistory.value.length > 0
  );
})

const getStationName = (code) => {
  const station = stations.find(s => s.code === code)
  return station ? station.name : code
}

const getCityName = (stationName) => {
  return stationNameToCityMap.get(stationName) || ''
}

async function loadStatistics() {
  const user = getCurrentUser();
  if (!user) return;

  try {
    statisticsLoading.value = true;
    const response = await api.get(`/user/statistics?userId=${user.id}`);
    if (response.data.success) {
      statisticsData.value = response.data.data;
      await nextTick();
      renderStationChart();
      renderCityChart();
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

  const [year, month] = selectedMonth.value.split('-');

  try {
    const response = await api.get(`/user/monthly-tickets?userId=${user.id}&year=${year}&month=${month}`);
    if (response.data.success) {
      monthlyData.value = response.data.data.tickets.map(t => ({
        ...t,
        travel_date: t.travel_date.replace(/(\d{4})年(\d{2})月(\d{2})日/, '$1-$2-$3')
      }));
      await nextTick();
      renderCalendarChart();
    }
  } catch (err) {
    console.error(err);
  }
}

function toCalendarDateKey(year, month, day) {
  return `${year}年${String(month).padStart(2, '0')}月${String(day).padStart(2, '0')}日`;
}

async function loadTicketHistory() {
  const user = getCurrentUser();
  if (!user) return;

  try {
    mapLoading.value = true;
    const response = await api.get(`/user/ticket-history?userId=${user.id}`);
    if (response.data.success) {
      ticketHistory.value = response.data.data;
      await nextTick();
      renderMapChart();
    }
  } catch (err) {
    console.error(err);
  } finally {
    mapLoading.value = false;
  }
}

// 通过站点全名获取坐标（直接映射）
const getStationCoords = (stationName) => {
  const normalizedName = normalizeStationName(stationName);
  return stationCoordinates[stationName] || stationCoordinates[normalizedName]
    || historicalStationCoordinates[stationName] || historicalStationCoordinates[normalizedName] || null;
};

function renderMapChart() {
  if (!mapChartRef.value || ticketHistory.value.length === 0) return;

  echarts.getInstanceByDom(mapChartRef.value)?.dispose();
  const chart = echarts.init(mapChartRef.value, isDark.value ? 'dark' : undefined);
  const hiddenStationNames = new Set(['南海诸岛']);

  // 收集所有有坐标的车站（用 Map 正确计数）
  const stationMap = new Map(); // key: "经度,纬度", value: { name, value, count }
  const lines = [];

  ticketHistory.value.forEach(ticket => {
    const departureCoords = getStationCoords(ticket.departureStation);
    const arrivalCoords = getStationCoords(ticket.arrivalStation);

    if (departureCoords && !hiddenStationNames.has(ticket.departureStation)) {
      const key = `${departureCoords[0]},${departureCoords[1]}`;
      if (stationMap.has(key)) {
        stationMap.get(key).count += 1;
      } else {
        stationMap.set(key, {
          name: ticket.departureStation,
          value: departureCoords,
          count: 1
        });
      }
    }

    if (arrivalCoords) {
      const key = `${arrivalCoords[0]},${arrivalCoords[1]}`;
      if (stationMap.has(key)) {
        stationMap.get(key).count += 1;
      } else {
        stationMap.set(key, {
          name: ticket.arrivalStation,
          value: arrivalCoords,
          count: 1
        });
      }
    }

    // 如果出发和到达都有坐标，添加轨迹线
    if (
      departureCoords &&
      arrivalCoords &&
      !hiddenStationNames.has(ticket.departureStation) &&
      !hiddenStationNames.has(ticket.arrivalStation)
    ) {
      lines.push({
        coords: [departureCoords, arrivalCoords]
      });
    }
  });

  const uniquePoints = [...stationMap.values()];

  const maxCount = Math.max(...uniquePoints.map(p => p.count), 1);

  // 先加载地图数据
  loadChinaMap(echarts).then(mapLoaded => {
    if (chart.isDisposed()) return;
    if (!mapLoaded) {
      ElMessage.warning('地图数据加载失败，请刷新页面重试');
      return;
    }

    const option = {
      title: {
        text: '全部铁路运转轨迹',
        left: 'center',
        textStyle: {
          fontSize: 24,
          fontWeight: 'bold',
          color: chartText(),
          textBorderColor: isDark.value ? '#000' : '#fff',
          textBorderWidth: 3
        }
      },
      tooltip: {
      renderMode: 'richText',
        trigger: 'item',
        formatter: (params) => {
          if (params.componentType === 'series' && params.seriesType === 'effectScatter') {
            return `${params.data.name}\n运转次数: ${params.data.count} 次`;
          }
          return '';
        }
      },
      geo: createRailwayGeo({
        zoom: 1.2,
        center: [105, 36],
      }),
      series: [
        {
          name: '运转轨迹',
          type: 'lines',
          coordinateSystem: 'geo',
          zlevel: 2,
          symbol: ['none', 'none'],
          effect: {
            show: true,
            period: 4,
            trailLength: 0.4,
            color: '#ff9800',
            symbol: 'circle',
            symbolSize: 4
          },
          lineStyle: {
            color: '#ff9800',
            width: 2,
            opacity: 0.6,
            curveness: 0.2
          },
          data: lines
        },
        {
          name: '站点',
          type: 'effectScatter',
          coordinateSystem: 'geo',
          zlevel: 3,
          rippleEffect: {
            brushType: 'stroke',
            scale: 3
          },
          label: {
            show: true,
            position: 'right',
            formatter: '{b}',
            fontSize: 10,
            color: '#17324d',
            textBorderColor: '#e8f5e9',
            textBorderWidth: 3
          },
          symbolSize: (val, params) => {
            const count = params.data.count;
            return Math.max(8, Math.min(20, 8 + count * 2));
          },
          itemStyle: {
            color: '#e53935'
          },
          data: uniquePoints.map(p => ({
            name: p.name,
            value: p.value,
            count: p.count
          }))
        }
      ]
    };

    option.backgroundColor = chartBackground();
  chart.setOption(option);
  });
}

function renderCalendarChart() {
  if (!calendarChartRef.value || !selectedMonth.value) return;

  const container = calendarChartRef.value;
  echarts.getInstanceByDom(container)?.dispose();
  const chart = echarts.init(container, isDark.value ? 'dark' : undefined);

  const [year, month] = selectedMonth.value.split('-').map(Number);
  const daysInMonth = new Date(year, month, 0).getDate();

  // 构建日期数据 map，方便快速查找
  const dataMap = new Map();
  monthlyData.value.forEach(item => {
    dataMap.set(item.travel_date, item.count);
  });

  // 补充完整一个月的所有日期（无数据的日期 count 为 0，但仍然显示）
  const calendarData = [];
  for (let day = 1; day <= daysInMonth; day++) {
    const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    calendarData.push([dateStr, dataMap.get(dateStr) || 0]);
  }

  const maxCount = monthlyData.value.length > 0
    ? Math.max(...monthlyData.value.map(t => t.count))
    : 3;

  const option = {
    tooltip: {
      renderMode: 'richText',
      formatter: (params) => {
        if (params.data[1] === 0) {
          return `${params.data[0]}\n无运转记录`;
        }
        return `${params.data[0]}\n运转次数: ${params.data[1]} 次`;
      }
    },
    visualMap: {
      min: 0,
      max: maxCount,
      calculable: false,
      show: false,
      inRange: {
        color: ['#ebedee', '#c3d6f2', '#86a8e7', '#5470c6', '#23448e']
      }
    },
    calendar: {
      orient: 'vertical',
      top: 50,
      left: 30,
      right: 20,
      cellSize: [Math.floor((container.offsetWidth - 50) / 7), 50],
      range: selectedMonth.value,
      itemStyle: {
        borderWidth: 0.5,
        borderColor: '#e0e0e0'
      },
      splitLine: {
        show: true,
        lineStyle: {
          color: '#f0f0f0',
          width: 1
        }
      },
      yearLabel: { show: false },
      monthLabel: {
        show: false,
        color: '#333',
        fontSize: 11,
        nameMap: 'ZH'
      },
      dayLabel: {
        firstDay: 1,
        nameMap: ['日', '一', '二', '三', '四', '五', '六'],
        fontSize: 10,
        color: chartText()
      }
    },
    series: [{
      type: 'heatmap',
      coordinateSystem: 'calendar',
      label: {
        show: true,
        formatter: (params) => {
          const count = params.data[1];
          const maxCount = monthlyData.value.length > 0
            ? Math.max(...monthlyData.value.map(t => t.count))
            : 3;
          const ratio = maxCount > 0 ? count / maxCount : 0;
          const style = ratio > 0.5 ? 'light' : 'dark';
          return `{${style}|${params.data[0].split('-')[2]}}`;
        },
        rich: {
          light: {
            fontSize: 14,
            color: '#ffffff'
          },
          dark: {
            fontSize: 14,
            color: '#333333'
          }
        }
      },
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowColor: 'rgba(0, 0, 0, 0.2)'
        }
      },
      data: calendarData
    }]
  };

  option.backgroundColor = chartBackground();
  chart.setOption(option);
}

function renderStationChart() {
  if (!statisticsChartRef.value) return;

  echarts.getInstanceByDom(statisticsChartRef.value)?.dispose();
  const chart = echarts.init(statisticsChartRef.value, isDark.value ? 'dark' : undefined);

  // 合并出发和到达站点统计（数据已经是站点全名）
  const stationCountMap = new Map();

  statisticsData.value.departures.forEach(item => {
    const name = item.station;  // 直接使用站点全名
    stationCountMap.set(name, {
      name,
      departure: item.count,
      arrival: 0
    });
  });

  statisticsData.value.arrivals.forEach(item => {
    const name = item.station;
    if (stationCountMap.has(name)) {
      stationCountMap.get(name).arrival = item.count;
    } else {
      stationCountMap.set(name, {
        name,
        departure: 0,
        arrival: item.count
      });
    }
  });

  const sortedStations = [...stationCountMap.values()]
    .sort((a, b) => (b.departure + b.arrival) - (a.departure + a.arrival))
    .slice(0, 10);

  const option = {
    title: {
      text: '最常到达站点统计',
      left: 'center',
      textStyle: { fontSize: 16, fontWeight: 'normal', color: chartText() }
    },
    tooltip: {
      renderMode: 'richText',
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      data: ['出发次数', '到达次数'],
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: sortedStations.map(s => s.name),
      axisLabel: { rotate: 30, fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      name: '次数'
    },
    series: [
      {
        name: '出发次数',
        type: 'bar',
        data: sortedStations.map(s => s.departure),
        itemStyle: { color: '#5470c6' }
      },
      {
        name: '到达次数',
        type: 'bar',
        data: sortedStations.map(s => s.arrival),
        itemStyle: { color: '#91cc75' }
      }
    ]
  };

  option.backgroundColor = chartBackground();
  chart.setOption(option);
}

function renderCityChart() {
  if (!cityChartRef.value) return;

  echarts.getInstanceByDom(cityChartRef.value)?.dispose();
  const chart = echarts.init(cityChartRef.value, isDark.value ? 'dark' : undefined);

  // 统计城市访问次数（出发+到达），数据已是站点全名
  const cityCountMap = new Map();

  statisticsData.value.departures.forEach(item => {
    const city = getCityName(item.station);  // 通过站点全名获取城市名
    if (city) {
      cityCountMap.set(city, (cityCountMap.get(city) || 0) + item.count);
    }
  });

  statisticsData.value.arrivals.forEach(item => {
    const city = getCityName(item.station);
    if (city) {
      cityCountMap.set(city, (cityCountMap.get(city) || 0) + item.count);
    }
  });

  const sortedCities = [...cityCountMap.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10);

  const option = {
    title: {
      text: '最常访问城市统计',
      left: 'center',
      textStyle: { fontSize: 16, fontWeight: 'normal', color: chartText() }
    },
    tooltip: {
      renderMode: 'richText',
      trigger: 'item',
      formatter: '{b}: {c} 次'
    },
    series: [
      {
        name: '访问次数',
        type: 'pie',
        radius: '55%',
        data: sortedCities.map(([name, value]) => ({ name, value })),
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        },
        label: {
          formatter: '{b}: {c}'
        }
      }
    ]
  };

  option.backgroundColor = chartBackground();
  chart.setOption(option);
}

onMounted(() => {
  const now = new Date()
  selectedMonth.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  loadStatistics()
  loadMonthlyData()
  loadTicketHistory()
})
</script>


<style scoped>
.user-page {
  min-height: 100vh;
  background: var(--app-page, radial-gradient(circle at 8% 10%, rgba(255, 186, 73, 0.22), transparent 40%),
    radial-gradient(circle at 92% 92%, rgba(61, 162, 255, 0.2), transparent 42%),
    linear-gradient(160deg, #f6f8fb 0%, #eef3ff 100%));
}

.user-layout {
  min-height: 100vh;
}

.user-sidebar {
  background: var(--app-surface, rgba(255, 255, 255, 0.65));
  backdrop-filter: blur(8px);
  border-right: 1px solid var(--app-border, rgba(15, 23, 42, 0.08));
  padding: 20px;
}

.user-main {
  padding: 28px;
}

.user-title-wrap {
  margin-bottom: 20px;
}

.title-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.title-content h1 {
  margin: 0 0 8px;
  font-size: 32px;
  color: var(--app-text, #17324d);
}

.title-content p {
  margin: 0;
  color: var(--app-muted, #5f6f81);
}

.user-content-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
}

.avatar-card,
.profile-card,
.coming-soon-card {
  border-radius: 16px;
}

.avatar-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
}

.avatar-file-input {
  display: none;
}

.avatar-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.avatar-crop-wrap label {
  display: grid;
  grid-template-columns: 80px 1fr;
  align-items: center;
  gap: 12px;
}

.avatar-crop-preview {
  width: 260px;
  height: 260px;
  margin: 0 auto 22px;
  overflow: hidden;
  border-radius: 50%;
  background: var(--app-hover, #eef1f6);
  box-shadow: 0 0 0 2px #dcdfe6;
}

.avatar-crop-preview canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.captcha-group {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 20%;
}

.captcha-group img {
  cursor: pointer;
  height: 40px;
  border-radius: 6px;
}

.coming-soon-card,
.statistics-card {
  margin-top: 20px;
  background: var(--app-surface, rgba(255, 255, 255, 0.4));
}

.statistics-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.statistics-card h3 {
  margin: 0;
}

.statistics-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.backup-file-input {
  display: none;
}

.chart-container {
  width: 100%;
  height: 320px;
  margin-bottom: 16px;
}

.calendar-wrapper {
  background: var(--app-surface, #fff);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.calendar-header span {
  font-weight: 500;
  color: var(--app-text, #17324d);
}

.calendar-container {
  width: 100%;
  height: 300px;
}

.map-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px dashed #e0e0e0;
}

.map-section h4 {
  margin: 0 0 16px;
  color: var(--app-text, #17324d);
  font-size: 16px;
}

.map-container {
  width: 100%;
  height: 500px;
  border-radius: 12px;
  background: var(--app-surface, #fff);
}

@media (max-width: 900px) {
  .user-layout :deep(.el-container) {
    flex-direction: column;
  }

  .user-sidebar {
    width: 100% !important;
    border-right: none;
    border-bottom: 1px solid var(--app-border, rgba(15, 23, 42, 0.08));
    padding: 12px;
  }

  .user-sidebar :deep(.el-space) {
    flex-direction: row !important;
    flex-wrap: wrap;
    gap: 10px;
  }

  .user-sidebar :deep(.el-space__item) {
    flex: 1 1 46%;
  }

  .user-sidebar :deep(.el-button) {
    width: 100%;
  }

  .user-main {
    padding: 16px 12px;
  }

  .user-content-grid {
    grid-template-columns: 1fr;
  }

  .statistics-card-header {
    align-items: flex-start;
  }

  .statistics-actions {
    width: 100%;
  }

  .captcha-group {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .user-page :deep(.el-dialog) {
    width: 92vw !important;
    max-width: 520px;
  }
}
</style>
