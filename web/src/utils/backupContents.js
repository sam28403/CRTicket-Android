export const BACKUP_CONTENTS = [
  { id: 'tickets', label: '车票历史记录' },
  { id: 'search', label: '搜索记录' },
  { id: 'settings', label: '设置项' },
  { id: 'profile', label: '个人资料' },
  { id: 'visits', label: '到访车站记录' },
  { id: 'speed', label: '测速记录与轨迹' },
  { id: 'draft', label: '车票生成草稿' },
  { id: 'monitor', label: '余票监控配置' },
]

const storageGroups = {
  search: ['crticket-train-query-history', 'crticket-station-query-history'],
  settings: ['crticket-important-notice-hidden', 'mapSource'],
  draft: ['crticket-generator-draft'],
}
const nativeGroups = {
  search: ['vehicleSearchHistory', 'saleSearchHistory'],
  settings: ['language', 'arrivalEnabled', 'importantNoticeSuppressed', 'railfanToolsEnabled',
    'saleAlarmEnabled', 'saleSameCity', 'mapSource'],
  visits: ['stationVisits'],
  speed: ['speedRecordings'],
  draft: ['generatorDraft'],
  monitor: ['monitorConfig'],
}
const has = (value, key) => Object.hasOwn(value || {}, key)
const pick = (value, keys) => Object.fromEntries(
  keys.filter(key => has(value, key)).map(key => [key, value[key]]),
)

export function availableBackupContents(backup) {
  const settings = backup.settings || {}
  return BACKUP_CONTENTS.map(item => item.id).filter(id => {
    if (backup.contents && !backup.contents.includes(id)) return false
    if (id === 'tickets') return Array.isArray(backup.tickets)
    if (id === 'profile') return has(settings, 'profile')
    if (id === 'settings' && has(settings, 'theme')) return true
    return (storageGroups[id] || []).some(key => has(settings.storage, key))
      || (nativeGroups[id] || []).some(key => has(settings.native, key))
  })
}

export function selectBackupContents(backup, selection = availableBackupContents(backup)) {
  if (!Array.isArray(selection) || !selection.length
      || selection.some(id => !BACKUP_CONTENTS.some(item => item.id === id))) {
    throw new Error('请选择备份内容')
  }
  const selected = availableBackupContents(backup).filter(id => selection.includes(id))
  if (!selected.length) throw new Error('备份中没有所选内容')
  const source = backup.settings || {}
  const settings = {}
  if (selected.includes('profile') && has(source, 'profile')) settings.profile = source.profile
  if (selected.includes('settings') && has(source, 'theme')) settings.theme = source.theme
  for (const [field, groups] of [['storage', storageGroups], ['native', nativeGroups]]) {
    const values = pick(source[field], selected.flatMap(id => groups[id] || []))
    if (Object.keys(values).length) settings[field] = values
  }
  const result = {
    ...backup,
    contents: selected,
    tickets: selected.includes('tickets') ? backup.tickets : [],
    settings: backup.settings ? settings : null,
  }
  // The legacy envelope also contains a username; do not leak an excluded profile.
  if (has(result, 'user') && !selected.includes('profile')) result.user = null
  return result
}
