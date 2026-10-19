<script setup>
defineProps({ discovery: { type: Object, required: true } })
const sections = [
  { key: 'busy', title: '繁忙车站' },
  { key: 'recommendations', title: '今日随机推荐' },
]
</script>

<template>
  <section class="discovery">
    <Transition name="history-collapse">
      <div v-if="discovery.history.value.length" class="history-wrapper">
        <div class="history" aria-label="历史查询">
          <el-button
            v-for="name in discovery.history.value" :key="name" round
            @click="discovery.open(name)"
          >{{ name }}</el-button>
          <el-button round text @click="discovery.clearHistory">清空</el-button>
        </div>
      </div>
    </Transition>
    <section v-for="section in sections" :key="section.key">
      <h2>{{ section.title }}</h2>
      <p v-if="discovery.loading.value">正在加载车站…</p>
      <p v-else-if="discovery.error.value">
        {{ discovery.error.value }}
        <el-button text @click="discovery.refresh">重试</el-button>
      </p>
      <p v-else-if="!discovery[section.key].value.length">今日暂无可展示车站</p>
      <div v-else class="recommendations">
        <button
          v-for="row in discovery[section.key].value" :key="row.station"
          class="station-card" @click="discovery.open(row.station)"
        >
          <div class="card-heading">
            <strong>{{ row.station }}</strong><span>共 {{ row.total }} 趟</span>
          </div>
          <div class="counts">
            <span>始发 {{ row.origin }} 趟</span><span>终到 {{ row.terminal }} 趟</span>
          </div>
          <small>已接发：{{ row.handled }} / {{ row.total }}，在站过路车：{{ row.passing }}</small>
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.discovery { margin-top: 16px; }
.history { display: flex; flex-wrap: wrap; gap: 8px; }
.history .el-button { margin: 0; }
.history-wrapper { display: grid; grid-template-rows: 1fr; }
.history-wrapper > .history { overflow: hidden; min-height: 0; }
.history-collapse-leave-active {
  transition: grid-template-rows 300ms ease, opacity 180ms ease;
}
.history-collapse-leave-to { grid-template-rows: 0fr; opacity: 0; }
h2 { margin: 24px 0 16px; font-size: 20px; }
.recommendations {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px;
}
.station-card {
  padding: 20px; border: 1px solid var(--el-border-color-light);
  border-radius: 24px; background: var(--el-bg-color); color: var(--el-text-color-primary);
  text-align: left; font: inherit; cursor: pointer;
}
.station-card:hover { border-color: var(--el-color-primary); }
.card-heading, .counts { display: flex; justify-content: space-between; gap: 12px; }
.card-heading strong { color: var(--el-color-primary); font-size: 24px; }
.card-heading span { padding: 5px 12px; border-radius: 12px; background: var(--el-fill-color); }
.counts { margin: 16px 0 10px; }
small { color: var(--el-text-color-secondary); }
</style>
