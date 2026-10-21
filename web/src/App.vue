<script setup>
import PullToRefresh from '@/components/PullToRefresh.vue'
import ImportantNoticeDialog from '@/components/ImportantNoticeDialog.vue'
import { native } from '@/android/platform.js'
import { ref } from 'vue'
import { useVisitedStationCards } from '@/android/visitedStationCards.js'
import { useVehicleTrainCards } from '@/android/vehicleTrainCards.js'

useVisitedStationCards()
useVehicleTrainCards()

const noticeVisible = ref(!native &&
  localStorage.getItem('crticket-important-notice-hidden') !== '1' &&
  sessionStorage.getItem('crticket-important-notice-confirmed') !== '1')

function confirmNotice(doNotShowAgain) {
  sessionStorage.setItem('crticket-important-notice-confirmed', '1')
  if (doNotShowAgain) localStorage.setItem('crticket-important-notice-hidden', '1')
}
</script>

<template>
  <ImportantNoticeDialog v-model="noticeVisible" @confirm="confirmNotice" />
  <PullToRefresh />
  <router-view v-slot="{ Component }">
    <KeepAlive include="TrainList,TrainQuery,StationBoard,TicketMonitor">
      <component :is="Component" />
    </KeepAlive>
  </router-view>
</template>
