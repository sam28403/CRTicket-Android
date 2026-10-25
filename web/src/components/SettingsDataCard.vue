<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { performSettingsAction } from '@/android/settingsActions.js'
import { parseAppBackup } from '@/utils/appBackup.js'
import { BACKUP_CONTENTS, availableBackupContents } from '@/utils/backupContents.js'
import { decryptBackup, isEncryptedBackup, MAX_BACKUP_FILE_BYTES } from '@/utils/backupEncryption.js'
import BackupPasswordDialog from './BackupPasswordDialog.vue'

const busy = ref(false)
const input = ref(null)
const operation = ref('')
const importText = ref('')
const available = ref([])
const selected = ref([])
const security = ref('')
const encryptedText = ref('')
const securityError = ref('')
const cryptoMessages = {
  backup_crypto_failed: '密码错误或备份文件已损坏',
  backup_crypto_invalid: '加密备份格式无效',
  backup_crypto_version: '此备份格式需要更新的软件版本',
  backup_crypto_size: '备份数据超过大小限制',
  backup_crypto_unavailable: '当前环境不支持安全加密，请使用 HTTPS 或本机应用',
  backup_password_invalid: '备份密码须为 8 至 1024 个字符',
}
const errorText = error => cryptoMessages[error.code] || error.message || '操作失败'

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
  if (action === 'exportBackup') {
    securityError.value = ''
    security.value = 'export'
    return
  }
  await run(action, { contents: selected.value, text: importText.value })
  importText.value = ''
}
async function run(action, payload) {
  busy.value = true
  try {
    ElMessage.success(await performSettingsAction(action, payload))
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = false
  }
}
async function importFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    if (file.size > MAX_BACKUP_FILE_BYTES) throw new Error('备份文件不能超过 28MB')
    const text = await file.text()
    if (isEncryptedBackup(JSON.parse(text))) {
      encryptedText.value = text
      securityError.value = ''
      security.value = 'import'
      return
    }
    await prepareImport(text)
  } catch (error) {
    ElMessage.error(errorText(error))
  }
}

async function prepareImport(text) {
  text = await decryptBackup(text)
  const backup = parseAppBackup(text)
  importText.value = text
  openSelection('import', availableBackupContents(backup))
}

function cancelSecurity() {
  if (busy.value) return
  security.value = ''
  encryptedText.value = ''
  securityError.value = ''
}

async function confirmSecurity(payload) {
  busy.value = true
  try {
    if (security.value === 'export') {
      ElMessage.success(await performSettingsAction('exportBackup', { ...payload, contents: selected.value }))
    } else {
      const text = await decryptBackup(encryptedText.value, payload.password)
      await prepareImport(text)
    }
    security.value = ''
    encryptedText.value = ''
    securityError.value = ''
  } catch (error) {
    securityError.value = errorText(error)
  } finally {
    busy.value = false
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
    <BackupPasswordDialog
      :mode="security" :busy="busy" :error="securityError"
      @submit="confirmSecurity" @cancel="cancelSecurity"
    />
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
