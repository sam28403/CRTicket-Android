<script setup>
import { computed } from 'vue'

const props = defineProps({
  stop: { type: Object, required: true },
  field: { type: String, required: true },
  placeholder: { type: String, default: '' },
})
const offset = computed(() => props.stop[`${props.field}Offset`] || 0)
</script>

<template>
  <span :class="{ delayed: !placeholder && offset > 0, early: !placeholder && offset < 0 }">
    {{ placeholder || stop[field] || '—' }}<template v-if="!placeholder && offset"> ({{ offset > 0 ? '+' : '' }}{{ offset }}')</template>
  </span>
</template>

<style scoped>
.delayed { color: var(--el-color-danger); }
.early { color: var(--el-color-success); }
</style>
