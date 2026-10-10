import { nativeRequest } from '../platform.js'

export async function nativeFetch(url, options = {}) {
    const result = await nativeRequest('GET', String(url), '', {
        timeout: 45000,
        signal: options.signal,
    })
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
