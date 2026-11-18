import assert from 'node:assert/strict'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { validListDate } from '../web/src/android/railway/lltList.js'

const base = 'https://api.raillog.top/api/train-timetables'
const baseRequestSpacingMs = Number(process.env.RAILLOG_REQUEST_SPACING_MS ?? 20)
let requestSpacingMs = baseRequestSpacingMs
const workers = Number(process.env.RAILLOG_WORKERS ?? 32)
const maxWorkers = Number(process.env.RAILLOG_MAX_WORKERS ?? Math.max(workers, Math.min(workers + 4, 16)))
assert(Number.isInteger(baseRequestSpacingMs) && baseRequestSpacingMs > 0, 'RAILLOG_REQUEST_SPACING_MS 必须是正整数')
assert(Number.isInteger(workers) && workers > 0, 'RAILLOG_WORKERS 必须是正整数')
assert(Number.isInteger(maxWorkers) && maxWorkers >= workers, 'RAILLOG_MAX_WORKERS 必须不小于 RAILLOG_WORKERS')
let nextRequestAt = 0
let retryAfterAt = 0
let transientErrors = 0
let consecutiveSuccesses = 0
const [version, directory, ...requestedCodes] = process.argv.slice(2)
if (!directory || !/^\d{4}\.\d{2}\.\d{2}$/.test(version)
    || !validListDate(version.replaceAll('.', '-'))) {
    throw new Error('用法：node tools/download-raillog-timetable.mjs <YYYY.MM.DD> <目录> [车次...]')
}

async function request(path, params) {
    const url = new URL(base + path)
    url.search = new URLSearchParams(params)
    for (let attempt = 0; ; attempt++) {
        while (Math.max(nextRequestAt, retryAfterAt) > Date.now()) {
            await delay(Math.max(nextRequestAt, retryAfterAt) - Date.now())
        }
        nextRequestAt = Date.now() + requestSpacingMs
        let response
        try {
            response = await fetch(url, { signal: AbortSignal.timeout(20000) })
            if (response.ok) {
                const result = await response.json()
                consecutiveSuccesses++
                requestSpacingMs = Math.max(baseRequestSpacingMs, Math.floor(requestSpacingMs * 0.9))
                if (consecutiveSuccesses >= 8 && requestSpacingMs > baseRequestSpacingMs) {
                    requestSpacingMs = baseRequestSpacingMs
                    consecutiveSuccesses = 0
                    console.log(`接口恢复稳定，请求间隔恢复为 ${requestSpacingMs}ms`)
                }
                return result
            }
        } catch (error) {
            if (attempt >= 3) throw error
            transientErrors++
            consecutiveSuccesses = 0
            requestSpacingMs = Math.min(2000, Math.ceil(requestSpacingMs * 1.25))
            console.warn(
                `RailLog 网络错误 ${error.cause?.code || error.name}，间隔调整为 ${requestSpacingMs}ms`,
            )
            retryAfterAt = Math.max(retryAfterAt, Date.now() + 1500 * 2 ** attempt)
        }
        const status = response?.status
        if ((status && status !== 429 && status < 500) || attempt >= 3) {
            throw new Error(`RailLog HTTP ${status}：${url.pathname}?${url.searchParams}`)
        }
        if (status) {
            transientErrors++
            consecutiveSuccesses = 0
            requestSpacingMs = Math.min(2000, Math.ceil(requestSpacingMs * 1.5))
            console.warn(
                `RailLog HTTP ${status}，间隔调整为 ${requestSpacingMs}ms`,
            )
        }
        const retryAfter = response?.headers.get('retry-after')
        const retryAt = /^\d+$/.test(retryAfter)
            ? Date.now() + Number(retryAfter) * 1000
            : Date.parse(retryAfter) || 0
        retryAfterAt = Math.max(retryAfterAt, retryAt, Date.now() + 2000 * 2 ** attempt)
    }
}

async function listCodes() {
    const codes = new Set()
    for (const prefix of '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ') {
        const result = await request('/search', { version, trainNumber: prefix })
        assert.equal(result.version, version)
        for (const row of result.trains) {
            assert(typeof row.train_codes === 'string' && row.train_codes.trim(), '缺少规范车次号')
            codes.add(row.train_codes)
        }
    }
    assert(codes.size > 0, '搜索未返回车次')
    return [...codes].sort()
}

function sameTrainCodes(left, right) {
    const normalize = value => value.split('/').sort().join('/')
    return normalize(left) === normalize(right)
}

async function loadTrain(code, cache) {
    const filename = join(cache, Buffer.from(code).toString('hex') + '.json')
    let result
    try {
        result = JSON.parse(await readFile(filename, 'utf8'))
    } catch (error) {
        if (error.code === 'ENOENT') {
            result = await request('', { version, trainNumber: code })
        } else if (error instanceof SyntaxError) {
            console.warn(`${code}：缓存 JSON 无效，删除后重新下载`)
            await rm(filename, { force: true })
            result = await request('', { version, trainNumber: code })
        } else {
            throw error
        }
    }
    assert.equal(result.version, version, `${code}：快照版本不匹配`)
    assert(Array.isArray(result.stops) && result.stops.length, `${code}：没有停站数据`)
    if (!requestedCodes.length) {
        assert(
            sameTrainCodes(result.train_codes, code),
            `${code}：详情与搜索清单的规范车次号不同（${result.train_codes}）`,
        )
    }
    await writeFile(filename, JSON.stringify(result), { flag: 'wx' }).catch(error => {
        if (error.code !== 'EEXIST') throw error
    })
    return result
}

const cache = join(directory, `cache-${version}`)
await mkdir(cache, { recursive: true })
const versions = await request('/versions')
assert(versions.includes(version), `RailLog 没有 ${version} 快照`)
const codes = requestedCodes.length ? [...new Set(requestedCodes)] : await listCodes()
console.log(`${version}：${codes.length} 个车次待核对`)
const results = new Array(codes.length)
let next = 0
let completed = 0
let activeWorkers = 0
let targetWorkers = workers
let successesSinceScale = 0
let failed
async function downloadTrains() {
    while (!failed) {
        while (!failed && activeWorkers >= targetWorkers && next < codes.length) await delay(50)
        if (failed || next >= codes.length) return
        const index = next++
        activeWorkers++
        try {
            results[index] = await loadTrain(codes[index], cache)
        } catch (error) {
            failed ??= error
            return
        } finally {
            activeWorkers--
        }
        completed++
        successesSinceScale++
        if (successesSinceScale >= 500) {
            if (transientErrors === 0 && targetWorkers < maxWorkers) {
                targetWorkers++
                console.log(`接口稳定，下载并发提升至 ${targetWorkers}/${maxWorkers}`)
            } else if (transientErrors >= 3 && targetWorkers > workers) {
                targetWorkers--
                console.warn(`近期接口重试较多，并发回调至 ${targetWorkers}/${maxWorkers}`)
            }
            successesSinceScale = 0
            transientErrors = 0
        }
        if (completed % 100 === 0) console.log(`已核对 ${completed}/${codes.length} 个车次`)
    }
}
await Promise.all(Array.from({ length: maxWorkers }, downloadTrains))
if (failed) throw failed
const trains = new Map(results.map(train => [train.train_codes, train]))
const output = join(directory, requestedCodes.length ? `${version}-sample.json` : `${version}.json`)
try {
    const existing = JSON.parse(await readFile(output, 'utf8'))
    assert.equal(existing.version, version)
    assert.equal(existing.trains.length, trains.size)
} catch (error) {
    if (error.code !== 'ENOENT') throw error
    await writeFile(output, JSON.stringify({ version, trains: [...trains.values()] }), { flag: 'wx' })
}
console.log(JSON.stringify({ output, trains: trains.size }))
