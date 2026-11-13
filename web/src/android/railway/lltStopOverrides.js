const clock = /^(?:(?:[01]\d|2[0-3]):[0-5]\d|24:00)$/

function isClock(value) {
  return value === null || (typeof value === 'string' && clock.test(value))
}

function isDay(value) {
  return Number.isSafeInteger(value) && value >= 0
}

export function applyLltStopOverrides(bytes, trains) {
  if (!bytes) return
  const rows = JSON.parse(new TextDecoder().decode(bytes))
  const seen = new Set()
  if (!Array.isArray(rows)) throw new Error('停站补充数据格式异常')
  for (const [code, changes] of rows) {
    const train = trains.get(code)
    if (!train || seen.has(code) || !Array.isArray(changes) || !changes.length) {
      throw new Error('停站补充数据车次无效')
    }
    seen.add(code)
    let previous = -1
    for (const [index, arrival, departure, arrivalDay, departureDay, stay, mileage,
      sourceArrival, sourceDeparture] of changes) {
      if (!Number.isInteger(index) || index <= previous || index >= train.stops.length
        || !isClock(arrival) || !isClock(departure) || !isDay(arrivalDay) || !isDay(departureDay)
        || (stay !== null && !isDay(stay))
        || (mileage !== null && !Number.isFinite(mileage))) {
        throw new Error('停站补充数据记录无效')
      }
      previous = index
      Object.assign(train.stops[index], { arrival, departure, arrivalDay, departureDay, stay, mileage })
      if (sourceArrival !== null) train.stops[index].sourceArrival = sourceArrival
      if (sourceDeparture !== null) train.stops[index].sourceDeparture = sourceDeparture
    }
    if (train.stops.some(({ mileage }, index, stops) =>
      mileage == null || (index === 0 ? mileage !== 0 : mileage < stops[index - 1].mileage))) {
      train.mileageReliable = false
    }
  }
}
