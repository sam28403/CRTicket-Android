import { callNativeNotification } from './nativeNotifications.js'
import { supportsNativeMethods } from './nativeCalls.js'

export function supportsTrainTracking() {
  return supportsNativeMethods([
    'requestTrainTrackingPermission', 'trainTrackingState',
    'startTrainTracking', 'stopTrainTracking',
  ])
}

export function trackingPayload(result, queryTrain, querySource) {
  return JSON.stringify({
    train: result.train,
    date: result.date,
    queryTrain: queryTrain || result.train,
    querySource: querySource === 'llt' ? 'llt' : '',
    stops: result.stops.map(stop => ({
      station: stop.station,
      train: stop.train,
      arrival: stop.arrival,
      departure: stop.departure,
      arrivalDay: stop.arrivalDay,
      departureDay: stop.departureDay,
    })),
  })
}

export function requestTrainTrackingPermission(signal) {
  return callNativeNotification('requestTrainTrackingPermission', [], signal)
}

export function getTrainTrackingState(signal) {
  return callNativeNotification('trainTrackingState', [], signal, 15000)
}

export function startTrainTracking(payload, signal) {
  return callNativeNotification('startTrainTracking', [payload], signal, 15000)
}

export function stopTrainTracking(signal) {
  return callNativeNotification('stopTrainTracking', [], signal, 15000)
}
