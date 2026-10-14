<script setup>
import { computed, ref } from 'vue'
import { ElRadio, ElRadioGroup } from 'element-plus'
import { useTheme } from '@/composables/useTheme.js'
import { appearanceModes, appearancePreference } from '@/composables/themePreference.js'

const { preference, highContrast } = useTheme()
const visible = ref(false)
const mode = computed({
  get: () => preference.value.split('-').at(-1),
  set: value => { preference.value = appearancePreference(value, highContrast.value) },
})
const pureBlack = computed({
  get: () => highContrast.value,
  set: value => { preference.value = appearancePreference(mode.value, value) },
})
</script>

<template>
  <el-card shadow="never">
    <el-button @click="visible = true">切换主题</el-button>
    <p>{{ appearanceModes.find(option => option.value === mode)?.label }}</p>
    <el-switch v-model="pureBlack" aria-label="纯黑背景" active-text="纯黑背景" />
    <p>在暗色模式下使用纯黑背景</p>
    <el-dialog v-model="visible" title="切换主题" width="320px">
      <el-radio-group v-model="mode" class="theme-radios" @change="visible = false">
        <el-radio v-for="option in appearanceModes" :key="option.value" :value="option.value">
          {{ option.label }}
        </el-radio>
      </el-radio-group>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.theme-radios {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
</style>
