import { buildBackupPayload, parseTicketBackup } from './ticketBackup.js'
import { normalizeUsername } from './username.js'
import { TICKET_FIELDS } from './ticketFields.js'
import { validSpeedRecordings, validGeneratorDraft } from './appBackupSpeedometer.js'
import { validRailfanBackup } from './appBackupRailfan.js'

export const BACKUP_STORAGE_KEYS = [
  'crticket-important-notice-hidden',
  'crticket-train-query-history',
  'crticket-station-query-history',
  'crticket-generator-draft',
]

const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)
const invalid = () => { throw new Error('备份设置格式无效') }

export function validateAppSettings(settings) {
  if (!object(settings)) invalid()
  const { profile, theme, storage, native } = settings
  if (profile !== undefined) {
    if (!object(profile)) invalid()
    normalizeUsername(profile.username)
    if (profile.avatar !== null && (typeof profile.avatar !== 'string'
        || !profile.avatar.startsWith('data:image/')
        || profile.avatar.length > 400000)) invalid()
  }
  if (theme !== undefined && (typeof theme !== 'string'
      || !/^(classic|contrast)-(system|light|dark)$/.test(theme))) invalid()
  if (storage !== undefined) {
    if (!object(storage)) invalid()
    for (const key of BACKUP_STORAGE_KEYS) {
      const value = storage[key]
      if (value === undefined || value === null) continue
      if (typeof value !== 'string' || value.length > 20000) invalid()
      if (key === 'crticket-generator-draft') {
        if (!validGeneratorDraft(JSON.parse(value))) invalid()
      } else if (key === 'crticket-important-notice-hidden') {
        if (value !== '1') invalid()
      } else {
        const history = JSON.parse(value)
        if (!Array.isArray(history) || history.length > 100
            || history.some(item => typeof item !== 'string' || item.length > 100)) invalid()
      }
    }
  }
  if (native !== undefined) {
    if (!object(native)) invalid()
    if (!validRailfanBackup(native)) invalid()
    if (native.speedRecordings !== undefined && !validSpeedRecordings(native.speedRecordings)) invalid()
    if (native.generatorDraft !== undefined && native.generatorDraft !== null
        && !validGeneratorDraft(native.generatorDraft)) invalid()
    if (native.language !== undefined && (typeof native.language !== 'string'
        || native.language.length > 35)) invalid()
    for (const key of ['arrivalEnabled', 'importantNoticeSuppressed']) {
      if (native[key] !== undefined && typeof native[key] !== 'boolean') invalid()
    }
    if (native.monitorConfig !== undefined && native.monitorConfig !== null
        && !object(native.monitorConfig)) invalid()
  }
  return settings
}

export function buildAppBackup(tickets, user, { theme, storage, native } = {}) {
  const payload = buildBackupPayload([], user)
  const settings = {
    profile: { username: user.username, avatar: user.avatar || null },
    theme,
    storage: Object.fromEntries(BACKUP_STORAGE_KEYS.map(key => [key, storage.getItem(key)])),
    ...(native ? { native } : {}),
  }
  if (native && Object.hasOwn(native, 'generatorDraft')) {
    settings.storage['crticket-generator-draft'] = native.generatorDraft === null
      ? null : JSON.stringify(native.generatorDraft)
  }
  validateAppSettings(settings)
  return {
    ...payload,
    schemaVersion: 2,
    settings,
    tickets: tickets.map(ticket => ({
      ...Object.fromEntries(TICKET_FIELDS.map(field => [field, ticket[field] ?? null])),
      ...(ticket.created_at ? { created_at: ticket.created_at } : {}),
    })),
  }
}

export function parseAppBackup(text, messages = {}) {
  const value = JSON.parse(text)
  const tickets = parseTicketBackup(text, messages)
  if (Array.isArray(value) || value.schemaVersion === undefined || value.schemaVersion === 1) {
    return { tickets, settings: null }
  }
  if (value.app !== 'CRTicket' || value.schemaVersion !== 2) {
    throw new Error('不支持此备份版本')
  }
  return { tickets, settings: validateAppSettings(value.settings) }
}
