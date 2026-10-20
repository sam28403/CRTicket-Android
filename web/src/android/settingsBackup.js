import { buildAppBackup, parseAppBackup, BACKUP_STORAGE_KEYS } from '../utils/appBackup.js'
import { importTicketBackup } from '../utils/ticketBackup.js'
import { restoreDeviceBackup } from './deviceBackup.js'
import { createNativeCalls } from './nativeCalls.js'
import { native } from './platform.js'
import { useTheme } from '../composables/useTheme.js'
import { useUserStore } from '../stores/user.js'

const calls = createNativeCalls('crticketBackupResult')

export function exportSettingsBackup(records, user) {
  return buildAppBackup(records, user, {
    theme: useTheme().preference.value,
    storage: localStorage,
    native: native?.backupSettings ? JSON.parse(native.backupSettings()) : undefined,
  })
}

export async function importSettingsBackup(text, records, user, api, messages) {
  const backup = parseAppBackup(text, messages)
  if (!backup.settings) return importTicketBackup(backup.tickets, records, user, api, messages)
  if (native) {
    const counts = await restoreDeviceBackup(backup, {
      storage: localStorage,
      validateNative(settings) {
        if (!native.validateBackupSettings) throw new Error('此软件版本不支持完整备份恢复')
        const error = native.validateBackupSettings(JSON.stringify(settings))
        if (error) throw new Error(error)
      },
      restoreNative: settings => calls.call(id => {
        native.restoreBackupSettings(id, JSON.stringify(settings))
      }, {
        // This local commit runs on the UI thread; do not time out a committed operation.
        mapResult(result) {
          if (!result.success) throw new Error(result.message || messages.failed)
        },
      }),
      applyTheme: value => { useTheme().preference.value = value },
      updateUser: value => useUserStore().setUser(value),
    })
    window.dispatchEvent(new Event('crticket-backup-restored'))
    return messages.complete(counts.imported, counts.skipped, counts.invalid)
  }
  // Keep the existing browser/server import route; device-owned records never enter a server account.
  const message = await importTicketBackup(backup.tickets, records, user, api, messages)
  const { profile, theme, storage } = backup.settings
  if (profile) {
    for (const [action, payload] of [
      ['name', { username: profile.username }], ['avatar', { avatar: profile.avatar }],
    ]) {
      const { data } = await api.post(`/user/update-${action}`, payload)
      if (!data.success) throw new Error(data.message || messages.failed)
      useUserStore().setUser(data.user)
    }
  }
  for (const key of BACKUP_STORAGE_KEYS) {
    if (!Object.hasOwn(storage || {}, key)) continue
    if (storage[key] === null) localStorage.removeItem(key)
    else localStorage.setItem(key, storage[key])
  }
  if (theme !== undefined) useTheme().preference.value = theme
  window.dispatchEvent(new Event('crticket-backup-restored'))
  return message
}
