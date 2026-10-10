import stationData from '../../station_name.js'
import { parseSeats } from './leftTicketSeats.js'
import { nativeFetch } from './nativeFetch.js'

const origin = 'https://kyfw.12306.cn'
const stationCodes = new Set(stationData.split('@').filter(Boolean).map(row => row.split('|')[2]))

export function parseTrains(data) {
    if (!Array.isArray(data?.result) || !data.map || typeof data.map !== 'object') {
        throw new Error('12306 返回的数据格式已变化，请稍后重试')
    }
    return data.result.map(row => {
        if (typeof row !== 'string') throw new Error('12306 车次数据格式异常')
        const fields = row.split('|')
        if (fields.length < 33 || !fields[2] || !fields[3]) {
            throw new Error('12306 返回的车次字段不完整')
        }
        return {
            id: `${fields[2]}-${fields[6]}-${fields[7]}`,
            train: fields[3],
            from: data.map[fields[6]] || fields[6],
            to: data.map[fields[7]] || fields[7],
            departure: fields[8],
            arrival: fields[9],
            duration: fields[10],
            canBuy: fields[11] === 'Y',
            status: fields[1].replace(/<[^>]*>/g, ''),
            seats: parseSeats(fields),
        }
    })
}

export function validateQuery({ from, to, date }) {
    if (!stationCodes.has(from) || !stationCodes.has(to)) return '请选择有效的出发站和到达站'
    if (typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return '出发日期格式错误'
    const parsed = new Date(`${date}T00:00:00Z`)
    if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
        return '出发日期无效'
    }
    const today = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
    const end = new Date(Date.parse(today) + 14 * 86400000).toISOString().slice(0, 10)
    return date < today || date > end ? '请选择今天起 15 天内的出发日期' : ''
}

export async function queryLeftTicket(query, fetchImpl = nativeFetch, signal) {
    const error = validateQuery(query)
    if (error) throw new Error(error)
    const options = { signal: signal || AbortSignal.timeout(20000) }
    const init = await fetchImpl(`${origin}/otn/leftTicket/init`, options)
    if (!init.ok) throw new Error(`12306 初始化失败（HTTP ${init.status}）`)
    const html = await init.text()
    let path = html.match(/CLeftTicketUrl\s*=\s*['"](leftTicket\/query[A-Za-z]*)['"]/)?.[1]
    if (!path) throw new Error('无法识别 12306 查询接口，官网可能需要验证或已更新')
    const parameters = new URLSearchParams({
        'leftTicketDTO.train_date': query.date,
        'leftTicketDTO.from_station': query.from,
        'leftTicketDTO.to_station': query.to,
        purpose_codes: 'ADULT',
    })
    for (let attempt = 0; attempt < 2; attempt++) {
        const response = await fetchImpl(`${origin}/otn/${path}?${parameters}`, options)
        if (!response.ok) throw new Error(`12306 查询失败（HTTP ${response.status}）`)
        const body = await response.json()
        if (body.status === true) {
            return { success: true, trains: parseTrains(body.data), queriedAt: new Date().toISOString() }
        }
        if (attempt === 0 && /^leftTicket\/query[A-Za-z]*$/.test(body.c_url)) {
            path = body.c_url
            continue
        }
        throw new Error('12306 暂未提供余票数据，请稍后重试或在官网查询')
    }
}
