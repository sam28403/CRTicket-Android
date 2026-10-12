const assert = require('node:assert/strict')
const path = require('node:path')
const { mkdir } = require('node:fs/promises')
const { assetServer, getPlaywright } = require('./browser-support.cjs')
const { installTrainQueryFixture } = require('./train-query-fixture.cjs')
const { touchDrag, verticalPull } = require('./pull-refresh-touch.cjs')

async function main() {
    const { server, address } = await assetServer()
    const browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
    try {
        const context = await browser.newContext({
            viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true,
        })
        const page = await context.newPage()
        const client = await context.newCDPSession(page)
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        await installTrainQueryFixture(page)
        await page.addInitScript(() => {
            window.refreshProgress = []
            window.refreshCount = 0
            window.CRTicketAndroid.setPullRefreshProgress = value => window.refreshProgress.push(value)
            window.CRTicketAndroid.refreshPage = () => {
                window.refreshCount++
                if (window.testActualReload) {
                    sessionStorage.setItem('pull-refresh-count', String(window.refreshCount))
                    location.reload()
                }
            }
        })
        const date = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
        const target = `${address}/#/train?train=G97&date=${date}`
        await page.goto(target)
        await page.getByText('车站数量：3 站', { exact: false }).waitFor()
        const count = () => page.evaluate(() => window.refreshCount)
        const resetScroll = () => page.evaluate(() => { window.scrollTo(0, 0); document.activeElement?.blur() })

        // Real browser touch input: tap jitter and horizontal movement remain browsing.
        await touchDrag(page, client, verticalPull(370, 25, 5))
        assert.equal(await count(), 0)
        await touchDrag(page, client, [[220, 25], [240, 28], [275, 30], [320, 35], [360, 40]])
        assert.equal(await count(), 0)
        await resetScroll()

        // A gesture starting below the top remains a scroll, including after it reaches the top.
        await page.evaluate(() => window.scrollTo(0, 400))
        await touchDrag(page, client, verticalPull(370, 25, 500))
        assert.equal(await count(), 0)
        await page.waitForFunction(() => document.scrollingElement.scrollTop <= 2)
        await touchDrag(page, client, verticalPull(370, 260, 20))
        assert.equal(await count(), 1)
        await page.reload()
        await page.getByText('车站数量：3 站', { exact: false }).waitFor()
        await resetScroll()

        // Maps, nested scroll areas, controls, and open dialogs own their touch gestures.
        for (const attributes of [
            'class="leaflet-container"',
            'style="overflow-y:auto;height:80px"',
            'role="combobox"',
            'class="el-select"',
        ]) {
            await page.evaluate(attrs => {
                const host = document.createElement('div')
                host.id = 'gesture-fixture'
                host.innerHTML = `<div ${attrs}><div style="height:500px">fixture</div></div>`
                host.style.cssText = 'position:fixed;top:0;right:0;width:100px;height:100px;z-index:5000'
                document.body.append(host)
                if (attrs.includes('overflow')) host.firstElementChild.scrollTop = 100
            }, attributes)
            await touchDrag(page, client, verticalPull(370, 25))
            assert.equal(await count(), 0)
            await page.evaluate(() => document.querySelector('#gesture-fixture').remove())
            await resetScroll()
        }
        await page.evaluate(() => {
            const dialog = document.createElement('div')
            dialog.id = 'gesture-fixture'
            dialog.setAttribute('role', 'dialog')
            document.body.append(dialog)
        })
        await touchDrag(page, client, verticalPull(370, 25))
        assert.equal(await count(), 0)
        await page.evaluate(() => document.querySelector('#gesture-fixture').remove())

        // A normal title tap still navigates; dragging that same link must only refresh.
        await page.locator('.header-brand').tap()
        await page.getByPlaceholder('例如：E351822734').waitFor()
        assert.equal(new URL(page.url()).hash, '#/')
        await page.goto(target)
        await page.getByText('车站数量：3 站', { exact: false }).waitFor()
        await resetScroll()

        // Releasing after pulling back cancels; one deliberate pull triggers exactly once.
        await touchDrag(page, client, [...verticalPull(370, 25), [370, 90], [370, 25]])
        assert.equal(await count(), 0)
        await touchDrag(page, client, verticalPull(160, 25, 20))
        assert.equal(await count(), 1)
        assert.equal(page.url(), target)
        assert.ok(await page.evaluate(() => window.refreshProgress.some(value => value >= 1)))
        await touchDrag(page, client, verticalPull(370, 25))
        assert.equal(await count(), 1)

        // A real page reload retains the route and stored ticket data.
        await page.reload()
        await page.getByText('车站数量：3 站', { exact: false }).waitFor()
        await resetScroll()
        await page.evaluate(() => {
            localStorage.setItem('pull-refresh-sentinel', 'preserved')
            window.testActualReload = true
        })
        await touchDrag(page, client, verticalPull(370, 460, 20))
        await page.waitForFunction(() => sessionStorage.getItem('pull-refresh-count') === '1')
        await page.getByText('车站数量：3 站', { exact: false }).waitFor()
        assert.equal(page.url(), target)
        assert.equal(await page.evaluate(() => localStorage.getItem('pull-refresh-sentinel')), 'preserved')
        assert.equal(await page.evaluate(() => sessionStorage.getItem('pull-refresh-count')), '1')

        // Lists and tables follow the same rule: scroll to their top, then pull again.
        await resetScroll()
        await page.evaluate(() => {
            const list = document.createElement('div')
            list.id = 'gesture-fixture'
            list.style.cssText = 'position:fixed;top:0;right:0;width:100px;height:100px;overflow:auto;z-index:5000'
            list.innerHTML = '<div style="height:600px">Scrollable list</div>'
            document.body.append(list)
            list.scrollTop = 160
        })
        await touchDrag(page, client, verticalPull(370, 25, 300))
        assert.equal(await count(), 0)
        await page.waitForFunction(() => document.querySelector('#gesture-fixture').scrollTop <= 2)
        await touchDrag(page, client, verticalPull(370, 25, 20))
        assert.equal(await count(), 1)
        await page.evaluate(() => document.querySelector('#gesture-fixture').remove())

        // An older/native-less browser uses the web indicator and the same gesture policy.
        await page.evaluate(() => {
            delete window.CRTicketAndroid.setPullRefreshProgress
            delete window.CRTicketAndroid.refreshPage
        })
        // The native capability is captured on mount, so test the fallback in a fresh page.
        const fallback = await context.newPage()
        await installTrainQueryFixture(fallback)
        await fallback.goto(target)
        await fallback.getByText('车站数量：3 站', { exact: false }).waitFor()
        const fallbackClient = await context.newCDPSession(fallback)
        await touchDrag(fallback, fallbackClient, verticalPull(160, 25, 20), { finish: false })
        await fallback.getByRole('status', { name: '松开刷新', exact: true }).waitFor()
        const output = path.resolve(__dirname, '../build/verification')
        await mkdir(output, { recursive: true })
        await fallback.screenshot({ path: path.join(output, 'pull-refresh-web.png') })
        await fallbackClient.send('Input.dispatchTouchEvent', { type: 'touchCancel', touchPoints: [] })
        assert.deepEqual(errors, [])
        console.log('PASS: scroll to top then short pull refresh, nested list refresh, map/control/dialog gestures, pull-back cancellation, duplicate guard, real reload and browser fallback.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
