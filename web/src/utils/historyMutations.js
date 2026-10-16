import api from '@/api.js'
import { buildTicketPayload, disableAirSeats } from '@/utils/ticketShared.js'

import { validateHistoryTicket } from './historyValidation.js'

export async function saveHistoryTicket(detail, owner) {
  const ticket = Object.fromEntries(
    Object.entries(detail.ticket || {}).map(([key, value]) => [key, String(value ?? '').trim()]),
  )
  const error = validateHistoryTicket(ticket)
  if (error) return { success: false, message: error }
  if (!owner?.id) return { success: false, message: '无法读取默认用户资料' }
  const hasConditioner = Number(ticket.hasConditioner) === 1 && !disableAirSeats.includes(ticket.seatType)
  const payload = buildTicketPayload({ ...ticket, trainNo: ticket.trainNo.toUpperCase(), price: Number(ticket.price) }, {
    userId: owner.id,
    useCredit: Number(ticket.useCredit) === 1,
    hasConditioner,
    finalSeatType: hasConditioner ? `新空调${ticket.seatType}` : ticket.seatType,
    distance: ticket.distance,
  })
  const path = detail.id == null ? '/ticket/add' : `/ticket/update/${encodeURIComponent(detail.id)}`
  const result = await api.post(path, payload)
  return { success: Boolean(result.data?.success), message: result.data?.message || '保存失败，请重试' }
}

export async function removeHistoryTicket(id) {
  const result = await api.delete(`/ticket/delete/${encodeURIComponent(id)}`)
  return { success: Boolean(result.data?.success), message: result.data?.message || '删除失败，请重试' }
}
