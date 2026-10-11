import { callNativeNotification } from './monitorNotifications.js'

export function supportsStationArrival() {
  const bridge = globalThis.window?.CRTicketAndroid
  return ['stationArrivalState', 'enableStationArrival', 'disableStationArrival']
    .every(method => typeof bridge?.[method] === 'function')
}

export function getStationArrivalState(signal) {
  return callNativeNotification('stationArrivalState', [], signal, 15000)
}

export function enableStationArrival(signal) {
  // 系统设置授权需要用户操作，不设置固定超时。
  return callNativeNotification('enableStationArrival', [], signal)
}

export function disableStationArrival(signal) {
  return callNativeNotification('disableStationArrival', [], signal, 15000)
}
