<script setup>
import PullToRefresh from '@/components/PullToRefresh.vue'
</script>

<template>
  <PullToRefresh />
  <router-view v-slot="{ Component }">
    <KeepAlive include="TrainList,TrainQuery">
      <component :is="Component" />
    </KeepAlive>
  </router-view>
</template>
