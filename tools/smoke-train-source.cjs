const assert = require('node:assert/strict')
const { assetServer, getPlaywright } = require('./browser-support.cjs')
const { installTrainQueryFixture } = require('./train-query-fixture.cjs')

async function main() {
    const { server, address } = await assetServer()
    const browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
    try {
        const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        await installTrainQueryFixture(page)
        const date = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
        const online = () => page.getByRole('button', { name: '切换为在线查询', exact: true })
        const offline = () => page.getByRole('button', { name: '切换为路路通离线', exact: true })
        const result = () => page.locator('.result-title-row strong')
        await page.goto(`${address}/#/train?train=G97&date=${date}`)
        await result().waitFor()
        await offline().click()
        await online().waitFor()
        await result().waitFor()
        assert.match(page.url(), /source=llt/)
        for (const width of [320, 390, 800, 1200]) {
            await page.setViewportSize({ width, height: 844 })
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
        }
        await page.setViewportSize({ width: 390, height: 844 })
        const queries = await page.evaluate(() => window.onlineQueries)
        await page.getByRole('button', { name: '车站大屏', exact: true }).click()
        await page.getByRole('heading', { name: '车站大屏', exact: true }).waitFor()
        await page.goBack()
        await result().waitFor()
        assert.equal(await page.evaluate(() => window.onlineQueries), queries)

        const nextDate = new Date(Date.parse(`${date}T00:00:00Z`) + 86400000).toISOString().slice(0, 10)
        await page.goto(`${address}/#/train?train=G100%2FG97B&date=${nextDate}&source=llt`)
        await result().waitFor()
        await page.evaluate(() => { window.holdOnline = true })
        await online().click()
        await page.waitForFunction(() => window.heldQueries.length === 1)
        await offline().click()
        await result().waitFor()
        assert.equal(await page.evaluate(() =>
            window.cancelledQueries.includes(window.heldQueries[0])), true)
        await page.evaluate(() => { window.holdOnline = false })

        await page.goto(`${address}/#/train?train=G100%2FG97B&date=${date}&source=llt`)
        await result().waitFor()
        await online().click()
        await result().waitFor()
        assert.ok(page.url().includes(`date=${date}`))
        assert.match(page.url(), /train=G100(?:&|$)/)
        await offline().click()
        await result().waitFor()
        const restoredQuery = new URLSearchParams(new URL(page.url()).hash.split('?')[1])
        assert.equal(restoredQuery.get('train'), 'G100/G97B')

        await page.goto(`${address}/#/train?train=G97&record=G100%2FG97B&date=${date}`)
        await result().waitFor()
        for (let cycle = 0; cycle < 2; cycle++) {
            assert.equal(await page.locator('.query-form input').first().inputValue(), 'G97')
            assert.match(await page.locator('.result-title-row strong').textContent(), /^\s*G97\s/)
            await offline().click()
            await online().waitFor()
            await result().waitFor()
            const selected = new URLSearchParams(new URL(page.url()).hash.split('?')[1])
            assert.equal(selected.get('train'), 'G97')
            assert.equal(selected.get('record'), 'G100/G97B')
            assert.match(await page.locator('.result-title-row strong').textContent(), /^\s*G97\s/)
            await online().click()
            await offline().waitFor()
            await result().waitFor()
        }
        assert.deepEqual(errors, [])
        console.log('PASS: source switching, selected segment preservation, offline aliases, pending query cancellation, complete record restoration, cached results and 320–1200px layout.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
