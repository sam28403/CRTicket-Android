<template>
  <section class="history-records">
    <div class="history-summary">
      <p>
        你已经运转了 <strong>{{ trips }}</strong> 次，
        <strong>{{ distance }}</strong> 公里，共消费 <strong>{{ money }}</strong> 元！
      </p>
      <el-button type="primary" round size="large" @click="$emit('create')">
        <el-icon><Plus /></el-icon>补登记录
      </el-button>
    </div>
    <div class="history-toolbar">
      <h2>历史记录</h2>
      <el-select
        :model-value="`${sort.prop}:${sort.order}`"
        aria-label="历史记录排序"
        @update:model-value="changeSort"
      >
        <el-option label="日期从近到远" value="date:descending" />
        <el-option label="日期从远到近" value="date:ascending" />
        <el-option label="票价从高到低" value="price:descending" />
        <el-option label="票价从低到高" value="price:ascending" />
      </el-select>
    </div>
    <p v-if="loading" role="status">正在读取记录…</p>
    <div v-if="error" role="alert">
      {{ error }} <el-button text @click="$emit('refresh')">重试</el-button>
    </div>
    <el-empty v-if="!loading && !error && !rows.length" description="还没有运转记录，补登一段旅程吧。" />
    <div class="history-record-grid">
      <article v-for="row in rows" :key="row.id" class="history-record">
        <div class="record-heading">
          <span>{{ row.number }}</span>
          <el-dropdown trigger="click" @command="action => $emit(action, row)">
            <el-button text circle aria-label="记录操作"><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="download">下载车票</el-dropdown-item>
                <el-dropdown-item command="edit">编辑记录</el-dropdown-item>
                <el-dropdown-item command="delete">删除记录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="record-route">
          <strong>{{ row.from }}</strong>
          <span class="record-train">{{ row.trainNo }}<span>→</span></span>
          <strong>{{ row.to }}</strong>
        </div>
        <p class="record-date">{{ row.date }}<template v-if="row.time"> · {{ row.time }}</template></p>
        <div class="record-details">
          <strong>{{ Number(row.useCredit) === 1 ? '积分 ' : '¥' }}{{ row.price }}</strong>
          <span>{{ row.seatNo }} · {{ row.seatType }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { MoreFilled, Plus } from '@element-plus/icons-vue'
import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus'

defineProps({
  rows: { type: Array, default: () => [] },
  trips: Number,
  distance: Number,
  money: String,
  sort: Object,
  loading: Boolean,
  error: String,
})
const emit = defineEmits(['create', 'download', 'edit', 'delete', 'sort', 'refresh'])
function changeSort(value) {
  const [prop, order] = value.split(':')
  emit('sort', { prop, order })
}
</script>

<style scoped>
.history-records {
  color: var(--app-text, #1f2937);
}
.history-summary {
  padding: 28px;
  border-radius: 32px;
  background: var(--app-primary-container, #e4e1ff);
  color: var(--app-text, #28234b);
}
.history-summary p {
  margin: 0 0 20px;
  font-size: 24px;
  line-height: 1.6;
}
.history-summary .el-button {
  margin: 0;
}
.history-toolbar, .record-heading, .record-details {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.history-toolbar {
  margin: 24px 4px 16px;
  flex-wrap: wrap;
}
.history-toolbar h2 {
  margin: 0;
  font-size: 20px;
}
.history-toolbar .el-select {
  width: 168px;
}
.history-record-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
  gap: 12px;
}
.history-record {
  min-width: 0;
  padding: 14px 20px 22px;
  border-radius: 24px;
  background: var(--app-surface, #fff);
}
.record-heading, .record-details, .record-date {
  font-size: 13px;
  color: var(--app-text-secondary, #666);
}
.record-heading > span, small, .record-details > span {
  overflow-wrap: anywhere;
}
.record-route {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
  margin: 12px 0 22px;
}
.record-route > strong {
  font-size: 22px;
  overflow-wrap: anywhere;
}
.record-route > strong:last-child {
  text-align: right;
}
.record-train {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 6px 12px;
  border-radius: 12px;
  background: var(--app-hover, #eeecff);
  font-weight: 600;
}
.record-details strong {
  color: var(--app-primary, #6252a0);
  white-space: nowrap;
}
small {
  display: block;
  margin-top: 10px;
}
</style>
