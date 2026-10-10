import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'

const directory = await mkdtemp(path.join(tmpdir(), 'crticket-default-user-'))
process.env.DATABASE_PATH = path.join(directory, 'test.db')
const { default: app } = await import('./app.js')
const { default: db } = await import('./db/db.js')
const { getDefaultUser } = await import('./defaultUser.js')
const server = app.listen(0, '127.0.0.1')
await new Promise(resolve => server.once('listening', resolve))
const base = `http://127.0.0.1:${server.address().port}/api`

async function request(route, method = 'GET', body) {
    return fetch(base + route, {
        method,
        headers: { 'Content-Type': 'application/json', 'X-CRTicket-Request': '1' },
        body: body === undefined ? undefined : JSON.stringify(body),
    })
}

test('default owner exists once and records work without login cookies', async () => {
    const owner = getDefaultUser()
    assert.equal(owner.username, '默认用户')
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM users').get().n, 1)
    const session = await request('/user/session').then(response => response.json())
    assert.equal(session.user.id, owner.id)

    const ticket = {
        ticket_number: 'SINGLE-USER-TEST', train_no: 'G1',
        departure_station: '北京南', arrival_station: '上海虹桥',
        travel_date: '2026年10月10日', price: 553, distance: 1318,
    }
    const saved = await request('/ticket/add', 'POST', ticket)
    assert.equal((await saved.json()).success, true)
    const list = await request(`/ticket/list/${owner.id}`).then(response => response.json())
    assert.equal(list.length, 1)
    assert.equal(list[0].user_id, owner.id)

    const backup = await request('/user/import-backup', 'POST', { backup: [ticket] })
    assert.equal((await backup.json()).data.skipped, 1)
    const stats = await request('/user/statistics').then(response => response.json())
    assert.equal(stats.data.departures[0].count, 1)
    await request(`/ticket/delete/${list[0].id}`, 'DELETE')
    const remaining = await request(`/ticket/list/${owner.id}`).then(response => response.json())
    assert.equal(remaining.length, 0)
})

test('login, registration and account deletion APIs are absent', async () => {
    for (const route of ['/user/login', '/user/register', '/user/delete-account', '/user/confirm-delete']) {
        assert.equal((await request(route, 'POST', {})).status, 404)
    }
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM users').get().n, 1)
})

test.after(async () => {
    await new Promise(resolve => server.close(resolve))
    db.close()
    await rm(directory, { recursive: true, force: true })
})
