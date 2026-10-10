import assert from 'node:assert/strict'
import { test } from 'node:test'

process.env.DATABASE_PATH = ':memory:'
process.env.ALLOWED_ORIGINS = 'https://tickets.example'
const { default: app } = await import('./app.js')
const { default: db } = await import('./db/db.js')

test('single-user backend keeps origin checks, mutation validation and input limits', async t => {
    const server = app.listen(0, '127.0.0.1')
    await new Promise(resolve => server.once('listening', resolve))
    t.after(async () => {
        await new Promise(resolve => server.close(resolve))
        db.close()
    })
    const base = `http://127.0.0.1:${server.address().port}/api`
    const blocked = await fetch(base + '/user/session', {
        headers: { Origin: 'https://evil.example' },
    })
    assert.equal(blocked.status, 403)
    const noHeader = await fetch(base + '/ticket/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{}',
    })
    assert.equal(noHeader.status, 403)
    const invalid = await fetch(base + '/ticket/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CRTicket-Request': '1' },
        body: JSON.stringify({ price: -1 }),
    })
    assert.equal(invalid.status, 400)
    const session = await fetch(base + '/user/session')
    assert.equal(session.status, 200)
    assert.equal((await session.json()).user.username, '默认用户')
})
