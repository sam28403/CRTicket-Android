<script setup>
import { ref } from 'vue'

const props = defineProps({ discovery: { type: Object, required: true } })
const busyExpanded = ref(false)
function visibleRows(key) {
  const limit = key === 'busy' && !busyExpanded.value ? 3 : 10
  return props.discovery[key].value.slice(0, limit)
}
function prepareCardLeave(element) {
  const { offsetLeft, offsetTop, offsetWidth, offsetHeight } = element
  Object.assign(element.style, {
    left: `${offsetLeft}px`, top: `${offsetTop}px`,
    width: `${offsetWidth}px`, height: `${offsetHeight}px`,
  })
}
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
      <TransitionGroup
        v-else tag="div" name="station-reveal" class="recommendations"
        @before-leave="prepareCardLeave"
      >
        <button
          v-for="(row, index) in visibleRows(section.key)" :key="row.station"
          :style="{ '--handled': `${row.handledRatio * 100}%` }"
          :class="{ 'fully-handled': row.handledRatio >= 1 }"
          class="station-card" @click="discovery.open(row.station)"
        >
          <div class="card-heading">
            <strong>
              <span v-if="section.key === 'busy'" class="rank">{{ index + 1 }}</span>
              {{ row.station }}
            </strong>
            <span>共 {{ row.total }} 趟</span>
          </div>
          <div class="counts">
            <span>始发 {{ row.origin }} 趟</span><span>终到 {{ row.terminal }} 趟</span>
          </div>
          <div class="card-footer">
            <small>已接发：{{ row.handled }} / {{ row.total }}，在站过路车：{{ row.passing }}</small>
            <span v-if="row.bureau" class="bureau">{{ row.bureau }}</span>
          </div>
        </button>
      </TransitionGroup>
      <el-button
        v-if="section.key === 'busy' && discovery.busy.value.length > 3 &&
          !discovery.loading.value && !discovery.error.value"
        class="busy-toggle" text :aria-expanded="busyExpanded"
        @click="busyExpanded = !busyExpanded"
      >{{ busyExpanded ? '收起第 4～10 名' : '展开第 4～10 名' }}</el-button>
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
  position: relative;
  display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px;
}
.station-reveal-move { transition: transform 300ms ease; }
.station-reveal-enter-active { transition: opacity 220ms ease, transform 300ms ease; }
.station-reveal-leave-active {
  position: absolute; box-sizing: border-box;
  transition: opacity 180ms ease, transform 180ms ease;
}
.station-reveal-enter-from, .station-reveal-leave-to {
  opacity: 0; transform: translateY(-12px);
}
@media (prefers-reduced-motion: reduce) {
  .station-reveal-move, .station-reveal-enter-active, .station-reveal-leave-active {
    transition: none;
  }
}
.station-card {
  padding: 20px; border: 1px solid var(--el-border-color-light);
  border-radius: 24px;
  background: linear-gradient(to right,
    var(--el-color-primary-light-9) 0 var(--handled, 0%),
    var(--el-bg-color) var(--handled, 0%) 100%);
  color: var(--el-text-color-primary);
  text-align: left; font: inherit; cursor: pointer;
}
.station-card:hover { border-color: var(--el-color-primary); }
.card-heading, .counts { display: flex; justify-content: space-between; gap: 12px; }
.card-heading strong { color: var(--el-color-primary); font-size: 24px; }
.card-heading > span { padding: 5px 12px; border-radius: 12px; background: var(--el-fill-color); }
.rank {
  display: inline-grid; place-items: center; width: 28px; height: 28px;
  border: 2px solid currentColor; border-radius: 50%; font-size: 14px;
}
.card-footer { display: flex; align-items: flex-end; justify-content: space-between; gap: 8px; }
.bureau { flex-shrink: 0; font-size: 12px; color: var(--el-text-color-secondary); }
.station-card.fully-handled {
  background: color-mix(in srgb, #4caf50 22%, var(--el-bg-color));
}
.busy-toggle { width: 100%; margin-top: 8px; }
.counts { margin: 16px 0 10px; }
small { color: var(--el-text-color-secondary); }
</style>
