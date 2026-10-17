<template>
  <button
    v-if="nativePicker"
    type="button"
    class="operation-month-button"
    aria-label="选择运转月份"
    aria-haspopup="dialog"
    @click="native.chooseStatisticsMonth(modelValue)"
  >
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 3v4m10-4v4M4 10h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z" />
    </svg>
    <span>{{ modelValue.replace('-', '年') }}月</span>
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg>
  </button>
  <el-date-picker
    v-else
    :model-value="modelValue"
    type="month"
    placeholder="选择月份"
    aria-label="选择运转月份"
    format="YYYY-MM"
    value-format="YYYY-MM"
    :clearable="false"
    @update:model-value="select"
  />
</template>

<script setup>
import { onBeforeUnmount } from 'vue';
import { native } from '@/android/platform.js';

defineProps({ modelValue: { type: String, required: true } });
const emit = defineEmits(['update:modelValue']);
const nativePicker = typeof native?.chooseStatisticsMonth === 'function';
function select(month) {
  if (typeof month === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
    emit('update:modelValue', month);
  }
}
const onNativeMonth = event => select(event.detail);
if (nativePicker) window.addEventListener('crticket-statistics-month', onNativeMonth);
onBeforeUnmount(() => window.removeEventListener('crticket-statistics-month', onNativeMonth));
</script>

<style scoped>
.operation-month-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  max-width: 100%;
  padding: 8px 12px;
  border: 0;
  border-radius: 24px;
  background: var(--app-hover, #eee8f4);
  color: var(--app-link, #65558f);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.operation-month-button svg {
  width: 18px;
  height: 18px;
  flex: none;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.operation-month-button:focus-visible { outline: 2px solid currentColor; outline-offset: 2px; }
</style>
