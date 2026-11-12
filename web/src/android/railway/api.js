import { showNonRunningTrains } from '../timetableVisibility.js'
import { requireNetwork } from '../networkFeatures.js'
import { nativeFetch } from './nativeFetch.js'
import { queryLcrTrainInfo, queryTrainInfo, validateTrainQuery } from './trainInfo.js'
import { loadLltDatabase } from './lltLoader.js'
import { listLltTrains, validListDate } from './lltList.js'
import { lltTrainDetail } from './lltDetail.js'
import { queryLeftTicket } from './leftTicket.js'
import { requestStation } from './station.js'
import { waitForRequest } from '../../utils/requests.js'
import { createLcrTimetableLoader } from './lcrTimetable.js'
import { combineTrainResults, lcrTrainDetail, listLcrTrains } from './lcrOffline.js'
import { hasLcrOperatingRule } from './lcrOperatingDays.js'
import { listStationSummaries } from './stationDiscovery.js'

const summaries = new WeakMap()
const loadLcrTimetable = createLcrTimetableLoader(nativeFetch)

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
        if (query.dataset === 'lcr') {
            return { success: true, ...await waitForRequest(queryLcrTrainInfo(train, query.date, loadLcrTimetable), signal) }
        }
        if (query.dataset) throw new Error('请选择有效时刻表')
        const fetchImpl = (upstream, options) => nativeFetch(upstream, {
            ...options,
            signal: signal && options.signal ? AbortSignal.any([signal, options.signal]) : signal || options.signal,
        })
        return { success: true, ...await waitForRequest(queryTrainInfo(train, query.date, fetchImpl, loadLltDatabase, loadLcrTimetable), signal) }
    }
    if (!validListDate(query.date)) throw new Error('请选择有效日期')
    const isDetail = url.pathname === '/llt-trains/detail'
    if (isDetail && query.dataset && query.dataset !== 'lcr') throw new Error('请选择有效时刻表')
    const lcr = isDetail ? lcrTrainDetail(query.train, query.date) : null
    const hasLcrCalendar = isDetail && hasLcrOperatingRule(query.train)
    if (isDetail && query.dataset === 'lcr') return lcr
    const lcrList = url.pathname === '/llt-trains' ? listLcrTrains(query.date) : null
    let database
    try {
        database = await loadLltDatabase()
    } catch (error) {
        if (lcr?.stops.length || hasLcrCalendar) return lcr
        if (lcrList?.total) return { success: true, ...lcrList }
        throw error
    }
    if (url.pathname === '/station/discovery') {
        if (query.vientianeDate !== undefined && !validListDate(query.vientianeDate)) {
            throw new Error('请选择有效日期')
        }
        return { success: true, date: query.date,
            stations: listStationSummaries(database, query.date, {
                includeLcr: !loadLltDatabase.usingImported,
                vientianeDate: query.vientianeDate,
            }) }
    }
    const includeNotRunning = showNonRunningTrains()
    if (url.pathname === '/llt-trains/detail') {
        const domestic = lltTrainDetail(database, query.train, query.date, includeNotRunning)
        if (loadLltDatabase.usingImported) return domestic
        if (!domestic.stops.length && (lcr.stops.length || hasLcrCalendar)) return lcr
        if (!lcr.stops.length) return domestic
        return combineTrainResults([domestic, lcr, ...(lcr.alternatives || [])])
    }
    if (url.pathname !== '/llt-trains') throw new Error('未知的铁路接口')
    let cache = summaries.get(database)
    if (!cache) summaries.set(database, cache = new Map())
    const key = `${query.date}:${includeNotRunning}`
    if (!cache.has(key)) {
        if (cache.size >= 8) cache.delete(cache.keys().next().value)
        const list = listLltTrains(database, query.date, includeNotRunning)
        cache.set(key, loadLltDatabase.usingImported ? list : listLcrTrains(query.date, list))
    }
    return { success: true, ...cache.get(key) }
}
