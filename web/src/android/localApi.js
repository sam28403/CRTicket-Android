import { TICKET_FIELDS, comparableTicketValues } from '../utils/ticketFields.js'
import { validTicket, ticketQuota } from '../utils/ticketValidation.js'
import { normalizeUsername } from '../utils/username.js'

const KEY = 'crticket-device-tickets-v1'
const OWNER_KEY = 'crticket-device-owner-v1'

// Keep device records separate from the server account and its login cache.
export function createLocalApi(storage) {
  const owner = () => {
    const saved = JSON.parse(storage.getItem(OWNER_KEY) || '{}')
    return {
      id: 'device',
      username: saved.username || '默认用户',
      avatar: saved.avatar || null,
    }
  }
  const read = () => JSON.parse(storage.getItem(KEY) || '[]')
  const write = tickets => storage.setItem(KEY, JSON.stringify(tickets))
  const success = (message = '保存成功') => ({ success: true, message })
  const check = ticket => {
    if (!validTicket(ticket)) throw new Error('车票字段格式错误或过长')
  }
  const record = (ticket, existing) => ({
    ...Object.fromEntries(TICKET_FIELDS.map(field => [field, ticket[field] ?? null])),
    id: existing?.id ?? crypto.randomUUID(), user_id: 'device',
    created_at: existing?.created_at ?? new Date().toISOString(),
  })
  const fingerprint = ticket => JSON.stringify(comparableTicketValues(ticket))

  return { owner, async request(method, rawPath, body = {}) {
    const url = new URL(rawPath, 'https://local.invalid')
    const path = url.pathname
    if (method === 'GET' && path === '/user/session') return { success: true, user: owner() }
    if (method === 'POST' && path === '/user/logout') return success('本机模式无需登录')
    if (method === 'POST' && path === '/user/update-name') {
      const user = { ...owner(), username: normalizeUsername(body.username) }
      storage.setItem(OWNER_KEY, JSON.stringify(user))
      return { ...success('用户名已保存'), user }
    }
    if (method === 'POST' && path === '/user/update-avatar') {
      if (body.avatar != null && (typeof body.avatar !== 'string' || !body.avatar.startsWith('data:image/') || body.avatar.length > 400000)) throw new Error('头像数据无效')
      const user = { ...owner(), avatar: body.avatar || null }
      storage.setItem(OWNER_KEY, JSON.stringify(user))
      return { ...success('头像已保存到本机'), user }
    }
    const tickets = read()
    if (method === 'GET' && path === '/ticket/list/device') return [...tickets].reverse()
    if (method === 'POST' && path === '/ticket/add') {
      check(body)
      if (tickets.length >= ticketQuota) throw new Error('车票数量已达10000条上限')
      write([...tickets, record(body)])
      return success('车票已保存到本机')
    }
    const mutation = path.match(/^\/ticket\/(update|delete)\/([^/]+)$/)
    if (mutation) {
      const index = tickets.findIndex(ticket => ticket.id === mutation[2])
      if (index < 0) throw new Error('车票记录不存在')
      if (mutation[1] === 'delete' && method === 'DELETE') {
        write(tickets.filter((_, i) => i !== index))
        return success('记录已删除')
      }
      if (mutation[1] === 'update' && ['POST', 'PUT'].includes(method)) {
        check(body)
        tickets[index] = record(body, tickets[index])
        write(tickets)
        return success('车票已更新')
      }
    }
    if (method === 'GET' && path === '/user/statistics') {
      const counts = field => {
        const result = new Map()
        for (const ticket of tickets) if (ticket[field]) result.set(ticket[field], (result.get(ticket[field]) || 0) + 1)
        return [...result].map(([station, count]) => ({ station, count })).sort((a, b) => b.count - a.count)
      }
      return { success: true, data: { departures: counts('departure_station'), arrivals: counts('arrival_station') } }
    }
    if (method === 'GET' && path === '/user/ticket-history') return { success: true, data: [...tickets]
      .sort((a, b) => String(b.travel_date).localeCompare(String(a.travel_date))).slice(0, 500)
      .map(t => ({ id: t.id, departureStation: t.departure_station, arrivalStation: t.arrival_station, travelDate: t.travel_date, trainNumber: t.train_no })) }
    if (method === 'GET' && path === '/user/monthly-tickets') {
      const year = Number(url.searchParams.get('year')) || new Date().getFullYear()
      const month = Number(url.searchParams.get('month')) || new Date().getMonth() + 1
      const prefix = `${year}年${String(month).padStart(2, '0')}月`
      const counts = new Map()
      tickets.filter(t => String(t.travel_date).startsWith(prefix)).forEach(t => counts.set(t.travel_date, (counts.get(t.travel_date) || 0) + 1))
      return { success: true, data: { year, month, tickets: [...counts].map(([travel_date, count]) => ({ travel_date, count })) } }
    }
    if (method === 'POST' && path === '/user/import-backup') {
      const backup = typeof body.backup === 'string' ? JSON.parse(body.backup) : body.backup
      const source = Array.isArray(backup) ? backup : backup?.tickets
      if (!Array.isArray(source) || source.length > 1000) throw new Error('未找到可导入的车票数据，或超过单次1000条限制')
      const next = [...tickets], existing = new Set(tickets.map(fingerprint))
      let imported = 0, skipped = 0, invalid = 0
      for (const ticket of source) {
        if (!validTicket(ticket)) { invalid++; continue }
        const key = fingerprint(ticket)
        if (existing.has(key)) { skipped++; continue }
        if (next.length >= ticketQuota) throw new Error('导入后车票数量不可超过10000条')
        next.push(record(ticket)); existing.add(key); imported++
      }
      write(next)
      return { ...success('备份已导入本机'), data: { imported, skipped, invalid, total: source.length } }
    }
    throw new Error('未知的本机数据接口')
  } }
}
