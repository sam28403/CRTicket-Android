import { callNativeNotification } from './nativeNotifications.js'

export { callNativeNotification } from './nativeNotifications.js'

export async function requestMonitorNotificationPermission(signal) {
  const native = globalThis.window?.CRTicketAndroid
  if (native) {
    if (!native.requestNotificationPermission) {
      return { granted: false, message: '请更新应用以启用系统通知，将继续使用页面内提醒' }
    }
    try {
      return await callNativeNotification('requestNotificationPermission', [], signal)
    } catch (error) {
      if (error.code === 'ERR_CANCELED') throw error
      return { granted: false, message: '无法申请系统通知权限，将继续使用页面内提醒' }
    }
  }

  const Notification = globalThis.window?.Notification
  if (!Notification) {
    return { granted: false, message: '当前浏览器不支持系统通知，将继续使用页面内提醒' }
  }
  if (Notification.permission === 'granted') return { granted: true }
  if (Notification.permission === 'denied') {
    return { granted: false, message: '浏览器通知已被禁用，请在网站权限设置中手动开启' }
  }
  try {
    const granted = (await Notification.requestPermission()) === 'granted'
    return {
      granted,
      message: granted ? '系统通知已开启' : '未获得通知权限，将继续使用页面内提醒',
    }
  } catch {
    return { granted: false, message: '无法申请浏览器通知权限，将继续使用页面内提醒' }
  }
}

export async function publishMonitorNotification(notices, details, signal) {
  const trains = Array.from(new Set(notices))
  const summary = trains.slice(0, 3).join('、')
  const more = trains.length > 3 ? ` 等 ${trains.length} 趟车` : ''
  const title = `余票提醒：${summary}${more}`
  const body = details.slice(0, 3).join('\n')
  const native = globalThis.window?.CRTicketAndroid
  if (native) {
    if (!native.notifyTicketAvailability) return { success: false }
    return callNativeNotification('notifyTicketAvailability', [title, body], signal)
  }

  const Notification = globalThis.window?.Notification
  if (!Notification || Notification.permission !== 'granted') return { success: false }
  const notification = new Notification(title, {
    body,
    icon: 'Picture1.png',
    tag: 'cr-ticket-availability',
    renotify: true,
  })
  notification.onclick = () => {
    window.focus()
    notification.close()
  }
  return { success: true }
}
