import { buildAppBackup, parseAppBackup, BACKUP_STORAGE_KEYS } from '../utils/appBackup.js'
import { importTicketBackup } from '../utils/ticketBackup.js'
import { restoreDeviceBackup } from './deviceBackup.js'
import { createNativeCalls } from './nativeCalls.js'
import { native } from './platform.js'
import { useTheme } from '../composables/useTheme.js'
import { useUserStore } from '../stores/user.js'
import { selectBackupContents } from '../utils/backupContents.js'

const calls = createNativeCalls('crticketBackupResult')

export function exportSettingsBackup(records, user, contents) {
  return buildAppBackup(records, user, {
    theme: useTheme().preference.value,
    storage: localStorage,
    contents,
    native: native?.backupSettings ? JSON.parse(native.backupSettings()) : undefined,
  })
}

export async function importSettingsBackup(text, records, user, api, messages, contents) {
  const backup = selectBackupContents(parseAppBackup(text, messages), contents)
  const restoredEvent = () => new CustomEvent('crticket-backup-restored', { detail: {
    generatorDraftRestored: Object.hasOwn(backup.settings?.native || {}, 'generatorDraft')
      || Object.hasOwn(backup.settings?.storage || {}, 'crticket-generator-draft'),
  } })
  if (!backup.settings) return importTicketBackup(backup.tickets, records, user, api, messages)
  if (native) {
    const draft = backup.settings.storage?.['crticket-generator-draft']
    if (Object.hasOwn(backup.settings.storage || {}, 'crticket-generator-draft')
        && !Object.hasOwn(backup.settings.native || {}, 'generatorDraft')) {
      backup.settings.native = { ...backup.settings.native, generatorDraft: draft === null ? null : JSON.parse(draft) }
    }
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
    window.dispatchEvent(restoredEvent())
    return messages.complete(counts.imported, counts.skipped, counts.invalid)
  }
  // Keep the existing browser/server import route; device-owned records never enter a server account.
  const message = await importTicketBackup(backup.tickets, records, user, api, messages)
  const { profile, theme } = backup.settings
  const storage = { ...backup.settings.storage }
  if (Object.hasOwn(backup.settings.native || {}, 'generatorDraft')) {
    const draft = backup.settings.native.generatorDraft
    storage['crticket-generator-draft'] = draft === null ? null : JSON.stringify(draft)
  }
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
  if (Object.hasOwn(storage, 'crticket-generator-draft')) localStorage.removeItem('crticket-generator-receipt')
  if (theme !== undefined) useTheme().preference.value = theme
  window.dispatchEvent(restoredEvent())
  return message
}
