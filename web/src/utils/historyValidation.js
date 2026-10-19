import { getTrainNumberError } from './ticketShared.js'

export function validateHistoryTicket(ticket) {
  const required = ['number', 'trainNo', 'from', 'to', 'date', 'time', 'seatType', 'seatNo']
  if (required.some(key => !String(ticket[key] ?? '').trim())) return '请填写完整车票信息'
  const trainError = getTrainNumberError(ticket.trainNo)
  if (trainError) return trainError
  if (ticket.price === '' || !Number.isFinite(Number(ticket.price)) || Number(ticket.price) < 0) {
    return '请输入有效的票价或积分'
  }
  if (ticket.distance !== '' && (!Number.isFinite(Number(ticket.distance)) || Number(ticket.distance) < 0)) {
    return '请输入有效的里程'
  }
  const parts = String(ticket.date).match(/^(\d{4})(?:年|-|\/)(\d{1,2})(?:月|-|\/)(\d{1,2})日?$/)
  if (!parts) return '请选择有效的开车日期'
  const date = new Date(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3]))
  if (date.getFullYear() !== Number(parts[1]) || date.getMonth() + 1 !== Number(parts[2]) ||
      date.getDate() !== Number(parts[3])) return '请选择有效的开车日期'
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(ticket.time)) return '请选择有效的开车时间'
  return ''
}
