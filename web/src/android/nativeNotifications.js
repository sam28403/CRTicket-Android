import { createNativeCalls } from './nativeCalls.js'

const notifications = createNativeCalls('crticketNotificationResult')

export function callNativeNotification(method, args, signal, timeout = 0) {
  return notifications.call(
    id => window.CRTicketAndroid[method](id, ...args),
    {
      signal,
      timeout,
      timeoutError: () => new Error('系统通知操作超时，请重试'),
    },
  )
}
