import { nativeFetch } from './nativeFetch.js'
import { parseLltArchive } from './lltParser.js'
import { readArchive, writeArchive } from './archiveStore.js'

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

export function createLltLoader(fetchImpl = nativeFetch, store = { readArchive, writeArchive }) {
    let cached
    let checkedAt = 0
    let initialization
    let pending

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
        const loadSignal = AbortSignal.timeout(90000)
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
            cached = next
            checkedAt = Date.now()
            await store.writeArchive({ bytes, checkedAt }).catch(() => {})
        } else {
            checkedAt = Date.now()
        }
        return cached
    }

    return async function load() {
        if (!initialization) {
            initialization = (async () => {
                const saved = await store.readArchive().catch(() => null)
                if (saved) {
                    try {
                        cached = await decodeArchive(new Uint8Array(saved.bytes))
                        checkedAt = saved.checkedAt
                    } catch {
                        cached = null
                    }
                }
            })()
        }
        await initialization
        if (cached && Date.now() - checkedAt < 3600000) return cached
        if (!pending) {
            // Failed refreshes do not advance checkedAt; the next query retries immediately.
            pending = refresh().finally(() => { pending = null })
            if (cached) pending.catch(() => {})
        }
        // An already downloaded database is immediately available while checking updates.
        return cached || pending
    }
}

export const loadLltDatabase = createLltLoader()
