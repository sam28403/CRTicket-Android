import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { updateSaleTimes } from './update-sale-times.mjs'

const html = await readFile(new URL('../app/src/test/resources/sale-announcement.html', import.meta.url), 'utf8')

test('the committed official snapshot can be packaged without downloading', async () => {
    const result = await updateSaleTimes({
        fetchHtml: async () => { throw new Error('Committed station data must match the bundled schedule') },
    })
    assert.equal(result.updated, false)
    assert.ok(result.count >= 2000)
})

test('station file changes refresh the full schedule, unchanged files make no requests', async () => {
    const workspace = await mkdtemp(path.join(tmpdir(), 'crticket-sale-sync-'))
    try {
        await mkdir(path.join(workspace, 'web/src'), { recursive: true })
        await mkdir(path.join(workspace, 'app/src/main/assets'), { recursive: true })
        const station = path.join(workspace, 'web/src/station_name.js')
        const output = path.join(workspace, 'app/src/main/assets/sale-times.json')
        await writeFile(station, 'version-one')
        let requests = 0
        const options = { workspace, fetchHtml: async () => { requests++; return html } }
        assert.equal((await updateSaleTimes(options)).count, 3206)
        assert.equal((await updateSaleTimes(options)).updated, false)
        assert.equal(requests, 1)
        const before = await readFile(output, 'utf8')
        await writeFile(station, 'version-two')
        await assert.rejects(updateSaleTimes({ workspace, fetchHtml: async () => '<html>Unavailable</html>' }))
        assert.equal(await readFile(output, 'utf8'), before)
        assert.equal((await updateSaleTimes(options)).updated, true)
        assert.equal(requests, 2)
        assert.notEqual(JSON.parse(await readFile(output, 'utf8')).stationHash, JSON.parse(before).stationHash)
    } finally {
        assert.equal(path.dirname(path.resolve(workspace)), path.resolve(tmpdir()))
        assert.ok(path.basename(workspace).startsWith('crticket-sale-sync-'))
        await rm(workspace, { recursive: true, force: true })
    }
})

test('LF and CRLF checkouts reuse the same official snapshot without network requests', async () => {
    const workspace = await mkdtemp(path.join(tmpdir(), 'crticket-sale-eol-'))
    try {
        await mkdir(path.join(workspace, 'web/src'), { recursive: true })
        await mkdir(path.join(workspace, 'app/src/main/assets'), { recursive: true })
        const station = path.join(workspace, 'web/src/station_name.js')
        const output = path.join(workspace, 'app/src/main/assets/sale-times.json')
        await writeFile(station, 'const stationNames = "北京|上海"\nexport default stationNames\n')
        await updateSaleTimes({ workspace, fetchHtml: async () => html })
        const snapshot = await readFile(output, 'utf8')
        const lf = await readFile(station, 'utf8')
        await writeFile(station, lf.replaceAll('\n', '\r\n'))
        const offline = async () => { throw new Error('Network must not be used for line-ending changes') }
        assert.equal((await updateSaleTimes({ workspace, fetchHtml: offline })).updated, false)
        assert.equal(await readFile(output, 'utf8'), snapshot)
        await writeFile(station, lf)
        assert.equal((await updateSaleTimes({ workspace, fetchHtml: offline })).updated, false)
        await assert.rejects(updateSaleTimes({ workspace, fetchHtml: offline, force: true }), /Network must not/)
        await writeFile(station, lf.replace('北京', '天津').replaceAll('\n', '\r\n'))
        await assert.rejects(updateSaleTimes({ workspace, fetchHtml: offline }), /Network must not/)
        assert.equal(await readFile(output, 'utf8'), snapshot)
    } finally {
        assert.equal(path.dirname(path.resolve(workspace)), path.resolve(tmpdir()))
        assert.ok(path.basename(workspace).startsWith('crticket-sale-eol-'))
        await rm(workspace, { recursive: true, force: true })
    }
})
