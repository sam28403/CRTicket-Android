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
        const result = () => page.getByText('车站数量：3 站', { exact: false })
        await page.goto(`${address}/#/train?train=G97&date=${date}`)
        await result().waitFor()
        await offline().click()
        await online().waitFor()
        await result().waitFor()
        await page.getByText('车站数据：路路通', { exact: false }).waitFor()
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

        await page.evaluate(() => { window.holdOnline = true })
        await online().click()
        await page.waitForFunction(() => window.heldQueries.length === 1)
        await offline().click()
        await result().waitFor()
        assert.equal(await page.evaluate(() =>
            window.cancelledQueries.includes(window.heldQueries[0])), true)
        await page.evaluate(() => { window.holdOnline = false })

        await page.goto(`${address}/#/train?train=G100%2FG97B&date=2000-01-01&source=llt`)
        await result().waitFor()
        await online().click()
        await page.getByText('所选日期超出在线查询范围，已切换到今天', { exact: true }).waitFor()
        await result().waitFor()
        assert.ok(page.url().includes(`date=${date}`))
        assert.match(page.url(), /train=G100(?:&|$)/)
        await offline().click()
        await result().waitFor()
        const restoredQuery = new URLSearchParams(new URL(page.url()).hash.split('?')[1])
        assert.equal(restoredQuery.get('train'), 'G100/G97B')
        assert.deepEqual(errors, [])
        console.log('PASS: source switching, offline aliases, pending query cancellation, date reset, complete record restoration, cached results and 320–1200px layout.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
