import { BACKUP_STORAGE_KEYS, validateAppSettings } from '../utils/appBackup.js'
import { TICKET_FIELDS, comparableTicketValues } from '../utils/ticketFields.js'
import { validTicket, ticketQuota } from '../utils/ticketValidation.js'
import { normalizeUsername } from '../utils/username.js'

const TICKETS_KEY = 'crticket-device-tickets-v1'
const OWNER_KEY = 'crticket-device-owner-v1'
const keys = [TICKETS_KEY, OWNER_KEY, 'crticket-default-owner:device', 'user', 'login',
  'crticket-theme', 'crticket-generator-receipt', ...BACKUP_STORAGE_KEYS]
const fingerprint = ticket => JSON.stringify(comparableTicketValues(ticket))

export async function restoreDeviceBackup(backup, {
  storage, validateNative, restoreNative, applyTheme, updateUser,
}) {
  const settings = validateAppSettings(backup.settings)
  if (!Array.isArray(backup.tickets) || backup.tickets.length > ticketQuota) {
    throw new Error('一次最多导入 10000 条车票记录')
  }
  if (settings.native) validateNative(settings.native)
  const saved = new Map(keys.map(key => [key, storage.getItem(key)]))
  const oldUser = JSON.parse(saved.get(OWNER_KEY) || '{}')
  const user = {
    ...oldUser,
    id: 'device',
    ...(settings.profile ? {
      username: normalizeUsername(settings.profile.username), avatar: settings.profile.avatar,
    } : {}),
  }
  const tickets = JSON.parse(saved.get(TICKETS_KEY) || '[]')
  const existing = new Set(tickets.map(fingerprint))
  const counts = { imported: 0, skipped: 0, invalid: 0 }
  for (const ticket of backup.tickets) {
    if (!validTicket(ticket) || !ticket.ticket_number || !ticket.train_no
        || !ticket.departure_station || !ticket.arrival_station || !ticket.travel_date
        || !ticket.departure_time || !ticket.seat_type || !ticket.seat_no
        || ticket.price === null || ticket.price === undefined || ticket.price === '') {
      counts.invalid++
      continue
    }
    const key = fingerprint(ticket)
    if (existing.has(key)) {
      counts.skipped++
      continue
    }
    if (tickets.length >= ticketQuota) throw new Error('导入后车票数量不可超过10000条')
    tickets.push({
      ...Object.fromEntries(TICKET_FIELDS.map(field => [field, ticket[field] ?? null])),
      id: crypto.randomUUID(),
      user_id: 'device',
      created_at: typeof ticket.created_at === 'string' && Number.isFinite(Date.parse(ticket.created_at))
        ? ticket.created_at : new Date().toISOString(),
    })
    existing.add(key)
    counts.imported++
  }
  try {
    if (!backup.contents || backup.contents.includes('tickets')) {
      storage.setItem(TICKETS_KEY, JSON.stringify(tickets))
    }
    if (settings.profile) {
      storage.setItem(OWNER_KEY, JSON.stringify(user))
      updateUser(user)
    }
    for (const key of BACKUP_STORAGE_KEYS) {
      if (!Object.hasOwn(settings.storage || {}, key)) continue
      const value = settings.storage[key]
      if (value === null) storage.removeItem(key)
      else storage.setItem(key, value)
    }
    if (Object.hasOwn(settings.native || {}, 'generatorDraft')) {
      if (settings.native.generatorDraft === null) storage.removeItem('crticket-generator-draft')
      else storage.setItem('crticket-generator-draft', JSON.stringify(settings.native.generatorDraft))
    }
    if (Object.hasOwn(settings.native || {}, 'generatorDraft')
        || Object.hasOwn(settings.storage || {}, 'crticket-generator-draft')) {
      storage.removeItem('crticket-generator-receipt')
    }
    if (settings.theme !== undefined) applyTheme(settings.theme)
    // Native restoration validates and commits before replying; runtime effects wait for completion.
    if (settings.native) await restoreNative(settings.native)
    return counts
  } catch (error) {
    const undo = action => {
      try { action() } catch (rollbackError) {
        error.rollbackErrors ||= []
        error.rollbackErrors.push(rollbackError)
      }
    }
    if (settings.theme !== undefined) undo(() => applyTheme(saved.get('crticket-theme') || 'classic-system'))
    if (settings.profile) undo(() => updateUser({ id: 'device', username: '默认用户', avatar: null, ...oldUser }))
    for (const [key, value] of saved) {
      undo(() => {
        if (value === null) storage.removeItem(key)
        else storage.setItem(key, value)
      })
    }
    throw error
  }
}
