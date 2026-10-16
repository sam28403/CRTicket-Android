import { parseStations } from '../../utils/stations.js'
import { stationQueryName } from '../../utils/stationName.js'
import { trainQueryRange } from '../../utils/trainQueryDate.js'
import { loadLltDatabase } from './lltLoader.js'
import { listLltStation } from './stationLlt.js'
import { cancelledRequest, waitForRequest } from '../../utils/requests.js'

const stations = new Map(parseStations().map(station => [station.name, station]))

export async function queryStation(station, date, {
  loadDatabase = loadLltDatabase,
} = {}) {
  const database = await loadDatabase()
  return {
    source: '路路通',
    version: database.version,
    trains: listLltStation(database, station.name, date),
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
    const date = trainQueryRange(now()).today
    if (!station) throw new Error('请输入车站名称（不超过 30 个字）')
    if (query.date != null && query.date !== date) {
      throw new Error('车站大屏仅支持查询当天信息')
    }

    const key = `${date}-${station.name}`
    let entry = cache.get(key)
    if (!entry || now() - entry.cachedAt >= 60000) {
      let task = pending.get(key)
      if (!task) {
        task = queryStation(station, date, options).then(result => {
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
    if (date !== trainQueryRange(now()).today) {
      throw new Error('日期已更新，请重新查询当天信息')
    }
    return { success: true, ...entry.data, total: entry.data.trains.length }
  }
}

export const requestStation = createStationQuery()
