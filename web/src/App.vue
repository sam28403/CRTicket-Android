<script setup>
import PullToRefresh from '@/components/PullToRefresh.vue'
import { useVisitedStationCards } from '@/android/visitedStationCards.js'
import { useVehicleTrainCards } from '@/android/vehicleTrainCards.js'
import { useRoutingQuery } from '@/android/routingQuery.js'

useVisitedStationCards()
useVehicleTrainCards()
useRoutingQuery()

const standaloneMap = window.location.hash.split('?')[0] === '#/station-map'
</script>

<template>
  <PullToRefresh v-if="!standaloneMap" />
  <router-view v-slot="{ Component }">
    <KeepAlive include="TrainList,TrainQuery,StationBoard,TicketMonitor">
      <component :is="Component" />
    </KeepAlive>
  </router-view>
</template>
