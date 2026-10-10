import test from 'node:test'
import assert from 'node:assert/strict'

let requestHandler
const cancellations = []
globalThis.window = {
    CRTicketAndroid: {
        configuration: () => JSON.stringify({ backend: 'phone' }),
        request: (...args) => requestHandler(...args),
        cancel: id => cancellations.push(id),
    },
}
const { nativeRequest } = await import('./platform.js')
const { nativeFetch } = await import('./railway/nativeFetch.js')

test('phone HTTP bridge returns upstream JSON responses', async () => {
    requestHandler = (id, method, path) => {
        assert.equal(method, 'GET')
        assert.equal(path, 'https://search.12306.cn/search/v1/train/search?keyword=G1')
        queueMicrotask(() => window.crticketApiResult(id, {
            status: 200, body: '{"data":[]}', contentType: 'application/json',
        }))
    }
    const response = await nativeFetch('https://search.12306.cn/search/v1/train/search?keyword=G1')
    assert.deepEqual(await response.json(), { data: [] })
})

test('binary chunks reassemble without corrupting archive bytes', async () => {
    const first = Uint8Array.from([0, 128, 255])
    const second = Uint8Array.from([240, 31, 140, 255])
    requestHandler = id => queueMicrotask(() => {
        window.crticketBinaryChunk(id, { chunk: Buffer.from(first).toString('base64') })
        window.crticketBinaryChunk(id, { chunk: Buffer.from(second).toString('base64') })
        window.crticketApiResult(id, { status: 200, binary: true, body: '' })
    })
    const response = await nativeFetch('http://down.lltskb.com/an.db')
    assert.deepEqual(new Uint8Array(await response.arrayBuffer()), new Uint8Array([...first, ...second]))
})

test('timeout and abort cancel native reads and discard late results', async () => {
    let staleId
    requestHandler = id => { staleId = id }
    await assert.rejects(nativeRequest('GET', 'https://kyfw.12306.cn/otn/leftTicket/init', '', {
        timeout: 10,
    }), error => error.code === 'ECONNABORTED')
    assert.equal(cancellations.at(-1), staleId)
    window.crticketApiResult(staleId, { status: 200, body: '{}' })
    const controller = new AbortController()
    const pending = nativeFetch('http://down.lltskb.com/an.db', { signal: controller.signal })
    controller.abort()
    await assert.rejects(pending, error => error.code === 'ERR_CANCELED')
})
