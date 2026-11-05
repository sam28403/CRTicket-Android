import { timetableAutoUpdate } from '../networkFeatures.js'
import { nativeFetch } from './nativeFetch.js'
import { parseLltArchive } from './lltParser.js'
import { readArchive, writeArchive, clearArchive } from './archiveStore.js'

const base = 'http://down.lltskb.com'
const fallback = 'http://223.107.87.50:8011/an.db'
const maxBytes = 16 * 1024 * 1024

export async function decodeArchive(bytes) {
    if (!globalThis.Worker) return parseLltArchive(bytes)
    return new Promise((resolve, reject) => {
        const worker = new Worker(new URL('./lltWorker.js', import.meta.url), { type: 'module' })
        worker.onmessage = ({ data }) => {
            worker.terminate()
            if (data.error) reject(new Error(data.error))
            else resolve(data.database)
        }
        worker.onerror = event => {
            worker.terminate()
            reject(new Error(event.message || '时刻表解析失败'))
        }
        const copy = bytes.slice()
        worker.postMessage(copy, [copy.buffer])
    })
}

export function createLltLoader(
    fetchImpl = nativeFetch,
    store = { readArchive, writeArchive, clearArchive },
    bundledArchive,
    options = {},
) {
    const autoUpdate = options.autoUpdate || timetableAutoUpdate
    const publishVersion = options.publish || (database => globalThis.CRTicketAndroid?.lltVersion?.(database.version))
    let published
    const publish = database => {
        if (published === database) return
        publishVersion(database)
        published = database
    }
    let cached
    let imported = false
    let checkedAt = 0
    let initialization
    let pending
    let clearing
    let generation = 0
    let refreshController
    let revision = 0
    Object.defineProperty(load, 'revision', { get: () => revision })

    async function download(url, loadSignal, timeout) {
        const signal = AbortSignal.any([loadSignal, AbortSignal.timeout(timeout)])
        signal.throwIfAborted()
        const response = await fetchImpl(url, { signal, timeout })
        if (!response.ok) throw new Error('路路通数据下载失败')
        const bytes = new Uint8Array(await response.arrayBuffer())
        if (bytes.length > maxBytes) throw new Error('路路通数据包过大')
        return bytes
    }

    async function refresh() {
        if (!autoUpdate()) return cached

        refreshController = new AbortController()
        const loadSignal = AbortSignal.any([refreshController.signal, AbortSignal.timeout(90000)])
        let manifest
        try {
            manifest = new TextDecoder().decode(await download(`${base}/android.ver`, loadSignal, 15000))
                .replace(/^\uFEFF/, '').replace('"name": 应用宝"', '"name": "应用宝"')
        } catch {
            manifest = null
        }
        const version = manifest ? JSON.parse(manifest).version?.data : null
        if (manifest && (typeof version !== 'string' || !/^\d{8}$/.test(version))) {
            throw new Error('路路通版本响应异常')
        }
        if (!cached || !version || cached.version !== version) {
            let bytes
            if (manifest) {
                try {
                    bytes = await download(`${base}/an.db`, loadSignal, 60000)
                } catch {
                    bytes = await download(fallback, loadSignal, 60000)
                }
            } else {
                bytes = await download(fallback, loadSignal, 60000)
            }
            const next = await decodeArchive(bytes)
            if (version && next.version !== version) {
                throw new Error('路路通正在更新数据，请稍后重试')
            }
            loadSignal.throwIfAborted()
            if (!autoUpdate()) return cached
            cached = next
            imported = false
            revision++
            publish(cached)
            checkedAt = Date.now()
            await store.writeArchive({ bytes, checkedAt }).catch(() => {})
        } else {
            checkedAt = Date.now()
        }
        return cached
    }

    async function load() {
        await clearing
        const currentGeneration = generation
        if (!initialization) {
            initialization = (async () => {
                const saved = await store.readArchive().catch(() => null)
                if (saved) {
                    try {
                        cached = await decodeArchive(new Uint8Array(saved.bytes))
                        imported = saved.imported === true
                        checkedAt = saved.checkedAt
                    } catch {
                        cached = null
                    }
                }
                if (!cached && bundledArchive) {
                    try {
                        const bytes = await bundledArchive()
                        if (bytes) cached = await decodeArchive(bytes)
                    } catch {
                        // A missing or damaged bundle falls back to the online loader.
                    }
                }
            })()
        }
        await initialization
        if (currentGeneration !== generation) throw new DOMException('Cache cleared', 'AbortError')
        if (cached) publish(cached)
        if (!autoUpdate()) {
            if (!cached) throw new Error('本机暂无路路通数据库')
            return cached
        }
        if (cached && Date.now() - checkedAt < 3600000) return cached
        if (!pending) {
            // Failed refreshes do not advance checkedAt; the next query retries immediately.
            pending = refresh().finally(() => { pending = null })
            if (cached) pending.catch(() => {})
        }
        // An already downloaded database is immediately available while checking updates.
        return cached || pending
    }

    // Quiz counts require a successful online version check, never stale fallback.
    load.fresh = async function fresh() {
        await load()
        return autoUpdate() ? pending || refresh() : cached
    }

    load.pauseUpdates = async function pauseUpdates() {
        refreshController?.abort(new DOMException('Updates disabled', 'AbortError'))
        await Promise.allSettled([pending])
    }

    if (typeof __CRTICKET_CI__ === 'undefined' || __CRTICKET_CI__) {
        load.importArchive = async function importArchive(bytes) {
            if (autoUpdate()) throw new Error('请先关闭自动更新')
            const next = await decodeArchive(bytes)
            await clearing
            await load.pauseUpdates()
            await Promise.allSettled([initialization])
            if (autoUpdate()) throw new Error('请先关闭自动更新')
            // Commit before replacing memory so a failed save preserves the current database.
            await store.writeArchive({ bytes, checkedAt: 0, imported: true })
            generation++
            cached = next
            imported = true
            revision++
            checkedAt = 0
            initialization = Promise.resolve()
            publish(next)
            return next
        }

    }

    load.clear = function clear() {
        if (clearing) return clearing
        generation++
        revision++
        refreshController?.abort(new DOMException('Cache cleared', 'AbortError'))
        clearing = (async () => {
            // An initialization or download must not write the archive back after cleanup.
            await Promise.allSettled([initialization, pending])
            if (imported && cached) {
                await store.clearArchive()
                initialization = Promise.resolve()
                pending = undefined
                return
            }
            cached = undefined
            imported = false
            published = undefined
            checkedAt = 0
            initialization = undefined
            pending = undefined
            await store.clearArchive()
        })().finally(() => { clearing = undefined })
        return clearing
    }
    return load
}

async function readBundledArchive() {
    const response = await fetch('/offline/an.db', { cache: 'no-store' })
    if (!response.ok) return null
    return new Uint8Array(await response.arrayBuffer())
}

export const loadLltDatabase = createLltLoader(undefined, undefined, readBundledArchive)
