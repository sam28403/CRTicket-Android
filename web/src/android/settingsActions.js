import api from '@/api.js'
import { useUserStore } from '@/stores/user.js'
import { normalizeUsername } from '@/utils/username.js'
import { useTheme } from '@/composables/useTheme.js'
import { appearancePreference } from '@/composables/themePreference.js'
import { initializeNativeStorage } from './storageActions.js'
import {
  buildExcelRows, getExportFileStamp,
} from '@/utils/ticketBackup.js'
import { exportSettingsBackup, importSettingsBackup } from './settingsBackup.js'
import { saveFile, native } from './platform.js'
import { settingsText } from './settingsStrings.js'
import { encryptBackup, decryptBackup, BackupEncryptionError } from '../utils/backupEncryption.js'
import {
  supportsStationArrival, getStationArrivalState,
  enableStationArrival, disableStationArrival,
} from './stationArrival.js'

async function owner() {
  const { data } = await api.get('/user/session')
  if (!data.success) throw new Error(data.message || settingsText('read_failed'))
  useUserStore().setUser(data.user)
  return data.user
}

async function tickets(user) {
  const { data } = await api.get(`/ticket/list/${user.id}`)
  return Array.isArray(data) ? data : data.data || []
}

export async function settingsSnapshot() {
  const user = await owner()
  const arrival = supportsStationArrival()
    ? await getStationArrivalState()
    : { enabled: false, active: false, supported: false, message: settingsText('arrival_unsupported') }
  const { preference, highContrast } = useTheme()
  return {
    username: user.username, avatar: user.avatar, arrival,
    mode: preference.value.split('-').at(-1), pureBlack: highContrast.value,
  }
}

export async function performSettingsAction(action, payload = {}) {
  if (action === 'snapshot') return ''
  if (action === 'theme') {
    useTheme().preference.value = appearancePreference(payload.mode, payload.pureBlack)
    return ''
  }
  if (action === 'name' || action === 'avatar') {
    const body = action === 'name'
      ? { username: normalizeUsername(payload.username) }
      : { avatar: payload.avatar || null }
    const { data } = await api.post(`/user/update-${action}`, body)
    if (!data.success) throw new Error(data.message || settingsText('save_failed'))
    useUserStore().setUser(data.user)
    return data.message || settingsText('saved')
  }
  if (action === 'arrival') {
    const state = await (payload.enabled ? enableStationArrival() : disableStationArrival())
    if (state.success === false) throw new Error(state.message || settingsText('arrival_failed'))
    return state.message || settingsText('arrival_updated')
  }
  const user = await owner()
  const records = await tickets(user)
  if (action === 'import') {
    return importSettingsBackup(payload.text, records, user, api, {
      noTickets: settingsText('backup_no_tickets'),
      limit: settingsText('backup_limit'),
      failed: settingsText('import_failed'),
      complete: (...counts) => settingsText('import_complete', ...counts),
    }, payload.contents)
  }
  const stamp = getExportFileStamp()
  let blob
  let filename
  if (action === 'exportBackup') {
    const text = JSON.stringify(exportSettingsBackup(records, user, payload.contents), null, 2)
    const output = payload.encryption === 'none'
      ? await decryptBackup(text) : await encryptBackup(text, payload.password)
    blob = new Blob([output], {
      type: 'application/json;charset=utf-8',
    })
    filename = settingsText('backup_filename', stamp)
  } else if (action === 'exportExcel') {
    if (!records.length) throw new Error(settingsText('export_empty'))
    const XLSX = await import('xlsx')
    const sheet = XLSX.utils.json_to_sheet(buildExcelRows(records))
    sheet['!cols'] = Array.from({ length: 17 }, () => ({ wch: 18 }))
    const book = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(book, sheet, settingsText('export_sheet'))
    blob = new Blob([XLSX.write(book, { bookType: 'xlsx', type: 'array' })], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    filename = settingsText('export_filename', stamp)
  } else {
    throw new Error(settingsText('unknown_action'))
  }
  const saved = await saveFile(blob, filename, {
    sizeLimit: settingsText('export_size_limit'),
    readFailed: settingsText('export_read_failed'),
    saveFailed: settingsText('file_save_failed'),
  })
  return settingsText(saved ? 'file_saved' : 'file_cancelled')
}

export function initializeNativeSettings() {
  if (typeof native?.settingsResult !== 'function') return
    initializeNativeStorage()
  window.crticketSettings = async (id, action, payload) => {
    let message = ''
    let success = true
    let backupText
    try {
      if (action === 'unlockBackup') backupText = await decryptBackup(payload.text, payload.password)
      else message = await performSettingsAction(action, payload)
    } catch (error) {
      message = error instanceof BackupEncryptionError ? settingsText(error.code)
        : error.response?.data?.message || error.message || settingsText('operation_failed')
      success = false
    }
    if (action === 'unlockBackup') {
      const result = { id, success, message }
      if (success) native.unlockedBackup(id, backupText)
      else native.settingsResult(JSON.stringify(result))
      return result
    }
    try {
      const state = await settingsSnapshot()
      const result = { id, success, message, state, backupText,
        backupRestored: success && action === 'import' }
      native.settingsResult(JSON.stringify(result))
      return result
    } catch (error) {
      if (success && action === 'import') {
        const result = { id, success: true, message, backupRestored: true }
        native.settingsResult(JSON.stringify(result))
        return result
      }
      const result = { id, success: false, message: message || error.message }
      native.settingsResult(JSON.stringify(result))
      return result
    }
  }
}
