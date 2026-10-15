import { buildTicketPayload } from './ticketShared.js'
import { comparableTicketValues } from './ticketFields.js'

const buildBackupTicket = (ticket) => ({
  ticket_number: ticket.ticket_number ?? "",
  train_no: ticket.train_no ?? "",
  departure_station: ticket.departure_station ?? "",
  arrival_station: ticket.arrival_station ?? "",
  travel_date: ticket.travel_date ?? "",
  departure_time: ticket.departure_time ?? "",
  price: ticket.price ?? "",
  use_credit: ticket.use_credit ?? 0,
  seat_type: ticket.seat_type ?? "",
  has_conditioner: ticket.has_conditioner ?? 0,
  seat_no: ticket.seat_no ?? "",
  sell_place: ticket.sell_place ?? "",
  gate_info: ticket.gate_info ?? "",
  message: ticket.message ?? "",
  theme: ticket.theme ?? "",
  distance: ticket.distance ?? 0,
})

const buildTicketFingerprint = (ticket) => comparableTicketValues(ticket).join("||")

const mapBackupTicketToTicketForm = (ticket) => ({
  number: ticket.ticket_number ?? "",
  trainNo: ticket.train_no ?? "",
  from: ticket.departure_station ?? "",
  to: ticket.arrival_station ?? "",
  date: ticket.travel_date ?? "",
  time: ticket.departure_time ?? "",
  price: ticket.price ?? "",
  seatType: ticket.seat_type ?? "",
  seatNo: ticket.seat_no ?? "",
  sellPlace: ticket.sell_place ?? "",
  gate: ticket.gate_info ?? "",
  message: ticket.message ?? "",
  theme: ticket.theme ?? "",
  distance: ticket.distance ?? 0,
})

export const buildBackupPayload = (tickets, user) => {
  return {
    schemaVersion: 1,
    exportedAt: new Date().toISOString(),
    app: "CRTicket",
    user: user ? {
      id: user.id,
      username: user.username,
    } : null,
    tickets: tickets.map(buildBackupTicket),
  }
}

export const buildExcelRows = (tickets) => {
  return tickets.map((ticket) => ({
    "票号/订单号": ticket.ticket_number ?? "",
    "车次": ticket.train_no ?? "",
    "出发站": ticket.departure_station ?? "",
    "到达站": ticket.arrival_station ?? "",
    "乘车日期": ticket.travel_date ?? "",
    "开车时间": ticket.departure_time ?? "",
    "票价": ticket.price ?? "",
    "使用积分": Number(ticket.use_credit) === 1 ? "是" : "否",
    "席位": ticket.seat_type ?? "",
    "空调": Number(ticket.has_conditioner) === 1 ? "有" : "无",
    "座位号": ticket.seat_no ?? "",
    "售票地点": ticket.sell_place ?? "",
    "检票/候车位置": ticket.gate_info ?? "",
    "提示语": ticket.message ?? "",
    "主题": ticket.theme ?? "",
    "里程": ticket.distance ?? "",
    "创建时间": ticket.created_at ?? "",
  }))
}

export const getExportFileStamp = () => {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, "0")
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
    + `-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
}


export function parseTicketBackup(text, messages = {}) {
  const parsed = JSON.parse(text)
  const tickets = Array.isArray(parsed) ? parsed : parsed?.tickets
  if (!Array.isArray(tickets)) throw new Error(messages.noTickets || '备份文件中没有找到车票数据')
  if (tickets.length > 10000) throw new Error(messages.limit || '一次最多导入 10000 条车票记录')
  return tickets
}

export async function importTicketBackup(tickets, currentTickets, user, api, messages = {}) {
  const existing = new Set(currentTickets.map(buildTicketFingerprint))
  const pending = []
  let imported = 0
  let skipped = 0
  let invalid = 0
  for (const raw of tickets) {
    const mapped = raw && typeof raw === 'object' ? mapBackupTicketToTicketForm(raw) : null
    if (!mapped || !mapped.number || !mapped.trainNo || !mapped.from || !mapped.to
        || !mapped.date || !mapped.time || !mapped.seatType || !mapped.seatNo || !mapped.price) {
      invalid++
      continue
    }
    const payload = buildTicketPayload(mapped, {
      userId: user.id,
      useCredit: Number(raw.use_credit) === 1,
      finalSeatType: raw.seat_type ?? mapped.seatType,
      hasConditioner: Number(raw.has_conditioner) === 1,
      distance: raw.distance ?? mapped.distance ?? 0,
    })
    const key = buildTicketFingerprint(payload)
    if (existing.has(key)) {
      skipped++
      continue
    }
    pending.push(payload)
    existing.add(key)
  }
  for (let offset = 0; offset < pending.length; offset += 100) {
    const { data } = await api.post('/user/import-backup', { backup: pending.slice(offset, offset + 100) })
    if (!data.success) throw new Error(data.message || messages.failed || '导入失败')
    imported += data.data.imported
    skipped += data.data.skipped
    invalid += data.data.invalid
  }
  return messages.complete?.(imported, skipped, invalid)
    ?? `导入完成：新增 ${imported} 条，略过 ${skipped} 条，未处理 ${invalid} 条`
}
