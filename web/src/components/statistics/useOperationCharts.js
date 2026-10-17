import { onBeforeUnmount, watch } from 'vue';
import { useTheme } from '@/composables/useTheme.js';

/** Load map code on demand and perform at most one chart operation per frame. */
export function useOperationCharts(state) {
  const { isDark, resolvedTheme } = useTheme();
  const cssColor = (name, fallback) =>
    getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
  const context = {
    ...state,
    isDark,
    chartText: () => cssColor('--app-text', isDark.value ? '#eeeeee' : '#17324d'),
    chartBackground: () => cssColor('--app-surface', isDark.value ? '#1c1b1f' : '#ffffff'),
  };
  const entries = [
    [state.statisticsChartRef, () => import('./stationChart.js'), 'renderStationChart'],
    [state.cityChartRef, () => import('./cityChart.js'), 'renderCityChart'],
    [state.calendarChartRef, () => import('./operationCalendar.js'), 'renderCalendarChart'],
    [state.mapChartRef, () => import('./journeyMap.js'), 'renderMapChart'],
  ].map(([ref, load, render]) => ({
    ref, load, render, visible: false, dirty: true, resize: false, chart: null, width: 0, height: 0,
  }));
  let alive = true;
  let frame = 0;
  let busy = false;
  function schedule() {
    if (!alive || frame || busy || document.hidden) return;
    frame = requestAnimationFrame(flush);
  }
  async function flush() {
    frame = 0;
    const entry = entries.find(item => item.visible && item.ref.value?.clientWidth
      && (item.dirty || item.resize));
    if (!alive || !entry) return;
    busy = true;
    try {
      if (entry.dirty) {
        entry.dirty = false;
        const element = entry.ref.value;
        const module = await entry.load();
        if (!alive || element !== entry.ref.value) return;
        entry.chart = await module[entry.render](context);
        if (!alive) entry.chart?.dispose();
      } else entry.chart?.resize();
      entry.resize = false;
    } catch (error) {
      console.error('运转图表绘制失败', error);
    } finally {
      busy = false;
      schedule();
    }
  }
  function invalidate(indexes) {
    for (const index of indexes) entries[index].dirty = true;
    schedule();
  }
  const intersection = new IntersectionObserver(changes => {
    for (const change of changes) {
      const entry = entries.find(item => item.ref.value === change.target);
      if (entry) entry.visible = change.isIntersecting;
    }
    schedule();
  }, { rootMargin: '100px' });
  const sizes = new ResizeObserver(changes => {
    for (const { target, contentRect } of changes) {
      const entry = entries.find(item => item.ref.value === target);
      if (!entry || (entry.width === contentRect.width && entry.height === contentRect.height)) continue;
      if (entry === entries[0] && entry.width !== contentRect.width) entry.dirty = true;
      entry.width = contentRect.width;
      entry.height = contentRect.height;
      entry.resize = true;
    }
    schedule();
  });
  watch(entries.map(item => item.ref), () => {
    intersection.disconnect();
    sizes.disconnect();
    for (const entry of entries) {
      if (!entry.ref.value) continue;
      intersection.observe(entry.ref.value);
      sizes.observe(entry.ref.value);
    }
  }, { flush: 'post' });
  watch(state.statisticsData, () => invalidate([0, 1]));
  watch(state.monthlyData, () => invalidate([2]));
  watch(state.ticketHistory, () => invalidate([3]));
  const invalidateTheme = () => invalidate([0, 1, 2, 3]);
  watch(resolvedTheme, invalidateTheme, { flush: 'post' });
  window.addEventListener('crticket-native-palette', invalidateTheme);
  document.addEventListener('visibilitychange', schedule);
  onBeforeUnmount(() => {
    alive = false;
    intersection.disconnect();
    sizes.disconnect();
    cancelAnimationFrame(frame);
    window.removeEventListener('crticket-native-palette', invalidateTheme);
    document.removeEventListener('visibilitychange', schedule);
    for (const entry of entries) entry.chart?.dispose();
  });
}
