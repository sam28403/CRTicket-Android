import assert from 'node:assert/strict'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { validListDate } from '../web/src/android/railway/lltList.js'

const base = 'https://api.raillog.top/api/train-timetables'
const baseRequestSpacingMs = Number(process.env.RAILLOG_REQUEST_SPACING_MS ?? 50)
const requestTimeoutMs = Number(process.env.RAILLOG_REQUEST_TIMEOUT_MS ?? 30000)
let requestSpacingMs = baseRequestSpacingMs
const workers = Number(process.env.RAILLOG_WORKERS ?? 12)
const minWorkers = Number(process.env.RAILLOG_MIN_WORKERS ?? 4)
const maxWorkers = Number(process.env.RAILLOG_MAX_WORKERS ?? Math.max(workers, 64))
const maxRetries = Number(process.env.RAILLOG_MAX_RETRIES ?? 8)
const transientErrorThreshold = Number(process.env.RAILLOG_TRANSIENT_ERROR_THRESHOLD ?? 3)
const maxNormalRequestSpacingMs = 200
const persistentFailureSpacingMs = 2000
assert(Number.isInteger(baseRequestSpacingMs) && baseRequestSpacingMs > 0, 'RAILLOG_REQUEST_SPACING_MS 必须是正整数')
assert(Number.isInteger(requestTimeoutMs) && requestTimeoutMs > 0, 'RAILLOG_REQUEST_TIMEOUT_MS 必须是正整数')
assert(Number.isInteger(workers) && workers > 0, 'RAILLOG_WORKERS 必须是正整数')
assert(Number.isInteger(minWorkers) && minWorkers > 0 && minWorkers <= workers, 'RAILLOG_MIN_WORKERS 必须在 1 与 RAILLOG_WORKERS 之间')
assert(Number.isInteger(maxWorkers) && maxWorkers >= workers, 'RAILLOG_MAX_WORKERS 必须不小于 RAILLOG_WORKERS')
assert(Number.isInteger(maxRetries) && maxRetries >= 1, 'RAILLOG_MAX_RETRIES 必须是正整数')
assert(Number.isInteger(transientErrorThreshold) && transientErrorThreshold >= 2, 'RAILLOG_TRANSIENT_ERROR_THRESHOLD 必须至少为 2')
let nextRequestAt = 0
let serverRetryAfterAt = 0
let successesSinceRecovery = 0
let errorsSinceRecovery = 0
let consecutiveTransientErrors = 0
let lastSuccessfulResponseAt = Date.now()
let targetWorkers = workers

function noteTransientError() {
    errorsSinceRecovery++
    consecutiveTransientErrors++
    successesSinceRecovery = 0
    if (errorsSinceRecovery % transientErrorThreshold !== 0) return
    const persistentFailure = consecutiveTransientErrors >= transientErrorThreshold * 3
        || Date.now() - lastSuccessfulResponseAt >= 30000
    const spacingLimit = persistentFailure
        ? persistentFailureSpacingMs
        : maxNormalRequestSpacingMs
    requestSpacingMs = Math.min(
        spacingLimit,
        Math.ceil(Math.max(baseRequestSpacingMs, requestSpacingMs) * 1.5),
    )
    const previousWorkers = targetWorkers
    targetWorkers = Math.max(minWorkers, Math.floor(targetWorkers / 2))
    console.warn(`累计 ${errorsSinceRecovery} 次请求错误，连续 ${consecutiveTransientErrors} 次未成功，并发由 ${previousWorkers} 调至 ${targetWorkers}/${maxWorkers}，间隔 ${requestSpacingMs}ms`)
}

function updateServerRetryAfter(response) {
    const retryAfter = response?.headers.get('retry-after')
    const retryAt = /^\d+$/.test(retryAfter)
        ? Date.now() + Number(retryAfter) * 1000
        : Date.parse(retryAfter) || 0
    serverRetryAfterAt = Math.max(serverRetryAfterAt, retryAt)
}

function retryBackoffMs(attempt) {
    const persistentFailure = consecutiveTransientErrors >= transientErrorThreshold * 3
        || Date.now() - lastSuccessfulResponseAt >= 30000
    const ceiling = persistentFailure
        ? Math.min(30000, 500 * 2 ** Math.min(attempt, 6))
        : maxNormalRequestSpacingMs
    return Math.floor(ceiling / 2 + Math.random() * ceiling / 2)
}
const [version, directory, ...requestedCodes] = process.argv.slice(2)
if (!directory || !/^\d{4}\.\d{2}\.\d{2}$/.test(version)
    || !validListDate(version.replaceAll('.', '-'))) {
    throw new Error('用法：node tools/download-raillog-timetable.mjs <YYYY.MM.DD> <目录> [车次...]')
}

async function request(path, params) {
    const url = new URL(base + path)
    url.search = new URLSearchParams(params)
    for (let attempt = 0; ; attempt++) {
        while (Math.max(nextRequestAt, serverRetryAfterAt) > Date.now()) {
            await delay(Math.max(nextRequestAt, serverRetryAfterAt) - Date.now())
        }
        nextRequestAt = Date.now() + requestSpacingMs
        let response
        try {
            response = await fetch(url, { signal: AbortSignal.timeout(requestTimeoutMs) })
            if (response.ok) {
                let result
                try {
                    result = await response.json()
                } catch (error) {
                    if (attempt >= maxRetries) throw error
                    noteTransientError()
                    console.warn(`RailLog HTTP ${response.status} 返回无效 JSON，退避后重试`)
                    updateServerRetryAfter(response)
                    await delay(retryBackoffMs(attempt))
                    continue
                }
                lastSuccessfulResponseAt = Date.now()
                consecutiveTransientErrors = 0
                if (requestSpacingMs > maxNormalRequestSpacingMs) {
                    requestSpacingMs = maxNormalRequestSpacingMs
                    console.log(`RailLog 请求成功，间隔恢复至 ${requestSpacingMs}ms`)
                }
                successesSinceRecovery++
                if (successesSinceRecovery >= 64) {
                    const previousSpacing = requestSpacingMs
                    requestSpacingMs = Math.max(
                        baseRequestSpacingMs,
                        Math.floor(requestSpacingMs * 0.8),
                    )
                    const previousWorkers = targetWorkers
                    targetWorkers = Math.min(maxWorkers, targetWorkers + 1)
                    errorsSinceRecovery = 0
                    successesSinceRecovery = 0
                    if (requestSpacingMs < previousSpacing) {
                        console.log(`接口连续成功，请求间隔恢复为 ${requestSpacingMs}ms`)
                    }
                    if (targetWorkers > previousWorkers) {
                        console.log(`接口连续成功，并发缓慢恢复至 ${targetWorkers}/${maxWorkers}`)
                    }
                }
                return result
            }
        } catch (error) {
            if (attempt >= maxRetries) throw error
            noteTransientError()
            console.warn(`RailLog 网络错误 ${error.cause?.code || error.name}，退避后重试`)
            const waitMs = retryBackoffMs(attempt)
            console.warn(`RailLog 请求退避 ${waitMs}ms`)
            await delay(waitMs)
            continue
        }
        const status = response?.status
        if ((status >= 400 && status < 500 && status !== 408 && status !== 429) || attempt >= maxRetries) {
            throw new Error(`RailLog HTTP ${status}：${url.pathname}?${url.searchParams}`)
        }
        if (status) {
            noteTransientError()
            console.warn(`RailLog HTTP ${status}，退避后重试`)
        }
        updateServerRetryAfter(response)
        await response?.body?.cancel().catch(() => {})
        const waitMs = retryBackoffMs(attempt)
        console.warn(`RailLog HTTP ${status} 请求退避 ${waitMs}ms`)
        await delay(waitMs)
    }
}

async function listCodes() {
    const codes = new Set()
    const prefixes = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    let nextPrefix = 0
    await Promise.all(Array.from({ length: Math.min(4, prefixes.length) }, async () => {
        while (nextPrefix < prefixes.length) {
            const prefix = prefixes[nextPrefix++]
            const result = await request('/search', { version, trainNumber: prefix })
            assert.equal(result.version, version)
            for (const row of result.trains) {
                assert(typeof row.train_codes === 'string' && row.train_codes.trim(), '缺少规范车次号')
                codes.add(row.train_codes)
            }
        }
    }))
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
    let downloaded = false
    try {
        result = JSON.parse(await readFile(filename, 'utf8'))
    } catch (error) {
        if (error.code === 'ENOENT') {
            result = await request('', { version, trainNumber: code })
            downloaded = true
        } else if (error instanceof SyntaxError) {
            console.warn(`${code}：缓存 JSON 无效，删除后重新下载`)
            await rm(filename, { force: true })
            result = await request('', { version, trainNumber: code })
            downloaded = true
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
    return { train: result, downloaded }
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
const startedAt = Date.now()
let lastProgressAt = startedAt
let downloadedCompleted = 0
let lastProgressDownloaded = 0
let activeWorkers = 0
let failed
async function downloadTrains() {
    while (!failed) {
        while (!failed && activeWorkers >= targetWorkers && next < codes.length) await delay(50)
        if (failed || next >= codes.length) return
        const index = next++
        activeWorkers++
        let loaded
        try {
            loaded = await loadTrain(codes[index], cache)
            results[index] = loaded.train
        } catch (error) {
            failed ??= error
            return
        } finally {
            activeWorkers--
        }
        completed++
        if (loaded.downloaded) {
            downloadedCompleted++
        }
        const now = Date.now()
        if (completed % 100 === 0 || now - lastProgressAt >= 30000) {
            const intervalSeconds = Math.max(0.001, (now - lastProgressAt) / 1000)
            const intervalRate = (downloadedCompleted - lastProgressDownloaded) / intervalSeconds
            const averageRate = downloadedCompleted / Math.max(0.001, (now - startedAt) / 1000)
            console.log(`${version}：已核对 ${completed}/${codes.length} 条，本次新下 ${downloadedCompleted} 条；当前 ${intervalRate.toFixed(2)} 条/秒，均值 ${averageRate.toFixed(2)} 条/秒，并发 ${targetWorkers}，间隔 ${requestSpacingMs}ms`)
            lastProgressAt = now
            lastProgressDownloaded = downloadedCompleted
        }
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
