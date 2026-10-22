<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { performSettingsAction } from '@/android/settingsActions.js'
import { parseAppBackup } from '@/utils/appBackup.js'
import { BACKUP_CONTENTS, availableBackupContents } from '@/utils/backupContents.js'

const busy = ref(false)
const input = ref(null)
const operation = ref('')
const importText = ref('')
const available = ref([])
const selected = ref([])

function openSelection(action, contents) {
  available.value = contents
  selected.value = [...contents]
  operation.value = action
}

function exportBackup() {
  openSelection('exportBackup', BACKUP_CONTENTS.map(item => item.id))
}

async function confirmBackup() {
  const action = operation.value
  operation.value = ''
  await run(action, { contents: selected.value, text: importText.value })
  importText.value = ''
}
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
    importText.value = text
    openSelection('import', availableBackupContents(backup))
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
      <el-button :disabled="busy" @click="exportBackup">导出备份</el-button>
      <el-button :disabled="busy" @click="input.click()">导入备份</el-button>
    </div>
    <input ref="input" type="file" accept=".json,application/json" hidden @change="importFile" />
    <el-dialog
      :model-value="!!operation"
      :title="operation === 'import' ? '导入备份' : '导出备份'"
      width="min(440px, 92vw)"
      @close="operation = ''; importText = ''"
    >
      <el-checkbox-group v-model="selected" class="backup-options">
        <template v-for="item in BACKUP_CONTENTS" :key="item.id">
          <el-checkbox v-if="available.includes(item.id)" :value="item.id">
            {{ item.label }}
          </el-checkbox>
        </template>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="operation = ''; importText = ''">取消</el-button>
        <el-button type="primary" :disabled="!selected.length" @click="confirmBackup">
          {{ operation === 'import' ? '开始导入' : '开始导出' }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.data-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.backup-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 55vh;
  overflow-y: auto;
}
.backup-options :deep(.el-checkbox) {
  min-height: 44px;
  margin-right: 0;
}
.backup-options :deep(.el-checkbox__label) {
  line-height: 1;
}
</style>
