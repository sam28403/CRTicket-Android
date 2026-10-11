import { nativeFetch } from './nativeFetch.js'
import { queryTrainInfo, validateTrainQuery } from './trainInfo.js'
import { loadLltDatabase } from './lltLoader.js'
import { listLltTrains, validListDate, runsOnDate } from './lltList.js'
import { queryLeftTicket } from './leftTicket.js'
import { requestStation } from './station.js'

const summaries = new WeakMap()

export async function requestRailway(path, signal) {
    const url = new URL(path, 'https://phone.invalid')
    const query = Object.fromEntries(url.searchParams)
    if (url.pathname === '/station') return requestStation(query, signal)
    if (url.pathname === '/left-ticket') return queryLeftTicket(query, nativeFetch, signal)
    if (url.pathname === '/train-info') {
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
    if (url.pathname === '/llt-trains/detail') {
        const train = query.train
        if (!/^[A-Z]?\d{1,5}[A-Z]?(?:\/[A-Z]?\d{1,5}[A-Z]?)*$/.test(train) || train.length > 40) {
            throw new Error('请选择有效车次')
        }
        const item = database.trains.get(train)
        const stops = item && runsOnDate(item.schedule, query.date) ? item.stops : []
        return {
            success: true,
            train,
            date: query.date,
            source: '路路通',
            mileageVersion: database.version,
            from: stops[0]?.station || '',
            to: stops.at(-1)?.station || '',
            stops,
            warnings: [],
        }
    }
    if (url.pathname !== '/llt-trains') throw new Error('未知的铁路接口')
    let cache = summaries.get(database)
    if (!cache) summaries.set(database, cache = new Map())
    if (!cache.has(query.date)) {
        if (cache.size >= 8) cache.delete(cache.keys().next().value)
        cache.set(query.date, listLltTrains(database, query.date))
    }
    return { success: true, ...cache.get(query.date) }
}
