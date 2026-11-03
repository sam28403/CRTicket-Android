import { queryTicketSample } from './railway/ticketSamples.js'
import { buildDebugTicket, randomChoice } from '../utils/debugTickets.js'
import { buildTicketPayload } from '../utils/ticketShared.js'
import { trainQueryRange, isTrainQueryDateInRange } from '../utils/trainQueryDate.js'
import { validListDate } from './railway/lltList.js'
import { createLocalApi } from './localApi.js'

export async function generateDebugTickets(date, count, { sample = queryTicketSample, api } = {}) {
  if (!Number.isInteger(count) || count < 1 || count > 1000) throw new Error('生成数量无效')
  if (!validListDate(date) || !isTrainQueryDateInRange(date) || date >= trainQueryRange().max) {
    throw new Error('始发日期无效')
  }
  const pool = []
  const target = Math.min(count, 5)
  for (let attempt = 0; attempt < target * 3 && pool.length < target; attempt++) {
    try {
      const result = await sample(date)
      if (result.sample) pool.push(result.sample)
    } catch (error) {
      if (attempt === target * 3 - 1 && !pool.length) throw error
    }
  }
  if (!pool.length) throw new Error('未取得完整车票数据')
  const tickets = Array.from({ length: count }, (_, index) => {
    const ticket = buildDebugTicket(pool[index] || randomChoice(pool))
    return buildTicketPayload(ticket, {
      userId: 'device', finalSeatType: ticket.finalSeatType, distance: ticket.distance,
    })
  })
  return api.request('POST', '/ticket/add-batch', { tickets })
}

export function initializeDebugGeneration() {
  let busy = false
  window.crticketDebugGenerate = (operation, date, count) => {
    if (busy) return
    busy = true
    window.crticketDebugResult = null
    generateDebugTickets(date, count, { api: createLocalApi(localStorage) })
      .then(result => { window.crticketDebugResult = { operation, ...result } })
      .catch(() => { window.crticketDebugResult = { operation, success: false } })
      .finally(() => { busy = false })
  }
}
