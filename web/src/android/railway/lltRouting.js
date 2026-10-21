import { lltTrainSummary, runsOnDate } from './lltList.js'

// jlb.dat begins with a big-endian record count and variable-length UTF-8 records.
// Other sections follow these records; they are not part of the routing table.
export function parseLltRouting(bytes) {
  if (!bytes) return []
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const decoder = new TextDecoder('utf-8', { fatal: true })
  let offset = 4
  const count = view.getUint32(0)
  if (count > 50000) throw new Error('交路记录数量异常')
  function text() {
    const length = bytes[offset++]
    if (length == null || offset + length > bytes.length) throw new Error('交路记录被截断')
    const value = decoder.decode(bytes.subarray(offset, offset + length))
    offset += length
    return value
  }
  return Array.from({ length: count }, (_, index) => {
    if (offset + 18 > bytes.length) throw new Error('交路记录被截断')
    const start = view.getUint32(offset)
    const end = view.getUint32(offset + 4)
    offset += 18
    const model = text()
    const trains = text().split('#').filter(Boolean)
    return { id: index, start, end, model, trains }
  })
}

export function routingTrainCodes(train) {
  const parts = train.toUpperCase().split('/')
  const first = parts[0].match(/^([A-Z]*)(\d+)$/)
  if (!first) return [train]
  return parts.map(part => /^\d+$/.test(part)
    ? first[1] + first[2].slice(0, Math.max(0, first[2].length - part.length)) + part
    : part)
}

export function queryLltRouting(database, input, date) {
  const train = input.trim().toUpperCase()
  if (!/^(?:[A-Z]{0,2}\d{1,5}|0[A-Z]{1,2}\d{1,5})(?:\/[A-Z]*\d{1,5})*$/.test(train)) {
    throw new Error('请输入有效车次')
  }
  if (!database.routing) throw new Error('当前数据包没有交路数据，请稍后重试')
  const selected = routingTrainCodes(train)
  const day = Number(date.replaceAll('-', ''))
  const groups = database.routing.filter(route => day >= route.start && day <= route.end
    && route.trains.some(code => routingTrainCodes(code).some(value => selected.includes(value))))
  const all = [...database.trains.values()]
  return { train, date, version: database.version, groups: groups.map(route => ({
    id: route.id,
    model: route.model,
    rows: route.trains.map(code => {
      const codes = routingTrainCodes(code)
      const candidates = all.filter(item => runsOnDate(item.schedule, date)
        && routingTrainCodes(item.train).some(value => codes.includes(value)))
      const item = candidates.length === 1 ? candidates[0] : null
      return {
        ...(item ? lltTrainSummary(item) : {}),
        train: code,
        detailTrain: item?.train || '',
        highlighted: codes.some(value => selected.includes(value)),
        clickable: Boolean(item) && codes.every(value => /^[A-Z]?\d{1,5}$/.test(value)
          && !/^0/.test(value)),
      }
    }),
  })) }
}
