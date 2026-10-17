import { operationChart } from './chartInstance.js';

export function renderCalendarChart({ calendarChartRef, selectedMonth, monthlyData, isDark, chartText, chartBackground }) {
  if (!calendarChartRef.value || !selectedMonth.value) return;

  const container = calendarChartRef.value;
  const [year, month] = selectedMonth.value.split('-').map(Number);
  const daysInMonth = new Date(year, month, 0).getDate();
  const firstWeekday = (new Date(year, month - 1, 1).getDay() + 6) % 7;
  const weeks = Math.ceil((firstWeekday + daysInMonth) / 7);
  const height = 30 + weeks * 42 + 4;
  container.style.height = `${height}px`;
  const chart = operationChart(container, isDark.value);
  chart.resize({ height });

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
    animation: false,
    textStyle: { color: chartText() },
    tooltip: {
      show: false,
      triggerOn: 'none'
    },
    visualMap: {
      min: 0,
      max: maxCount,
      calculable: false,
      show: false,
      hoverLink: false,
      inRange: {
        color: ['#ebedee', '#c3d6f2', '#86a8e7', '#5470c6', '#23448e']
      }
    },
    calendar: {
      orient: 'vertical',
      top: 30,
      left: 30,
      right: 20,
      cellSize: ['auto', 42],
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
      silent: true,
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
      emphasis: { disabled: true },
      data: calendarData
    }]
  };

  option.backgroundColor = chartBackground();
  chart.setOption(option, { notMerge: true });
  return chart;
}

