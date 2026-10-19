<script setup>
defineProps({ discovery: { type: Object, required: true } })
</script>

<template>
  <section class="discovery">
    <Transition name="history-collapse">
      <div v-if="discovery.history.value.length" class="history-wrapper">
        <div class="history" aria-label="历史查询">
          <el-button
            v-for="code in discovery.history.value"
            :key="code"
            round
            @click="discovery.open(code)"
          >{{ code }}</el-button>
          <el-button round text @click="discovery.clearHistory">清空</el-button>
        </div>
      </div>
    </Transition>
    <h2>今日随机推荐</h2>
    <p v-if="discovery.recommendationLoading.value">正在加载推荐车次…</p>
    <div v-else-if="discovery.recommendationError.value">
      {{ discovery.recommendationError.value }}
      <el-button text @click="discovery.refresh">重试</el-button>
    </div>
    <p v-else-if="!discovery.recommendations.value.length">今日暂无可推荐车次</p>
    <div class="recommendations">
      <button
        v-for="row in discovery.recommendations.value" :key="row.train"
        class="train-card" @click="discovery.open(row.queryTrain)"
      >
        <div class="card-heading">
          <strong>{{ row.train }}</strong>
          <span>{{ row.stationCount ?? '暂无' }} 站</span>
        </div>
        <div class="route"><span>{{ row.from }}</span> → <span>{{ row.to }}</span></div>
        <small>{{ row.duration || '暂无' }} · {{ row.mileage ?? '暂无' }} km</small>
      </button>
    </div>
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
.recommendations { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px; }
.train-card {
  padding: 20px; border: 1px solid var(--el-border-color-light);
  border-radius: 24px; background: var(--el-bg-color); color: var(--el-text-color-primary);
  text-align: left; font: inherit; cursor: pointer;
}
.train-card:hover { border-color: var(--el-color-primary); }
.card-heading, .route { display: flex; justify-content: space-between; gap: 12px; }
.card-heading strong { color: var(--el-color-primary); font-size: 24px; }
.card-heading span { padding: 5px 12px; border-radius: 12px; background: var(--el-fill-color); }
.route { margin: 16px 0 10px; }
small { color: var(--el-text-color-secondary); }
</style>
