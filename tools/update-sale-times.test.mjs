import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { updateSaleTimes } from './update-sale-times.mjs'

const html = await readFile(new URL('../app/src/test/resources/sale-announcement.html', import.meta.url), 'utf8')

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
