import * as echarts from 'echarts';
import { operationChart } from './chartInstance.js';
import { ElMessage } from 'element-plus';
import stationCoordinates, { historicalStationCoordinates, loadChinaMap } from '@/utils/maps/stationCoordinates.js';
import { createRailwayGeo } from '@/utils/maps/railwayMap.js';
import { normalizeStationName } from '@/utils/stationName.js';

// 通过站点全名获取坐标（直接映射）
const getStationCoords = (stationName) => {
  const normalizedName = normalizeStationName(stationName);
  return stationCoordinates[stationName] || stationCoordinates[normalizedName]
    || historicalStationCoordinates[stationName] || historicalStationCoordinates[normalizedName] || null;
};

export async function renderMapChart({ mapChartRef, ticketHistory, isDark, chartText, chartBackground }) {
  if (!mapChartRef.value || ticketHistory.value.length === 0) return;

  const chart = operationChart(mapChartRef.value, isDark.value);
  const hiddenStationNames = new Set(['南海诸岛']);

  // 收集所有有坐标的车站（用 Map 正确计数）
  const stationMap = new Map(); // key: "经度,纬度", value: { name, value, count }
  const routes = new Map();

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
      const key = `${departureCoords.join(',')}:${arrivalCoords.join(',')}`;
      routes.set(key, {
        coords: [departureCoords, arrivalCoords]
      });
    }
  });

  const uniquePoints = [...stationMap.values()].sort((a, b) => b.count - a.count);

  // 先加载地图数据
  const mapLoaded = await loadChinaMap(echarts);
  if (chart.isDisposed()) return;
  if (!mapLoaded) {
    ElMessage.warning('地图数据加载失败，请重新进入页面重试');
    return;
  }

  const option = {
    animation: false,
    textStyle: { color: chartText() },
    tooltip: {
      renderMode: 'richText',
      confine: true,
      trigger: 'item',
      formatter: (params) => {
        if (params.componentType === 'series' && params.seriesType === 'scatter') {
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
        z: 2,
        symbol: ['none', 'none'],
        silent: true,
        progressive: 1000,
        lineStyle: {
          color: '#ff9800',
          width: 2,
          opacity: 0.6,
          curveness: 0.2
        },
        data: [...routes.values()]
      },
      {
        name: '站点',
        type: 'scatter',
        coordinateSystem: 'geo',
        z: 3,
        labelLayout: { hideOverlap: false },
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
  chart.setOption(option, { notMerge: true });
  return chart;
}

