import { queryLltRouting } from './lltRouting.js'

export function nextRoutingTrain(database, train, date) {
  if (!database.routing) return null
  const route = queryLltRouting(database, train, date).groups[0]
  if (!route?.rows.length) return null
  const index = route.rows.findIndex(row => row.highlighted)
  return index < 0 ? null : route.rows[(index + 1) % route.rows.length]
}
