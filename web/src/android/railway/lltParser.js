import { unzipSync } from 'fflate'

const decode = bytes => new TextDecoder().decode(new Uint8Array(bytes))
function readBigEndian(bytes, offset) {
  if (offset + 2 > bytes.length) throw new Error('路路通索引被截断')
  return bytes[offset] * 256 + bytes[offset + 1]
}

const maxArchiveBytes = 16 * 1024 * 1024
const maxFileBytes = 4 * 1024 * 1024

// an.db 的字典长度是标准大端整数；T*.dat 的索引、里程使用 255 进制。
// 实测 20261004 包的 18783 条车次，全部车次及站点索引均能对应字典。
export function readLltNumber(buffer, offset) {
  return buffer[offset] * 255 + buffer[offset + 1]
}

// T*.dat 的日期是小端 128 进制的 YYYYMMDD，不是 Unix 时间戳。
export function readLltDate(buffer, offset) {
  const value = buffer[offset]
    + buffer[offset + 1] * 128
    + buffer[offset + 2] * 128 ** 2
    + buffer[offset + 3] * 128 ** 3
  if (!value) return null
  const text = String(value)
  const date = `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}`
  const parsed = new Date(`${date}T00:00:00Z`)
  if (
    text.length !== 8
    || !Number.isFinite(parsed.getTime())
    || parsed.toISOString().slice(0, 10) !== date
  ) {
    throw new Error('路路通开行日期无效')
  }
  return date
}

function readRules(bytes) {
  if (!bytes) return []
  return decode(bytes).trim().split(/\r?\n/).map(line => {
    const match = line.match(/^(\d+) (\d+)$/)
    if (!match) throw new Error('路路通开行规则格式异常')
    const period = Number(match[1])
    const mask = Number(match[2])
    if (
      period < 1
      || period > 31
      || !Number.isSafeInteger(mask)
      || mask < 0
      || mask >= 2 ** period
    ) {
      throw new Error('路路通开行规则无效')
    }
    return { period, mask }
  })
}

function readNames(bytes) {
  if (!bytes) throw new Error('路路通数据缺少索引')
  const buffer = bytes
  const count = readBigEndian(buffer, 0)
  const names = []
  let offset = 2
  for (let i = 0; i < count; i++) {
    const length = readBigEndian(buffer, offset)
    offset += 2
    if (!length || offset + length > buffer.length) throw new Error('路路通索引格式异常')
    names.push(decode(buffer.subarray(offset, offset + length)))
    offset += length
  }
  if (offset !== buffer.length) throw new Error('路路通索引长度不一致')
  return names
}

export function parseLltFiles(files) {
  const trainNames = readNames(files['t.i'])
  const stationNames = readNames(files['s.i'])
  const version = decode(files['ver.txt'] || []).trim()
  if (!/^\d{8}$/.test(version)) throw new Error('路路通数据版本无效')
  const trains = new Map()
  const rules = readRules(files['t.rule'])
  for (let file = 0; file < 20; file++) {
    if (!files[`T${file}.dat`]) throw new Error('路路通车次数据不完整')
    const buffer = files[`T${file}.dat`]
    let offset = 0
    while (offset < buffer.length) {
      if (offset + 17 > buffer.length) throw new Error('路路通车次记录被截断')
      const train = trainNames[readLltNumber(buffer, offset)]
      const count = readBigEndian(buffer, offset + 15)
      const startDate = readLltDate(buffer, offset + 5)
      const endDate = readLltDate(buffer, offset + 9)
      const ruleIndex = readLltNumber(buffer, offset + 13)
      if (startDate && endDate && startDate > endDate) throw new Error('路路通开行日期范围异常')
      if (ruleIndex && (!startDate || !rules[ruleIndex])) throw new Error('路路通开行规则索引异常')
      offset += 17
      if (!train || trains.has(train) || !count || offset + count * 7 > buffer.length) throw new Error('路路通车次索引异常')
      const stops = []
      let previousMileage = -1
      let validMileage = true
      let day = 0
      let previousDeparture = null
      for (let i = 0; i < count; i++, offset += 7) {
        const station = stationNames[readLltNumber(buffer, offset)]
        const hour = buffer[offset + 2]
        const minute = buffer[offset + 3]
        const stay = buffer[offset + 4]
        const mileage = readLltNumber(buffer, offset + 5)
        if (!station || hour > 23 || minute > 59) throw new Error('路路通车站记录异常')
        if (mileage < previousMileage || (i === 0 && mileage !== 0)) validMileage = false
        previousMileage = mileage
        const arrivalMinutes = hour * 60 + minute
        if (previousDeparture != null && arrivalMinutes < previousDeparture) day++
        const arrivalDay = day
        const departureMinutes = arrivalMinutes + stay
        day += Math.floor(departureMinutes / 1440)
        previousDeparture = departureMinutes % 1440
        const clock = value =>
          `${String(Math.floor(value / 60) % 24).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`
        stops.push({ no: String(i + 1).padStart(2, '0'), train, station,
          arrival: i === 0 ? null : clock(arrivalMinutes),
          departure: i === count - 1 ? null : clock(departureMinutes),
          stay: i === 0 || i === count - 1 ? null : stay, arrivalDay, departureDay: day, mileage })
      }
      // 个别记录的里程中途归零，整条记录不用于补全。
      if (!validMileage) stops.forEach(stop => { stop.mileage = null })
      trains.set(train, {
        train,
        stops,
        schedule: {
          startDate,
          endDate,
          rule: ruleIndex ? rules[ruleIndex] : null,
        },
      })
    }
  }
  if (trains.size !== trainNames.length) throw new Error('路路通车次字典与记录数量不一致')
  return { version, trains }
}

export function parseLltArchive(bytes) {
  if (bytes.byteLength > maxArchiveBytes) throw new Error('路路通数据包过大')
  const files = unzipSync(bytes, { filter: entry => {
    if (!/^(?:[st]\.i|t\.rule|ver\.txt|T\d{1,2}\.dat)$/.test(entry.name)) return false
    if (entry.originalSize > maxFileBytes) throw new Error('路路通数据文件过大')
    return true
  } })
  return parseLltFiles(files)
}

