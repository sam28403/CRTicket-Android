import { hasAvailableSeat } from '../utils/seatAvailability.js'

export function createBrowserTicketMonitor({
  api,
  state,
  matches,
  watchedColumns,
  hasTicket,
  publishNotification,
  message,
  schedule = setTimeout,
  cancel = clearTimeout,
}) {
  let timer
  let controller
  let generation = 0
  let previous = new Map()

  function stop() {
    state.monitoring.value = false
    cancel(timer)
    state.nextAt.value = ''
    generation++
    controller?.abort()
    state.loading.value = false
  }

  function resetReminders() {
    previous = new Map()
    state.events.value = []
  }

  async function updateReminders(current, signal) {
    const currentTickets = new Map()
    const notices = []
    const details = []
    for (const row of state.rows.value.filter(matches)) {
      if (!hasTicket(row)) continue
      const signature = JSON.stringify(
        watchedColumns.value.map(([key]) => [key, row.seats[key]])
      )
      currentTickets.set(row.id, signature)
      if (!state.monitoring.value || previous.get(row.id) === signature) continue
      const seats = watchedColumns.value
        .filter(([key]) => hasAvailableSeat(row.seats[key]))
        .map(([key, label]) => `${label}：${row.seats[key]}`)
        .join('，')
      const text = `${row.train} ${row.from} → ${row.to}，${seats}`
      state.events.value.unshift({ at: state.queriedAt.value, text })
      notices.push(row.train)
      details.push(text)
    }
    previous = currentTickets
    state.events.value = state.events.value.slice(0, 30)
    if (!notices.length) return

    const summary = notices.slice(0, 3).join('、')
    const more = notices.length > 3 ? ` 等 ${notices.length} 趟车` : ''
    message('success', `${summary}${more} 有票或余票发生变化，详见有票提醒`)
    if (!state.systemNotifications.value) return
    try {
      const result = await publishNotification(notices, details, signal)
      if (current !== generation) return
      if (!result.success) {
        state.systemNotifications.value = false
        message('warning', result.message || '系统通知发送失败，请查看页面内的有票提醒')
      }
    } catch {
      if (current === generation) {
        message('warning', '系统通知发送失败，请查看页面内的有票提醒')
      }
    }
  }

  async function query(params, startMonitor = false) {
    cancel(timer)
    const current = ++generation
    controller = new AbortController()
    state.loading.value = true
    state.error.value = ''
    state.nextAt.value = ''
    if (startMonitor) {
      state.monitoring.value = true
      resetReminders()
    }
    try {
      const { data } = await api.get('/left-ticket', {
        params,
        timeout: 25000,
        signal: controller.signal,
      })
      if (current !== generation) return
      if (!data.success || !Array.isArray(data.trains)) {
        throw new Error('12306 返回的数据格式错误')
      }
      state.rows.value = data.trains
      state.queriedAt.value = data.queriedAt
      if (state.monitoring.value && state.lastMonitorAt) state.lastMonitorAt.value = data.queriedAt
      await updateReminders(current, controller.signal)
    } catch (error) {
      if (current !== generation) return
      state.error.value = error.response?.data?.message ||
        (error.code === 'ECONNABORTED'
          ? '查询超时，请检查手机网络'
          : error.message || '查询失败')
    } finally {
      if (current === generation) {
        state.loading.value = false
        if (state.monitoring.value) {
          const delay = Math.max(10, Number(state.interval.value) || 60) * 1000
          state.nextAt.value = new Date(Date.now() + delay).toISOString()
          timer = schedule(() => query(params), delay)
        }
      }
    }
  }

  return { query, stop, resetReminders }
}
