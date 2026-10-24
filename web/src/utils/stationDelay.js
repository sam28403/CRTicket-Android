const dayMinutes = 1440

export function applyStationDelay(row, prediction) {
  const result = { ...row }
  for (const field of ['arrival', 'departure']) {
    const offset = prediction[`${field}Offset`]
    if (!Number.isInteger(offset) || !/^\d{2}:\d{2}$/.test(row[field] || '')) continue
    const [hour, minute] = row[field].split(':').map(Number)
    const actual = hour * 60 + minute + offset
    const clock = (actual % dayMinutes + dayMinutes) % dayMinutes
    result[`${field}Day`] = Math.floor(actual / dayMinutes)
    result[`${field}Offset`] = offset
    result[field] = `${String(Math.floor(clock / 60)).padStart(2, '0')}:${String(clock % 60).padStart(2, '0')}`
    result[`${field}Label`] = result[field]
  }
  if (row.stay != null) {
    const stay = Number(row.stay) + (result.departureOffset || 0) - (result.arrivalOffset || 0)
    result.stay = stay > 0 ? stay : row.stay
  }
  return result
}

export function stationDelayLabel(row, field) {
  const offset = row[`${field}Offset`] || 0
  const label = row[`${field}Label`]
  if (!row[field] || !offset) return label
  const day = row[`${field}Day`] || 0
  const prefix = day > 0 ? '次日 ' : day < 0 ? '昨日 ' : ''
  return `${prefix}${label} (${offset > 0 ? '+' : ''}${offset}')`
}
