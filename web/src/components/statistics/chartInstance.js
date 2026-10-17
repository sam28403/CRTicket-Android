import * as echarts from 'echarts';

export function operationChart(element, dark) {
  return echarts.getInstanceByDom(element) || echarts.init(element, dark ? 'dark' : undefined, {
    renderer: 'canvas',
    useDirtyRect: true,
    devicePixelRatio: Math.min(window.devicePixelRatio || 1, 2),
  });
}
