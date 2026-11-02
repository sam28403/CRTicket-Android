// extra.dat stores turn stations separately from free-text routing notes.
// Numbers in this file use two-byte base 255 encoding.
export function applyLltTurnStations(bytes, trainNames, stationNames, trains) {
  if (!bytes) return
  let offset = 0
  function skip(size) {
    if (!Number.isInteger(size) || size < 0 || offset + size > bytes.length) {
      throw new Error('Invalid optional turn station data')
    }
    offset += size
  }
  function byte() {
    skip(1)
    return bytes[offset - 1]
  }
  const number = () => byte() * 255 + byte()
  const records = []
  try {
    const companies = number()
    for (let i = 0; i < companies; i++) skip(byte())
    const extras = number()
    for (let i = 0; i < extras; i++) {
      skip(4)
      const stations = byte()
      for (let j = 0; j < stations; j++) {
        skip(2)
        const units = byte()
        for (let k = 0; k < units; k++) {
          skip(8)
          skip(byte())
        }
      }
    }
    // Three rolling-stock classification lists precede the turn station table.
    for (let i = 0; i < 3; i++) skip(number() * 2)
    const count = number()
    for (let i = 0; i < count; i++) {
      const train = trainNames[number()]
      const size = byte()
      const stations = new Set()
      if (!train) return
      for (let j = 0; j < size; j++) {
        const station = stationNames[number()]
        if (!station) return
        stations.add(station)
      }
      records.push({ train, stations })
    }
  } catch {
    // Optional metadata must never interrupt the timetable query.
    return
  }
  for (const { train, stations } of records) {
    for (const stop of trains.get(train)?.stops || []) {
      if (stations.has(stop.station)) stop.isTurn = true
    }
  }
}
