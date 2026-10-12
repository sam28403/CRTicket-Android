const assert = require('node:assert/strict')
const { assetServer, getPlaywright } = require('./browser-support.cjs')
const { touchDrag, verticalPull } = require('./pull-refresh-touch.cjs')

const viewport = '.native-table-scroll .el-table-v2__body > div:first-child'

async function checkNativeSwipe(page, client, name) {
    const surface = page.locator(viewport).first()
    await surface.waitFor()
    await surface.evaluate(element => {
        window.scrollTo(0, element.getBoundingClientRect().top + window.scrollY - 100)
    })
    await surface.evaluate(element => { element.scrollTop = 1000 })
    await page.waitForTimeout(300)
    const box = await surface.boundingBox()
    const bottom = Math.min(box.y + box.height, 1000) - 30
    const top = Math.max(box.y, 0) + 30
    assert.ok(bottom - top > 100, `${name}: visible scrolling area`)
    const x = box.x + box.width / 2
    const start = bottom
    const distance = Math.min(300, bottom - top)
    const initialTop = await surface.evaluate(element => element.scrollTop)
    await page.evaluate(() => {
        window.touchWasBlocked = false
        const audit = event => {
            queueMicrotask(() => { window.touchWasBlocked ||= event.defaultPrevented })
        }
        window.addEventListener('touchmove', audit, { capture: true, passive: true })
        window.stopTouchAudit = () => window.removeEventListener('touchmove', audit, { capture: true })
    })
    await client.send('Input.dispatchTouchEvent', {
        type: 'touchStart', touchPoints: [{ x, y: start }],
    })
    await page.waitForTimeout(50)
    for (let index = 1; index <= 5; index++) {
        await new Promise(resolve => setTimeout(resolve, 20))
        await client.send('Input.dispatchTouchEvent', {
            type: 'touchMove', touchPoints: [{ x, y: start - distance * index / 5 }],
        })
    }
    await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    await page.waitForTimeout(350)
    const afterRelease = await surface.evaluate(element => element.scrollTop)
    assert.ok(afterRelease - initialTop > distance * 0.6,
        `${name}: browser should scroll the viewport (${initialTop} -> ${afterRelease})`)
    assert.equal(await page.evaluate(() => {
        window.stopTouchAudit()
        return window.touchWasBlocked
    }), false, `${name}: JavaScript must not intercept native scrolling`)
    assert.ok(await page.locator('.el-table-v2__row').count() < 80,
        `${name}: rows remain virtualized`)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    // OS fling velocity is verified with actual finger input in ListScrollTest.
    console.log(`${name}: native touch scrolled ${Math.round(afterRelease - initialTop)}px without preventDefault`)
    return surface
}

async function checkTopRefresh(page, client, surface) {
    // End any fling, then start from the top with a fresh finger gesture.
    await page.waitForTimeout(1000)
    await page.evaluate(() => window.scrollTo(0, 0))
    await surface.evaluate(element => { element.scrollTop = 0 })
    await page.waitForTimeout(150)
    const box = await surface.boundingBox()
    const x = box.x + box.width / 2
    const y = Math.max(box.y, 0) + 30
    await page.evaluate(() => { window.scrollTestSentinel = true })
    await touchDrag(page, client, verticalPull(x, y, 20), { finish: false })
    await page.getByRole('status', { name: '松开刷新', exact: true }).waitFor()
    assert.equal(await page.evaluate(() => window.scrollTestSentinel), true)
    await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    await page.waitForFunction(() => !window.scrollTestSentinel)
}

async function main() {
    const { server, address } = await assetServer()
    const browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
    try {
        const options = {
            viewport: { width: 390, height: 1000 }, isMobile: true, hasTouch: true,
        }
        let page = await browser.newPage(options)
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        const date = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
        const trains = Array.from({ length: 1000 }, (_, index) => ({
            id: String(index), train: `G${index + 1}`, from: '北京南', to: '上海虹桥',
            duration: '04:00', stationCount: 8, mileage: index + 100,
            arrival: '12:00', departure: '12:05', stay: 5,
        }))
        const reply = route => route.fulfill({
            json: {
                success: true, date, station: '北京南', source: '路路通', version: '20261013',
                total: trains.length, trains, generatedAt: Date.now(),
            },
        })
        await page.route('**/api/**', reply)
        let client = await page.context().newCDPSession(page)
        await page.goto(`${address}/#/list`)
        const trainSurface = await checkNativeSwipe(page, client, 'Train list')
        // Native scroll events also preserve the cached route position.
        await page.waitForTimeout(1200)
        const saved = await trainSurface.evaluate(element => element.scrollTop)
        await page.locator('.header-brand').tap()
        await page.getByPlaceholder('例如：E351822734').waitFor()
        await page.goBack()
        await page.waitForFunction(({ selector, saved }) =>
            Math.abs(document.querySelector(selector)?.scrollTop - saved) < 3,
        { selector: viewport, saved })
        await checkTopRefresh(page, client, page.locator(viewport).first())

        await page.close()
        page = await browser.newPage(options)
        page.on('pageerror', error => errors.push(error.message))
        await page.route('**/api/**', reply)
        client = await page.context().newCDPSession(page)
        await page.goto(`${address}/#/station?station=${encodeURIComponent('北京南')}`)
        await page.getByText('共 1,000 趟', { exact: false }).waitFor()
        await page.locator('.board-actions .el-switch').click()
        const stationSurface = await checkNativeSwipe(page, client, 'Station board')
        assert.equal(await page.getByRole('switch').getAttribute('aria-checked'), 'false')
        await checkTopRefresh(page, client, stationSurface)
        assert.deepEqual(errors, [])
        console.log('PASS: browser owns touch scrolling on both virtual tables, bounded row rendering, route restoration and top pull refresh.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
