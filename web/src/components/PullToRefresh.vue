<script setup>
import { usePullRefresh } from '@/composables/usePullRefresh.js'

const { progress, refreshing, armed, nativeIndicator } = usePullRefresh()
</script>

<template>
  <div
    v-if="!nativeIndicator && (progress > 0 || refreshing)"
    class="pull-refresh-indicator"
    :class="{ 'is-refreshing': refreshing, 'is-armed': armed }"
    :style="{ '--pull-progress': progress }"
    role="status"
    aria-live="polite"
    :aria-label="refreshing ? '正在刷新' : armed ? '松开刷新' : '下拉刷新'"
  >
    <span class="refresh-shape" aria-hidden="true" />
    <span class="refresh-label">{{ refreshing ? '正在刷新' : armed ? '松开刷新' : '下拉刷新' }}</span>
  </div>
</template>

<style scoped>
.pull-refresh-indicator {
  position: fixed;
  z-index: 4000;
  top: 0;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  color: var(--el-color-primary);
  background: var(--app-surface, #fff);
  border: 1px solid var(--el-border-color-light);
  border-radius: 28px;
  box-shadow: 0 4px 18px rgb(0 0 0 / 14%);
  pointer-events: none;
  transform: translate(-50%, calc(-62px + min(var(--pull-progress), 1) * 84px));
}
.refresh-shape {
  width: 28px;
  height: 28px;
  background: currentColor;
  border-radius: 35% 65% 35% 65%;
  transform: rotate(calc(var(--pull-progress) * 180deg));
}
.refresh-label { font-size: 13px; white-space: nowrap; }
.is-armed .refresh-shape { border-radius: 50% 20% 50% 20%; }
.is-refreshing .refresh-shape { animation: expressive-refresh 1s ease-in-out infinite; }
@keyframes expressive-refresh {
  0% { transform: rotate(0deg); border-radius: 35% 65% 35% 65%; }
  50% { border-radius: 50% 20% 50% 20%; }
  100% { transform: rotate(360deg); border-radius: 35% 65% 35% 65%; }
}
@media (prefers-reduced-motion: reduce) {
  .is-refreshing .refresh-shape { animation: none; }
}
</style>
