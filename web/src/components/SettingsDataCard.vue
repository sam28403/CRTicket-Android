<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { performSettingsAction } from '@/android/settingsActions.js'
import { parseAppBackup } from '@/utils/appBackup.js'

const busy = ref(false)
const input = ref(null)
async function run(action, payload) {
  busy.value = true
  try {
    ElMessage.success(await performSettingsAction(action, payload))
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    busy.value = false
  }
}
async function importFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    if (file.size > 20 * 1024 * 1024) throw new Error('备份文件不能超过 20MB')
    const text = await file.text()
    const backup = parseAppBackup(text)
    const settingsNotice = backup.settings ? '个人资料和设置将恢复为备份中的内容。' : ''
    await ElMessageBox.confirm(
      `导入 ${backup.tickets.length} 条记录，已有的相同记录会略过。${settingsNotice}`, '导入备份',
    )
    await run('import', { text })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message)
  }
}
</script>

<template>
  <el-card shadow="never">
    <template #header>数据与备份</template>
    <div class="data-actions">
      <el-button :disabled="busy" @click="run('exportExcel')">导出 Excel</el-button>
      <el-button :disabled="busy" @click="run('exportBackup')">导出备份</el-button>
      <el-button :disabled="busy" @click="input.click()">导入备份</el-button>
    </div>
    <input ref="input" type="file" accept=".json,application/json" hidden @change="importFile" />
  </el-card>
</template>

<style scoped>
.data-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
