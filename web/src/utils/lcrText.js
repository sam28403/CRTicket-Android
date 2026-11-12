import { configuration } from '../android/platform.js'

const simplified = {
  offlineSource: '中老铁路（离线）',
  invalidTimetable: '中老铁路时刻表格式异常',
  downloadFailed: '中老铁路时刻表下载失败',
  local: '当地时间',
  beijing: '北京时间',
  vientiane: '万象时间',
}

const traditional = {
  offlineSource: '中寮鐵路（離線）',
  invalidTimetable: '中寮鐵路時刻表格式異常',
  downloadFailed: '中寮鐵路時刻表下載失敗',
  local: '當地時間',
  beijing: '北京時間',
  vientiane: '永珍時間',
}

const english = {
  offlineSource: 'Laos-China Railway (offline)',
  invalidTimetable: 'Invalid Laos-China Railway timetable format',
  downloadFailed: 'Could not download the Laos-China Railway timetable',
  local: 'Local time',
  beijing: 'Beijing time',
  vientiane: 'Vientiane time',
}

export function getLcrText(locale) {
  const [language, ...subtags] = locale.toLowerCase().split('-')
  if (language !== 'zh') return english
  const [scriptOrRegion] = subtags
  return ['hant', 'tw', 'hk', 'mo'].includes(scriptOrRegion) ? traditional : simplified
}

export const lcrText = getLcrText(configuration.locale || globalThis.window?.navigator?.language || 'zh-CN')
