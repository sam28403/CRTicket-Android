<script setup>
// Android draws this route with the native Material 3 Expressive home layer.
// Keep links available when the bundled pages are opened in a browser.
const nativeHome = typeof window.CRTicketAndroid?.setHomeVisible === 'function'
const entries = [
  { route: '/', title: '车票生成', description: '制作车票、查询车次与余票' },
  { route: '/history', title: '历史记录', description: '查看与管理已保存的车票' },
  { route: '/user', title: '运转数据', description: '站点统计、运转日历与出行轨迹' },
  { route: '/monitor', title: '余票监控', description: '关注余票变化与有票提醒' },
  { route: '/train', title: '车次查询', description: '查询时刻表与列车详情' },
  { route: '/list', title: '车次列表', description: '浏览路路通离线车次' },
  { route: '/station', title: '车站大屏', description: '查看车站到发信息' },
  { route: '/settings', title: '设置', description: '个人资料、到站检测与数据备份' },
  { route: '/about', title: '关于', description: '应用信息与项目主页' },
]
</script>

<template>
  <main v-if="!nativeHome" class="home-fallback">
    <h1>CRTicket</h1>
    <nav aria-label="首页">
      <RouterLink v-for="entry in entries" :key="entry.route" :to="entry.route">
        <strong>{{ entry.title }}</strong>
        <span>{{ entry.description }}</span>
      </RouterLink>
    </nav>
  </main>
</template>

<style scoped>
.home-fallback {
  max-width: 640px;
  margin: 0 auto;
  padding: 48px 20px;
  color: var(--app-text, var(--el-text-color-primary));
}

h1 {
  font-size: 40px;
  font-weight: 500;
  margin: 16px 0 40px;
}

nav {
  display: grid;
  gap: 2px;
}

nav > * {
  display: grid;
  gap: 8px;
  padding: 24px;
  border-radius: 4px;
  background: var(--app-surface, var(--el-bg-color));
  color: inherit;
  text-decoration: none;
}

nav > :first-child {
  border-radius: 24px 24px 4px 4px;
}

nav > :nth-child(7) {
  border-radius: 4px 4px 24px 24px;
  margin-bottom: 14px;
}

nav > :nth-child(8) {
  border-radius: 24px 24px 4px 4px;
}

nav > :last-child {
  border-radius: 4px 4px 24px 24px;
}

strong {
  font-size: 22px;
}

span {
  color: var(--app-muted, var(--el-text-color-regular));
}
</style>
