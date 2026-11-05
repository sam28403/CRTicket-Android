import { showNonRunningTrains } from '../timetableVisibility.js'
import { requireNetwork } from '../networkFeatures.js'
import { nativeFetch } from './nativeFetch.js'
import { queryTrainInfo, validateTrainQuery } from './trainInfo.js'
import { loadLltDatabase } from './lltLoader.js'
import { listLltTrains, validListDate } from './lltList.js'
import { lltTrainDetail } from './lltDetail.js'
import { queryLeftTicket } from './leftTicket.js'
import { requestStation } from './station.js'
import { listStationSummaries } from './stationDiscovery.js'

const summaries = new WeakMap()

export async function requestRailway(path, signal) {
    const url = new URL(path, 'https://phone.invalid')
    const query = Object.fromEntries(url.searchParams)
    if (url.pathname === '/station') return requestStation(query, signal)
    if (url.pathname === '/left-ticket') {
        requireNetwork()
        return queryLeftTicket(query, nativeFetch, signal)
    }
    if (url.pathname === '/train-info') {
        requireNetwork()
        const train = query.train?.trim().toUpperCase()
        const error = validateTrainQuery({ train, date: query.date })
        if (error) throw new Error(error)
        const fetchImpl = (upstream, options) => nativeFetch(upstream, {
            ...options,
            signal: signal || options.signal,
        })
        return { success: true, ...await queryTrainInfo(train, query.date, fetchImpl) }
    }
    if (!validListDate(query.date)) throw new Error('请选择有效日期')
    const database = await loadLltDatabase()
    if (url.pathname === '/station/discovery') {
        return { success: true, date: query.date,
            stations: listStationSummaries(database, query.date) }
    }
    const includeNotRunning = showNonRunningTrains()
    if (url.pathname === '/llt-trains/detail') {
        return lltTrainDetail(database, query.train, query.date, includeNotRunning)
    }
    if (url.pathname !== '/llt-trains') throw new Error('未知的铁路接口')
    let cache = summaries.get(database)
    if (!cache) summaries.set(database, cache = new Map())
    const key = `${query.date}:${includeNotRunning}`
    if (!cache.has(key)) {
        if (cache.size >= 8) cache.delete(cache.keys().next().value)
        cache.set(key, listLltTrains(database, query.date, includeNotRunning))
    }
    return { success: true, ...cache.get(key) }
}
