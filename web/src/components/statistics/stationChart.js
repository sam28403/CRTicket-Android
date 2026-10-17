import { operationChart } from './chartInstance.js';

export function renderStationChart({ statisticsChartRef, statisticsData, isDark, chartText, chartBackground }) {
  if (!statisticsChartRef.value) return;

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

  // Give every station its own row, including full names on a narrow phone screen.
  const container = statisticsChartRef.value;
  // Reserve six 12px Chinese characters rather than a percentage of the chart.
  const labelWidth = 72;
  const longestName = Math.max(1, ...sortedStations.map(station => [...station.name].length));
  const labelLines = Math.ceil(longestName / Math.max(1, Math.floor(labelWidth / 12)));
  const rowHeight = Math.max(28, labelLines * 16 + 12);
  const height = Math.max(320, sortedStations.length * rowHeight + 76);
  container.style.height = `${height}px`;
  const chart = operationChart(container, isDark.value);
  chart.resize({ height });

  const option = {
    animation: false,
    textStyle: { color: chartText() },
    tooltip: {
      show: false,
      triggerOn: 'none'
    },
    axisPointer: { show: false, triggerOn: 'none' },
    legend: {
      data: ['出发次数', '到达次数'],
      textStyle: { color: chartText() },
      selectedMode: false,
      bottom: 0
    },
    grid: {
      left: 8,
      right: 28,
      bottom: 44,
      top: 28,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitNumber: Math.max(2, Math.min(4, Math.floor((container.clientWidth - labelWidth - 48) / 64))),
      axisLabel: {
        color: chartText(),
        fontSize: 10,
        hideOverlap: true,
        showMinLabel: true,
        showMaxLabel: true
      },
      splitLine: { lineStyle: { color: isDark.value ? '#45424a' : '#e0dce4' } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: sortedStations.map(s => s.name),
      axisLabel: {
        interval: 0,
        hideOverlap: false,
        width: labelWidth,
        overflow: 'break',
        fontSize: 12,
        lineHeight: 16,
        color: chartText()
      }
    },
    series: [
      {
        name: '出发次数',
        type: 'bar',
        silent: true,
        emphasis: { disabled: true },
        data: sortedStations.map(s => s.departure),
        itemStyle: { color: '#5470c6' }
      },
      {
        name: '到达次数',
        type: 'bar',
        silent: true,
        emphasis: { disabled: true },
        data: sortedStations.map(s => s.arrival),
        itemStyle: { color: '#91cc75' }
      }
    ]
  };

  option.backgroundColor = chartBackground();
  chart.setOption(option, { notMerge: true });
  return chart;
}

