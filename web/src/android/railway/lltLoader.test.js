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

function archive(version = '20261004') {
    const files = Object.fromEntries(Array.from({ length: 20 }, (_, i) => [`T${i}.dat`, new Uint8Array()]))
    files['t.i'] = dictionary(['G1'])
    files['s.i'] = dictionary(['北京南', '上海虹桥'])
    files['ver.txt'] = Buffer.from(version)
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

test('cleaning a protected import preserves its parsed database and publishes the version once', async () => {
    let reads = 0
    let publishes = 0
    const load = createLltLoader(undefined, {
        readArchive: async () => { reads++; return { bytes: archive(), imported: true } },
        clearArchive: async () => {},
    }, undefined, { autoUpdate: () => false, publish: () => { publishes++ } })
    const first = await load()
    await load()
    await load.clear()
    assert.equal(await load(), first)
    assert.equal(reads, 1)
    assert.equal(publishes, 1)
})

test('network master switch prevents automatic timetable checks while preserving offline data', async () => {
    const original = globalThis.CRTicketDeveloper
    try {
        globalThis.CRTicketDeveloper = { networkEnabled: () => false, lltAutoUpdate: () => true }
        const load = createLltLoader(() => assert.fail('Network switch is off'), {
            readArchive: async () => ({ bytes: archive('20111102'), checkedAt: 0 }),
        })
        assert.equal((await load()).version, '20111102')
        assert.equal((await load.fresh()).version, '20111102')
    } finally {
        if (original === undefined) delete globalThis.CRTicketDeveloper
        else globalThis.CRTicketDeveloper = original
    }
})

test('manual mode never downloads, including fresh and restart after cache cleaning', async () => {
    let saved = { bytes: archive('20111102'), checkedAt: 0, imported: true }
    const store = {
        readArchive: async () => saved,
        writeArchive: async value => { saved = value },
        clearArchive: async () => { if (!saved?.imported) saved = null },
    }
    const options = { autoUpdate: () => false }
    const offline = () => assert.fail('Manual mode must not use the network')
    const load = createLltLoader(offline, store, undefined, options)
    assert.equal((await load.fresh()).version, '20111102')
    await load.clear()
    assert.equal((await load()).version, '20111102')
    const restarted = createLltLoader(offline, store, undefined, options)
    assert.equal((await restarted()).version, '20111102')
})

test('validated imports replace the active archive and publish the actual version', async () => {
    let saved = { bytes: archive(), checkedAt: 0 }
    let published
    const load = createLltLoader(undefined, {
        readArchive: async () => saved,
        writeArchive: async value => { saved = value },
    }, undefined, { autoUpdate: () => false, publish: value => { published = value.version } })
    await load()
    await load.importArchive(archive('20111102'))
    assert.equal((await load()).version, '20111102')
    assert.equal(saved.imported, true)
    assert.equal(published, '20111102')
    const original = saved
    await assert.rejects(load.importArchive(new Uint8Array([1, 2, 3])))
    assert.equal(saved, original)
    assert.equal((await load()).version, '20111102')
})

test('failed persistence preserves the active database, and automatic mode refuses imports', async () => {
    let enabled = false
    const load = createLltLoader(undefined, {
        readArchive: async () => ({ bytes: archive(), checkedAt: 0 }),
        writeArchive: async () => { throw new Error('Storage full') },
    }, undefined, { autoUpdate: () => enabled })
    await load()
    await assert.rejects(load.importArchive(archive('20111102')), /Storage full/)
    assert.equal((await load()).version, '20261004')
    enabled = true
    await assert.rejects(load.importArchive(archive('20111102')), /关闭自动更新/)
})

test('an in-flight download cannot overwrite a manual import after updates are disabled', async () => {
    let enabled = true
    let release
    let downloading
    const started = new Promise(resolve => { downloading = resolve })
    const gate = new Promise(resolve => { release = resolve })
    let saved = { bytes: archive('20261003'), checkedAt: 0 }
    const load = createLltLoader(async url => {
        if (url.endsWith('android.ver')) return new Response('{"version":{"data":"20261004"}}')
        downloading()
        await gate
        return new Response(archive())
    }, {
        readArchive: async () => saved,
        writeArchive: async value => { saved = value },
    }, undefined, { autoUpdate: () => enabled })
    await load()
    await started
    enabled = false
    const imported = load.importArchive(archive('20111102'))
    release()
    await imported
    assert.equal((await load()).version, '20111102')
    assert.equal(saved.imported, true)
})

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

test('cleanup removes the persisted archive and forces the next query to download', async () => {
    let saved = { bytes: archive(), checkedAt: Date.now() }
    let requests = 0
    const load = createLltLoader(async url => {
        requests++
        return new Response(url.endsWith('android.ver')
            ? '{"version":{"data":"20261004"}}' : archive())
    }, {
        readArchive: async () => saved,
        writeArchive: async value => { saved = value },
        clearArchive: async () => { saved = undefined },
    })
    const first = await load()
    assert.equal(requests, 0)
    await load.clear()
    assert.equal(saved, undefined)
    const next = await load()
    assert.notEqual(next, first)
    assert.equal(requests, 2)
    assert.ok(saved.bytes.byteLength > 0)
})

test('cleanup waits for initialization and prevents an old read from restoring memory', async () => {
    let resolveRead
    let saved = { bytes: archive(), checkedAt: Date.now() }
    const load = createLltLoader(() => assert.fail('No download expected'), {
        readArchive: () => new Promise(resolve => { resolveRead = resolve }),
        writeArchive: async () => {},
        clearArchive: async () => { saved = undefined },
    })
    const loading = load()
    const rejected = assert.rejects(loading, { name: 'AbortError' })
    await delay(0)
    const clearing = load.clear()
    resolveRead(saved)
    await Promise.all([rejected, clearing])
    assert.equal(saved, undefined)
})

test('cleanup aborts a download and does not let it repopulate the archive', async () => {
    let started
    const downloading = new Promise(resolve => { started = resolve })
    let saved
    const load = createLltLoader(async (url, options) => {
        if (url.endsWith('android.ver')) {
            return new Response('{"version":{"data":"20261004"}}')
        }
        started()
        return new Promise((resolve, reject) => {
            if (options.signal.aborted) reject(options.signal.reason)
            else options.signal.addEventListener('abort', () => reject(options.signal.reason), { once: true })
        })
    }, {
        readArchive: async () => null,
        writeArchive: async value => { saved = value },
        clearArchive: async () => { saved = undefined },
    })
    const rejected = assert.rejects(load(), { name: 'AbortError' })
    await downloading
    await Promise.all([load.clear(), rejected])
    assert.equal(saved, undefined)
})


test('quiz fresh load rejects failed online checks even with a usable cached archive', async () => {
    let online = false
    const load = createLltLoader(async url => {
        if (!online) throw new Error('offline')
        return new Response(url.endsWith('android.ver')
            ? '{"version":{"data":"20261004"}}' : archive())
    }, { readArchive: async () => ({ bytes: archive(), checkedAt: Date.now() }),
        writeArchive: async () => {} })
    assert.equal((await load()).version, '20261004')
    await assert.rejects(load.fresh(), /offline/)
    online = true
    assert.equal((await load.fresh()).version, '20261004')
})


test('bundled timetable works on first launch while the network is unavailable', async () => {
    let reads = 0
    const load = createLltLoader(async () => { throw new Error('Offline') }, emptyStore,
        async () => { reads++; return archive() })
    assert.equal((await load()).version, '20261004')
    assert.equal((await load()).trains.get('G1').stops.length, 2)
    assert.equal(reads, 1)
    await assert.rejects(load.fresh(), /Offline/)
})

test('saved phone archive takes priority over the bundled snapshot', async () => {
    const load = createLltLoader(() => assert.fail('Fresh cache needs no download'), {
        readArchive: async () => ({ bytes: archive(), checkedAt: Date.now() }),
    }, () => assert.fail('Existing data must not be replaced by the bundle'))
    assert.equal((await load()).version, '20261004')
})

test('corrupt saved cache falls back to the bundle and clearing restores it', async () => {
    let reads = 0
    const load = createLltLoader(async () => { throw new Error('Offline') }, {
        readArchive: async () => ({ bytes: new Uint8Array([1]), checkedAt: 0 }),
        clearArchive: async () => {},
    }, async () => { reads++; return archive() })
    assert.equal((await load()).version, '20261004')
    await load.clear()
    assert.equal((await load()).version, '20261004')
    assert.equal(reads, 2)
})

test('invalid bundle falls back to a successful online download', async () => {
    const load = createLltLoader(async url => new Response(url.endsWith('android.ver')
        ? '{"version":{"data":"20261004"}}' : archive()), emptyStore,
    async () => new Uint8Array([1]))
    assert.equal((await load()).version, '20261004')
})


test('bundled snapshot is immediately available while a newer archive is persisted', async () => {
    let saved
    const load = createLltLoader(async url => new Response(url.endsWith('android.ver')
        ? '{"version":{"data":"20261004"}}' : archive()), {
        readArchive: async () => null,
        writeArchive: async value => { saved = value },
    }, async () => archive('20261003'))
    assert.equal((await load()).version, '20261003')
    assert.equal((await load.fresh()).version, '20261004')
    assert.ok(saved.bytes.length > 0)
    assert.equal((await load()).version, '20261004')
})
