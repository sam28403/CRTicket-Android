import { nativeRequest } from '../platform.js'

export async function nativeFetch(url, options = {}) {
    let result
    try {
        result = await nativeRequest('GET', String(url), '', {
            timeout: options.timeout ?? 45000,
            signal: options.signal,
        })
    } catch (error) {
        // Deadline aborts must surface as timeouts; only user aborts are silently cancelled.
        if (options.signal?.reason?.name === 'TimeoutError') {
            throw Object.assign(new Error('铁路服务请求超时'), { code: 'ECONNABORTED' })
        }
        throw error
    }
    if (result.error) throw new Error(result.error)
    let body = result.body
    if (result.binary) {
        body = Uint8Array.from(atob(body), character => character.charCodeAt(0))
    }
    return new Response(body, {
        status: result.status,
        headers: { 'Content-Type': result.contentType || 'application/octet-stream' },
    })
}
