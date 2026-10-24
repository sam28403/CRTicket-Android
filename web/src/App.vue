<script setup>
import PullToRefresh from '@/components/PullToRefresh.vue'
import ImportantNoticeDialog from '@/components/ImportantNoticeDialog.vue'
import { native } from '@/android/platform.js'
import { ref } from 'vue'
import { useVisitedStationCards } from '@/android/visitedStationCards.js'
import { useVehicleTrainCards } from '@/android/vehicleTrainCards.js'
import { useRoutingQuery } from '@/android/routingQuery.js'

useVisitedStationCards()
useVehicleTrainCards()
useRoutingQuery()

const standaloneMap = window.location.hash.split('?')[0] === '#/station-map'
const noticeVisible = ref(!native && !standaloneMap &&
  localStorage.getItem('crticket-important-notice-hidden') !== '1' &&
  sessionStorage.getItem('crticket-important-notice-confirmed') !== '1')

function confirmNotice(doNotShowAgain) {
  sessionStorage.setItem('crticket-important-notice-confirmed', '1')
  if (doNotShowAgain) localStorage.setItem('crticket-important-notice-hidden', '1')
}
</script>

<template>
  <ImportantNoticeDialog v-if="!standaloneMap" v-model="noticeVisible" @confirm="confirmNotice" />
  <PullToRefresh v-if="!standaloneMap" />
  <router-view v-slot="{ Component }">
    <KeepAlive include="TrainList,TrainQuery,StationBoard,TicketMonitor">
      <component :is="Component" />
    </KeepAlive>
  </router-view>
</template>
