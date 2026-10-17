import { operationChart } from './chartInstance.js';

export function renderCityChart({ cityChartRef, cityData, isDark, chartText, chartBackground }) {
  if (!cityChartRef.value) return;

  const chart = operationChart(cityChartRef.value, isDark.value);

  const option = {
    animation: false,
    textStyle: { color: chartText() },
    tooltip: {
      show: false,
      triggerOn: 'none'
    },
    series: [
      {
        name: '访问次数',
        type: 'pie',
        silent: true,
        radius: ['30%', '76%'],
        avoidLabelOverlap: true,
        labelLayout: { hideOverlap: true },
        data: cityData.value,
        emphasis: { disabled: true },
        label: {
          position: 'inside',
          formatter: '{c}',
          color: '#ffffff',
          fontSize: 13
        }
      }
    ]
  };

  option.color = cityData.value.map(item => item.color);
  option.aria = { enabled: true };
  option.backgroundColor = chartBackground();
  chart.setOption(option, { notMerge: true });
  return chart;
}

