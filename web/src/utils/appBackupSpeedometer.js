const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)
const text = (value, required = true) => typeof value === 'string' && value.length <= 100
  && (!required || value.trim()) && !/[\u0000-\u001f\u007f-\u009f]/.test(value)
const integer = (value, minimum) => Number.isSafeInteger(value) && value >= minimum

export function validSpeedRecordings(rows) {
  if (!Array.isArray(rows) || rows.length > 10000) return false
  const ids = new Set()
  let count = 0
  for (const row of rows) {
    if (!object(row) || ['id', 'train', 'from', 'to'].some(key => !text(row[key]))
        || ids.has(row.id) || (row.vehicle !== undefined && !text(row.vehicle, false))
        || !integer(row.started, 0) || !integer(row.elapsed, 60000)
        || typeof row.meters !== 'number' || !Number.isFinite(row.meters) || row.meters < 0
        || !Array.isArray(row.points)) return false
    ids.add(row.id)
    count += row.points.length
    if (count > 1000000) return false
    let previousTime = -1
    let previousSegment = -1
    for (const point of row.points) {
      if (!Array.isArray(point) || point.length !== 4
          || typeof point[0] !== 'number' || !Number.isFinite(point[0]) || Math.abs(point[0]) > 90
          || typeof point[1] !== 'number' || !Number.isFinite(point[1]) || Math.abs(point[1]) > 180
          || !integer(point[2], 0) || point[2] <= previousTime
          || !integer(point[3], 0) || point[3] > 2147483647 || point[3] < previousSegment) return false
      previousTime = point[2]
      previousSegment = point[3]
    }
  }
  return true
}

const draftTextFields = new Set([
  'number', 'from', 'to', 'trainNo', 'date', 'time', 'seatType', 'seatNo',
  'sellPlace', 'gate', 'message', 'theme',
])

export function validGeneratorDraft(value) {
  if (!object(value)) return false
  return Object.entries(value).every(([key, field]) => {
    if (draftTextFields.has(key)) return typeof field === 'string'
      && field.length <= (key === 'message' ? 2000 : 200)
    if (['price', 'distance'].includes(key)) return typeof field === 'string' && field.length <= 200
      || typeof field === 'number' && Number.isFinite(field)
    if (['useCredit', 'hasConditioner'].includes(key)) return field === 0 || field === 1
    return false
  })
}
