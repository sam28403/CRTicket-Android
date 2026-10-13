import { hasAvailableSeat } from './seatAvailability.js'
import { matchesDepartureTime } from './trainTime.js'

export const ticketSeatColumns = [
  ['second', '二等座'],
  ['first', '一等座'],
  ['premiumSleeper', '优选一等座'],
  ['business', '商务座'],
  ['premium', '特等座'],
  ['advancedSleeper', '高级软卧'],
  ['softSleeper', '软卧 / 动卧'],
  ['hardSleeper', '硬卧'],
  ['softSeat', '软座'],
  ['hardSeat', '硬座'],
  ['standing', '无座'],
  ['other', '其他'],
]

export function isOrdinaryTrain(trainNo) {
  const normalized = String(trainNo || '').trim().toUpperCase()
  if (/^(?:[ZTKLY]\d+|\d+)$/.test(normalized)) return true
  const match = normalized.match(/^([DCS])(\d+)$/)
  if (!match) return false
  const number = Number(match[2])
  if (match[1] === 'D') {
    return (number >= 1 && number <= 300) || (number >= 701 && number <= 800)
  }
  if (match[1] === 'C') {
    return (number >= 1 && number <= 999) || (number >= 4001 && number <= 4999)
  }
  return (number >= 101 && number <= 999) || (number >= 1001 && number <= 2999)
}

export function matchesTicketMonitor(row, filters) {
  const trains = filters.trainFilters.map(value => value.trim().toUpperCase())
  const ordinary = isOrdinaryTrain(row.train)
  return (
    (!trains.length || trains.includes(String(row.train).toUpperCase())) &&
    ((!filters.highSpeedOnly && !filters.ordinaryOnly) ||
      (filters.highSpeedOnly && !ordinary) ||
      (filters.ordinaryOnly && ordinary)) &&
    (!filters.selectedDepartureStations.length ||
      filters.selectedDepartureStations.includes(row.from)) &&
    (!filters.selectedArrivalStations.length ||
      filters.selectedArrivalStations.includes(row.to)) &&
    matchesDepartureTime(row.departure, filters.timeRange)
  )
}

export const watchedTicketColumns = filters => ticketSeatColumns.filter(
  ([key]) => !filters.seatFilters.length || filters.seatFilters.includes(key)
)

export const rowHasTicket = (row, columns) =>
  row.canBuy && columns.some(([key]) => hasAvailableSeat(row.seats?.[key]))
