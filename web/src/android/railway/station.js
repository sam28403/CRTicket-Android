import { showNonRunningTrains } from '../timetableVisibility.js'
import { parseStations } from '../../utils/stations.js'
import { stationQueryName } from '../../utils/stationName.js'
import { isVientianeStation, stationClock } from '../../utils/stationTimeZone.js'
import { listLcrStation } from './lcrOffline.js'
import { loadLltDatabase } from './lltLoader.js'
import { listLltStation } from './stationLlt.js'
import { cancelledRequest, waitForRequest } from '../../utils/requests.js'

const stations = new Map(parseStations().map(station => [station.name, station]))

export async function queryStation(station, date, {
  loadDatabase = loadLltDatabase,
  includeNotRunning = showNonRunningTrains(),
} = {}) {
  let lcr = isVientianeStation(station.name) ? listLcrStation(station.name, date) : []
  let database
  try {
    database = await loadDatabase()
  } catch (error) {
    if (!lcr.length) throw error
    return { source: '老中铁路（离线）', trains: lcr, warnings: [] }
  }
  if (loadDatabase.usingImported) lcr = []
  const key = row => JSON.stringify([row.train, row.trainDate, row.from, row.to])
  const curated = new Set(lcr.map(key))
  const domestic = listLltStation(database, station.name, date, includeNotRunning)
  return {
    source: '路路通',
    version: database.version,
    trains: [...domestic.filter(row => !curated.has(key(row))), ...lcr],
    warnings: [],
  }
}

export function createStationQuery(options = {}) {
  const now = options.now || Date.now
  const cache = new Map()
  const pending = new Map()

  return async function requestStation(query, signal) {
    if (signal?.aborted) {
      throw cancelledRequest()
    }
    const name = stationQueryName(query.station)
    const station = name ? stations.get(name) || { name, telecode: '' } : null
    const date = stationClock(now(), name).date
    if (!station) throw new Error('请输入车站名称（不超过 30 个字）')
    if (query.date != null && query.date !== date) {
      throw new Error('车站大屏仅支持查询当天信息')
    }

    const includeNotRunning = options.includeNotRunning ?? showNonRunningTrains()
    const key = `${loadLltDatabase.revision}-${includeNotRunning}-${date}-${station.name}`
    let entry = cache.get(key)
    if (!entry || now() - entry.cachedAt >= 60000) {
      let task = pending.get(key)
      if (!task) {
        task = queryStation(station, date, { ...options, includeNotRunning }).then(result => {
          const cachedAt = now()
          const value = {
            cachedAt,
            data: {
              ...result,
              station: station.name,
              date,
              generatedAt: new Date(cachedAt).toISOString(),
            },
          }
          if (cache.size >= 40) cache.delete(cache.keys().next().value)
          cache.set(key, value)
          return value
        }).catch(() => {
          throw new Error('路路通车站数据暂不可用，请稍后重试')
        }).finally(() => pending.delete(key))
        pending.set(key, task)
      }
      entry = await waitForRequest(task, signal)
    }
    if (date !== stationClock(now(), name).date) {
      throw new Error('日期已更新，请重新查询当天信息')
    }
    return { success: true, ...entry.data, total: entry.data.trains.length }
  }
}

export const requestStation = createStationQuery()
