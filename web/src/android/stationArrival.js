import { callNativeNotification } from './nativeNotifications.js'
import { supportsNativeMethods } from './nativeCalls.js'

export function supportsStationArrival() {
  return supportsNativeMethods([
    'stationArrivalState', 'enableStationArrival', 'disableStationArrival',
  ])
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
