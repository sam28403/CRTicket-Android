const numberAt = (bytes, offset) => bytes[offset] * 255 + bytes[offset + 1]

function trainCodes(train) {
  if (!/^[A-Z]?\d+[A-Z]?(?:\/[A-Z]?\d+[A-Z]?)+$/.test(train)) return []
  const prefix = train.match(/^[A-Z]/)?.[0] || ''
  return train.split('/').map(code => /^\d/.test(code) ? prefix + code : code)
}

// xw.dat: 255 进制记录数；每条为车次索引、2 字节属性、分段数，
// 随后每段为 2 字节累计里程和 1 字节车次分量索引。
export function applyLltTrainSegments(bytes, trainNames, trains) {
  if (!bytes || bytes.length < 2) return
  const records = []
  const count = numberAt(bytes, 0)
  let offset = 2
  for (let i = 0; i < count; i++) {
    if (offset + 5 > bytes.length) return
    const train = trainNames[numberAt(bytes, offset)]
    const size = bytes[offset + 4]
    offset += 5
    if (!train || offset + size * 3 > bytes.length) return
    const segments = []
    for (let j = 0; j < size; j++, offset += 3) {
      segments.push({ mileage: numberAt(bytes, offset), code: bytes[offset + 2] })
    }
    records.push({ train, segments })
  }
  // 可选数据损坏时不应用部分结果，继续显示完整车次。
  if (offset !== bytes.length) return
  for (const { train, segments } of records) {
    const item = trains.get(train)
    const codes = trainCodes(train)
    if (!item || !codes.length || !segments.length || segments[0].mileage !== 0) continue
    const lastMileage = item.stops.at(-1)?.mileage
    if (item.stops.some(stop => stop.mileage == null) || segments.some((segment, index) =>
      !codes[segment.code]
      || segment.mileage > lastMileage
      || (index > 0 && segment.mileage <= segments[index - 1].mileage)
    )) continue
    for (const stop of item.stops) {
      const departure = segments.findLast(segment => segment.mileage <= stop.mileage)
      const arrival = segments.findLast(segment => segment.mileage < stop.mileage) || segments[0]
      stop.arrivalTrain = codes[arrival.code]
      stop.departureTrain = codes[departure.code]
      stop.train = stop.departure ? stop.departureTrain : stop.arrivalTrain
    }
  }
}
