<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElRadio, ElRadioGroup } from 'element-plus'
import StationLocationMap from '@/components/StationLocationMap.vue'
import { mapSources } from '@/utils/maps/mapSources.js'
import { useMapSource } from '@/composables/useMapSource.js'

const selected = useMapSource()
const showWarning = ref(false)
watch(selected, () => { showWarning.value = false })
function failed() {
  showWarning.value = true
}
onBeforeUnmount(() => { showWarning.value = false })
</script>

<template>
  <main class="map-settings">
    <header><RouterLink to="/settings">返回</RouterLink><h1>地图选择</h1></header>
    <div class="map-preview">
      <StationLocationMap :key="selected" station="北京" :source="selected" @failed="failed" />
    </div>
    <el-radio-group v-model="selected" aria-label="地图选择">
      <el-radio v-for="source in mapSources" :key="source.id" :value="source.id" border>
        {{ source.name }}
      </el-radio>
    </el-radio-group>
    <el-dialog v-model="showWarning" title="地图加载失败" width="min(420px, 90vw)" append-to-body>
      <p>当前地图源无法加载，请更换地图源。</p>
      <template #footer><el-button type="primary" @click="showWarning = false">关闭</el-button></template>
    </el-dialog>
  </main>
</template>

<style scoped>
.map-settings {
  max-width: 640px;
  margin: auto;
  padding: 24px 16px;
  display: grid;
  gap: 16px;
}
header { display: flex; align-items: center; gap: 24px; }
.map-preview { height: 260px; overflow: hidden; border-radius: 24px; }
.el-radio-group { display: grid; gap: 4px; }
.el-radio { margin: 0; padding: 20px; height: auto; min-height: 64px; border-radius: 20px; }
</style>
