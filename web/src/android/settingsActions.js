import api from '@/api.js'
import { useUserStore } from '@/stores/user.js'
import { normalizeUsername } from '@/utils/username.js'
import { useTheme } from '@/composables/useTheme.js'
import { appearancePreference } from '@/composables/themePreference.js'
import {
  buildBackupPayload, buildExcelRows, getExportFileStamp,
  parseTicketBackup, importTicketBackup,
} from '@/utils/ticketBackup.js'
import { saveFile, native } from './platform.js'
import {
  supportsStationArrival, getStationArrivalState,
  enableStationArrival, disableStationArrival,
} from './stationArrival.js'

async function owner() {
  const { data } = await api.get('/user/session')
  if (!data.success) throw new Error(data.message || '无法读取本机资料')
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
    : { enabled: false, active: false, supported: false, message: '此设备暂不支持到站检测' }
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
    if (!data.success) throw new Error(data.message || '保存失败')
    useUserStore().setUser(data.user)
    return data.message || '已保存'
  }
  if (action === 'arrival') {
    const state = await (payload.enabled ? enableStationArrival() : disableStationArrival())
    if (state.success === false) throw new Error(state.message || '未能更改到站检测')
    return state.message || '已更新到站检测'
  }
  const user = await owner()
  const records = await tickets(user)
  if (action === 'import') {
    return importTicketBackup(parseTicketBackup(payload.text), records, user, api)
  }
  const stamp = getExportFileStamp()
  let blob
  let filename
  if (action === 'exportBackup') {
    blob = new Blob([JSON.stringify(buildBackupPayload(records, user), null, 2)], {
      type: 'application/json;charset=utf-8',
    })
    filename = `CRTicket-backup-${stamp}.json`
  } else if (action === 'exportExcel') {
    if (!records.length) throw new Error('暂无可导出的运转数据')
    const XLSX = await import('xlsx')
    const sheet = XLSX.utils.json_to_sheet(buildExcelRows(records))
    sheet['!cols'] = Array.from({ length: 17 }, () => ({ wch: 18 }))
    const book = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(book, sheet, '运转数据')
    blob = new Blob([XLSX.write(book, { bookType: 'xlsx', type: 'array' })], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    filename = `CRTicket-运转数据-${stamp}.xlsx`
  } else {
    throw new Error('未知设置操作')
  }
  return await saveFile(blob, filename) ? '文件已保存' : '已取消保存'
}

export function initializeNativeSettings() {
  if (typeof native?.settingsResult !== 'function') return
  window.crticketSettings = async (id, action, payload) => {
    let message = ''
    let success = true
    try {
      message = await performSettingsAction(action, payload)
    } catch (error) {
      message = error.response?.data?.message || error.message || '操作失败，请重试'
      success = false
    }
    try {
      const state = await settingsSnapshot()
      const result = { id, success, message, state }
      native.settingsResult(JSON.stringify(result))
      return result
    } catch (error) {
      const result = { id, success: false, message: message || error.message }
      native.settingsResult(JSON.stringify(result))
      return result
    }
  }
}
