import { getDepartureMinutes } from './trainTime.js'

function dateKey(date) {
  const normalized = String(date || '').replace(/年|月|-/g, '/').replace(/日/g, '')
  return Date.parse(normalized) || 0
}

export function compareHistoryDeparture(a, b, direction) {
  const dateDifference = dateKey(a.date) - dateKey(b.date)
  if (dateDifference !== 0) return dateDifference * direction

  const aMinutes = getDepartureMinutes(a.time)
  const bMinutes = getDepartureMinutes(b.time)
  if (aMinutes === bMinutes) return 0
  if (aMinutes === null) return 1
  if (bMinutes === null) return -1
  return (aMinutes - bMinutes) * direction
}
