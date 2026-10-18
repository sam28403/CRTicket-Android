import api from '@/api.js'
import { buildTicketPayload, disableAirSeats } from './ticketShared.js'
import { comparableTicketValues } from './ticketFields.js'

export async function storeGeneratedTicket(value, owner) {
  const ticket = Object.fromEntries(
    Object.entries(value).map(([key, field]) => [key, String(field ?? '').trim()]),
  )
  const hasConditioner = Number(ticket.hasConditioner) === 1 && !disableAirSeats.includes(ticket.seatType)
  const payload = buildTicketPayload({ ...ticket, trainNo: ticket.trainNo.toUpperCase(), price: Number(ticket.price) }, {
    userId: owner.id,
    useCredit: Number(ticket.useCredit) === 1,
    hasConditioner,
    finalSeatType: hasConditioner ? `新空调${ticket.seatType}` : ticket.seatType,
    distance: ticket.distance,
  })
  const response = await api.get(`/ticket/list/${encodeURIComponent(owner.id)}`)
  const rows = Array.isArray(response.data) ? response.data : response.data?.data
  if (!Array.isArray(rows)) throw new Error('无法读取账户车票，请重试')
  const fingerprint = JSON.stringify(comparableTicketValues(payload))
  if (rows.some(row => JSON.stringify(comparableTicketValues(row)) === fingerprint)) {
    return { success: true }
  }
  const result = await api.post('/ticket/add', payload)
  return { success: Boolean(result.data?.success), message: result.data?.message || '保存失败，请重试' }
}
