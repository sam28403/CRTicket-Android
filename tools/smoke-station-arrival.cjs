const assert = require('node:assert/strict')
const path = require('node:path')
const { mkdir } = require('node:fs/promises')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function main() {
    const { server, address } = await assetServer()
    const browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
    const output = path.resolve(__dirname, '../build/verification')
    await mkdir(output, { recursive: true })
    try {
        const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        await page.addInitScript(() => {
            window.arrivalAllowed = true
            window.arrivalSupported = true
            const read = () => ({
                success: true, supported: window.arrivalSupported, stations: 3360,
                enabled: localStorage.getItem('arrival-fixture') === 'on',
                active: localStorage.getItem('arrival-fixture') === 'on',
                message: localStorage.getItem('arrival-fixture') === 'on'
                    ? '已开启到站提醒，正在监控附近车站' : '尚未开启到站提醒',
            })
            const reply = (id, result) => queueMicrotask(() => window.crticketNotificationResult(id, result))
            window.CRTicketAndroid = {
                configuration: () => JSON.stringify({ backend: 'phone' }),
                systemDarkMode: () => false,
                setAppearance() {},
                stationArrivalState(id) { reply(id, read()) },
                enableStationArrival(id) {
                    if (window.arrivalAllowed) localStorage.setItem('arrival-fixture', 'on')
                    reply(id, { ...read(), success: window.arrivalAllowed,
                        message: window.arrivalAllowed ? read().message : '请允许始终定位后重新开启' })
                },
                disableStationArrival(id) {
                    localStorage.removeItem('arrival-fixture')
                    reply(id, { ...read(), message: '已关闭到站提醒' })
                },
            }
        })
        await page.goto(`${address}/#/user`)
        const card = page.locator('.arrival-card')
        const start = () => card.getByRole('button', { name: '开启到站提醒', exact: true })
        const stop = () => card.getByRole('button', { name: '关闭到站提醒', exact: true })
        await start().waitFor()
        await page.evaluate(() => { window.arrivalAllowed = false })
        await start().click()
        await card.getByText('请允许始终定位后重新开启', { exact: true }).waitFor()
        assert.equal(await stop().count(), 0)
        await page.evaluate(() => { window.arrivalAllowed = true })
        await start().click()
        await stop().waitFor()
        await page.reload()
        await stop().waitFor()
        for (const width of [320, 390, 800, 1200]) {
            await page.setViewportSize({ width, height: 844 })
            await card.scrollIntoViewIfNeeded()
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
        }
        await page.setViewportSize({ width: 390, height: 844 })
        await card.screenshot({ path: path.join(output, 'station-arrival-settings.png') })
        await stop().click()
        await start().waitFor()
        await page.evaluate(() => {
            window.arrivalSupported = false
            window.dispatchEvent(new Event('focus'))
        })
        await page.waitForFunction(() => document.querySelector('.arrival-actions button')?.disabled)
        assert.deepEqual(errors, [])
        console.log('Arrival UI smoke passed: default off, denial, enable, reload, disable, unsupported device and 320–1200px.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
