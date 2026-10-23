import { createHash } from 'node:crypto'
import { readFile, writeFile, rename } from 'node:fs/promises'
import https from 'node:https'
import { rootCertificates } from 'node:tls'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

export const saleScheduleSource = 'https://www.12306.cn/mormhweb/zxdt/201411/t20141126_2316.html'
const root = fileURLToPath(new URL('../', import.meta.url))

export function parseSaleSchedule(html) {
    const text = html.replace(/<!--[\s\S]*?-->/g, '').replace(/<[^>]*>/g, '')
        .replace(/&nbsp;|&#160;|&#xA0;/gi, ' ')
    const headings = [...text.matchAll(/(\d{1,2})\s*:\s*(\d{2})\s*起售车站/g)]
    if (headings.length !== 41) throw new Error('Incomplete official sale schedule')
    const stations = {}
    headings.forEach((heading, index) => {
        const hour = Number(heading[1])
        const minute = Number(heading[2])
        if (hour < 8 || hour > 18 || minute % 15 || (hour === 18 && minute)) {
            throw new Error('Invalid sale time slot')
        }
        const start = heading.index + heading[0].length
        const end = headings[index + 1]?.index ?? text.indexOf('。', start)
        if (end < start) throw new Error('Missing sale schedule terminator')
        const time = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
        for (const name of text.slice(start, end).split(/[、，,。\s]+/u)) {
            if (/^[\p{Script=Han}A-Za-z0-9·（）()]+$/u.test(name)) stations[name] = time
        }
    })
    if (Object.keys(stations).length < 2000 || new Set(Object.values(stations)).size !== 41) {
        throw new Error('Incomplete station sale times')
    }
    return stations
}

async function download() {
    const certificate = await readFile(path.join(root, 'app/src/main/res/raw/cfca_ev_root.pem'), 'utf8')
    return new Promise((resolve, reject) => {
        const request = https.get(saleScheduleSource, {
            ca: [...rootCertificates, certificate],
            headers: { 'User-Agent': 'CRTicket station data update' },
        }, response => {
            if (response.statusCode !== 200) {
                response.resume()
                reject(new Error(`Sale schedule HTTP ${response.statusCode}`))
                return
            }
            response.setEncoding('utf8')
            let html = ''
            response.on('data', chunk => { html += chunk })
            response.on('end', () => resolve(html))
            response.on('error', reject)
        })
        request.setTimeout(15000, () => request.destroy(new Error('Sale schedule update timed out')))
        request.on('error', reject)
    })
}

export async function updateSaleTimes({ workspace = root, fetchHtml = download, force = false } = {}) {
    const input = await readFile(path.join(workspace, 'web/src/station_name.js'))
    const stationHash = createHash('sha256').update(input).digest('hex')
    const output = path.join(workspace, 'app/src/main/assets/sale-times.json')
    const previous = await readFile(output, 'utf8').then(JSON.parse).catch(() => null)
    if (!force && previous?.stationHash === stationHash && Object.keys(previous.stations || {}).length >= 2000) {
        return { updated: false, count: Object.keys(previous.stations).length }
    }
    const stations = parseSaleSchedule(await fetchHtml())
    const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
    const snapshot = { source: saleScheduleSource, date, stationHash, stations }
    await writeFile(`${output}.tmp`, `${JSON.stringify(snapshot, null, 2)}\n`)
    await rename(`${output}.tmp`, output)
    return { updated: true, count: Object.keys(stations).length }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const result = await updateSaleTimes({ force: process.argv.includes('--force') })
    console.log(`Official sale schedule: ${result.updated ? 'updated' : 'current'}, ${result.count} stations.`)
}
