import test from 'node:test'
import assert from 'node:assert/strict'
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
