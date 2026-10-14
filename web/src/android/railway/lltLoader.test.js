import test from 'node:test'
import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'
import { zipSync } from 'fflate'
import { createLltLoader } from './lltLoader.js'

function dictionary(names) {
    const count = Buffer.alloc(2)
    count.writeUInt16BE(names.length)
    return Buffer.concat([count, ...names.map(name => {
        const bytes = Buffer.from(name)
        const length = Buffer.alloc(2)
        length.writeUInt16BE(bytes.length)
        return Buffer.concat([length, bytes])
    })])
}

function archive() {
    const files = Object.fromEntries(Array.from({ length: 20 }, (_, i) => [`T${i}.dat`, new Uint8Array()]))
    files['t.i'] = dictionary(['G1'])
    files['s.i'] = dictionary(['北京南', '上海虹桥'])
    files['ver.txt'] = Buffer.from('20261004')
    const record = Buffer.alloc(31)
    record.writeUInt16BE(2, 15)
    record.set([0, 0, 8, 0, 0, 0, 0], 17)
    record.set([0, 1, 12, 30, 0, 5, 43], 24)
    files['T0.dat'] = record
    return zipSync(files)
}

test('phone loader merges downloads and persists a reusable archive', async () => {
    let saved
    let requests = 0
    const store = {
        readArchive: async () => saved,
        writeArchive: async value => { saved = value },
    }
    const fetchImpl = async url => {
        requests++
        return new Response(url.endsWith('android.ver')
            ? '{"version":{"data":"20261004"}}' : archive())
    }
    const load = createLltLoader(fetchImpl, store)
    const [first, second] = await Promise.all([load(), load()])
    assert.equal(first, second)
    assert.equal(requests, 2)
    assert.equal(saved.bytes instanceof Uint8Array, true)
    const offline = createLltLoader(() => assert.fail('Fresh phone cache must work without network'), store)
    assert.equal((await offline()).trains.get('G1').stops[1].mileage, 1318)
})

test('official archive fallback works when the version service cannot be reached', async () => {
    const urls = []
    const load = createLltLoader(async url => {
        urls.push(url)
        if (url.endsWith('android.ver')) throw new Error('Offline')
        return new Response(archive())
    }, { readArchive: async () => null, writeArchive: async () => {} })
    assert.equal((await load()).version, '20261004')
    assert.deepEqual(urls, ['http://down.lltskb.com/android.ver', 'http://223.107.87.50:8011/an.db'])
})

const emptyStore = { readArchive: async () => null, writeArchive: async () => {} }

test('failed phone downloads retry immediately and merge concurrent retry requests', async () => {
    const urls = []
    let failing = true
    const load = createLltLoader(async url => {
        urls.push(url)
        if (failing) throw new DOMException('下载超时', 'TimeoutError')
        return new Response(url.endsWith('android.ver')
            ? '{"version":{"data":"20261004"}}' : archive())
    }, emptyStore)
    const failures = await Promise.allSettled([load(), load()])
    assert.ok(failures.every(result => result.status === 'rejected'))
    assert.equal(urls.length, 2)

    failing = false
    const [first, second] = await Promise.all([load(), load()])
    assert.equal(first.version, '20261004')
    assert.equal(first, second)
    assert.equal(await load(), first)
    assert.equal(urls.length, 4)
})

function delayedArchive(signal) {
    return new Response(new ReadableStream({
        async start(controller) {
            try {
                await delay(60, undefined, { signal })
                controller.enqueue(archive())
                controller.close()
            } catch {
                controller.error(signal.reason)
            }
        },
    }))
}

test('slow phone archive body uses its own download deadline after manifest completes', async context => {
    const timeout = AbortSignal.timeout
    context.mock.method(AbortSignal, 'timeout', milliseconds =>
        timeout(milliseconds === 15000 ? 20 : 2000))
    const load = createLltLoader(async (url, options) => {
        if (url.endsWith('android.ver')) {
            assert.equal(options.timeout, 15000)
            return new Response('{"version":{"data":"20261004"}}')
        }
        assert.equal(options.timeout, 60000)
        return delayedArchive(options.signal)
    }, emptyStore)
    assert.equal((await load()).version, '20261004')
})

test('whole phone load deadline aborts fallback body and allows the next query to retry', async context => {
    const timeout = AbortSignal.timeout
    context.mock.method(AbortSignal, 'timeout', milliseconds =>
        timeout(milliseconds === 90000 ? 20 : 2000))
    let slow = true
    let fallbackCalls = 0
    const load = createLltLoader(async (url, options) => {
        if (url.endsWith('android.ver')) {
            return new Response('{"version":{"data":"20261004"}}')
        }
        if (!slow) return new Response(archive())
        if (url.startsWith('http://down.lltskb.com')) throw new TypeError('fetch failed')
        fallbackCalls++
        return delayedArchive(options.signal)
    }, emptyStore)
    await assert.rejects(load(), { name: 'TimeoutError' })
    assert.equal(fallbackCalls, 1)
    slow = false
    assert.equal((await load()).version, '20261004')
})
