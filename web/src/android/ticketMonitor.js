import { createNativeCalls, supportsNativeMethods } from './nativeCalls.js'

const calls = createNativeCalls('crticketTicketMonitorResult')

export function supportsTicketMonitoring() {
  return supportsNativeMethods([
    'startTicketMonitor',
    'ticketMonitorStatus',
    'stopTicketMonitor',
    'updateTicketMonitor',
  ])
}

function callMonitor(method, payload, signal) {
  return calls.call(id => {
    const bridge = globalThis.window?.CRTicketAndroid
    if (typeof bridge?.[method] !== 'function') {
      throw new Error('当前应用不支持后台余票监控，请更新应用')
    }
    if (payload === undefined) bridge[method](id)
    else bridge[method](id, JSON.stringify(payload))
  }, {
    signal,
    timeout: 15000,
    timeoutError: () => new Error('读取后台余票监控状态超时，请稍后重试'),
    mapResult: result => {
      if (!result || result.success === false) {
        throw new Error(result?.message || result?.error || '后台余票监控操作失败')
      }
      return result
    },
  })
}

export const startTicketMonitor = (payload, signal) =>
  callMonitor('startTicketMonitor', payload, signal)

export const getTicketMonitorStatus = signal =>
  callMonitor('ticketMonitorStatus', undefined, signal)

export const stopTicketMonitor = signal =>
  callMonitor('stopTicketMonitor', undefined, signal)

export const updateTicketMonitor = (payload, signal) =>
  callMonitor('updateTicketMonitor', payload, signal)
