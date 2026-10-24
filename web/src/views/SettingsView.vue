<script setup>
import UserNameCard from '@/components/UserNameCard.vue'
import SettingsAvatarCard from '@/components/SettingsAvatarCard.vue'
import SettingsDataCard from '@/components/SettingsDataCard.vue'
import StationArrivalCard from '@/components/StationArrivalCard.vue'
import SettingsAppearanceCard from '@/components/SettingsAppearanceCard.vue'

const nativeSettings = typeof window.CRTicketAndroid?.settingsResult === 'function'
</script>

<template>
  <main v-if="!nativeSettings" class="settings-fallback">
    <header><RouterLink to="/home">返回</RouterLink><h1>设置</h1></header>
    <h2>个人资料</h2>
    <UserNameCard />
    <SettingsAvatarCard />
    <h2>出行提醒</h2>
    <StationArrivalCard />
    <h2>外观</h2>
    <SettingsAppearanceCard />
    <RouterLink to="/settings/map">地图选择</RouterLink>
    <h2>数据与备份</h2>
    <SettingsDataCard />
  </main>
</template>

<style scoped>
.settings-fallback {
  max-width: 640px;
  margin: auto;
  padding: 24px 16px;
  display: grid;
  gap: 12px;
}
header {
  display: flex;
  align-items: center;
  gap: 24px;
}
h2 {
  font-size: 18px;
  margin: 24px 12px 8px;
  color: var(--app-link);
}
.settings-fallback :deep(.el-card) {
  border-radius: 24px;
}
</style>
