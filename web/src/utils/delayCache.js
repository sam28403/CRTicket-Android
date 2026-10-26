import { applyStationDelay } from './stationDelay.js'

export function mergeDelayOffsets(previous, incoming) {
  const offsets = { ...previous }
  for (const field of ['arrivalOffset', 'departureOffset']) {
    if (Number.isInteger(incoming?.[field])) offsets[field] = incoming[field]
  }
  return offsets
}

export function trainDelayCacheKey(snapshot) {
  return JSON.stringify([snapshot.train, snapshot.date, snapshot.stops.map(row => [
    row.station, row.train, row.arrival, row.departure, row.arrivalDay, row.departureDay,
  ])])
}

export function applyTrainDelay(snapshot, offsets) {
  const stops = snapshot.stops.map((row, index) => {
    const prediction = offsets[index] || {}
    const corrected = applyStationDelay(row, prediction)
    for (const field of ['arrival', 'departure']) {
      if (!Number.isInteger(prediction[`${field}Offset`])) continue
      const day = field === 'arrival' ? row.arrivalDay || 0
        : row.departureDay ?? ((row.arrivalDay || 0)
          + (row.arrival && row.departure < row.arrival ? 1 : 0))
      corrected[`${field}Day`] += day
    }
    return corrected
  })
  return { ...snapshot, stops, scheduledStops: snapshot.stops, delayAvailable: true, delayEnabled: true }
}

export function stationDelayCacheKey(snapshot) {
  return JSON.stringify([snapshot.station, snapshot.date])
}

export function stationDelayRowKey(row) {
  return JSON.stringify([row.id, row.train, row.trainDate, row.arrival, row.departure])
}
