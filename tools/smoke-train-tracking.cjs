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
            window.trackingAllowed = true
            window.trackingStarts = 0
            const reply = (id, result) => queueMicrotask(() => window.crticketNotificationResult(id, result))
            window.CRTicketAndroid = {
                configuration: () => JSON.stringify({ backend: 'phone' }),
                systemDarkMode: () => true,
                setAppearance() {},
                cancel() {},
                requestTrainTrackingPermission(id) {
                    reply(id, { granted: window.trackingAllowed, message: '请开启列车追踪通知' })
                },
                trainTrackingState(id) {
                    const state = JSON.parse(sessionStorage.getItem('tracking') || '{"active":false}')
                    if (state.expiresAt && Date.now() >= state.expiresAt) {
                        sessionStorage.removeItem('tracking')
                    }
                    reply(id, JSON.parse(sessionStorage.getItem('tracking') || '{"active":false}'))
                },
                startTrainTracking(id, payload) {
                    const data = JSON.parse(payload)
                    window.trackingStarts++
                    window.lastSchedule = data
                    const today = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
                    const finished = data.date < today
                    sessionStorage.setItem('tracking', JSON.stringify({
                        active: true, finished, train: data.train, date: data.date, queryTrain: data.queryTrain,
                    }))
                    reply(id, { success: true, finished })
                },
                stopTrainTracking(id) {
                    sessionStorage.removeItem('tracking')
                    reply(id, { success: true })
                },
                request(id, method, rawUrl) {
                    const url = new URL(rawUrl)
                    let data
                    if (url.pathname.includes('/train/search')) {
                        data = { data: [{ station_train_code: '1461', train_no: 'fixture',
                            from_station: '北京', to_station: '上海' }] }
                    } else if (url.pathname.includes('/queryTrainInfo/query')) {
                        data = { data: { data: [
                            { station_name: '北京', start_time: '11:59', station_no: '01' },
                            { station_name: '天津西', arrive_time: '13:00', start_time: '13:05',
                                station_no: '02', arrive_day_diff: 0 },
                            { station_name: '上海', arrive_time: '10:00', station_no: '03', arrive_day_diff: 1 },
                        ] } }
                    } else {
                        queueMicrotask(() => window.crticketApiResult(id, { error: '测试未提供离线里程数据' }))
                        return
                    }
                    queueMicrotask(() => window.crticketApiResult(id, {
                        status: 200, contentType: 'application/json', body: JSON.stringify(data),
                    }))
                },
            }
        })
        const date = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
        await page.goto(`${address}/#/train?train=1461&date=${date}`)
        const start = () => page.getByRole('button', { name: '追踪 1461', exact: true })
        const stop = () => page.getByRole('button', { name: '停止追踪 1461', exact: true })
        await start().waitFor()
        await page.waitForFunction(() => !document.querySelector('.result-title-row button')?.disabled)
        for (const width of [320, 390, 800, 1200]) {
            await page.setViewportSize({ width, height: 844 })
            const layout = await page.locator('.result-title-row').evaluate(element => {
                const row = element.getBoundingClientRect()
                const button = element.querySelector('button').getBoundingClientRect()
                return { right: button.right <= row.right + 1, left: button.left >= row.left,
                    primary: element.querySelector('button').classList.contains('el-button--primary'),
                    overflow: document.documentElement.scrollWidth > window.innerWidth }
            })
            assert.deepEqual(layout, { right: true, left: true, primary: true, overflow: false })
        }
        await page.setViewportSize({ width: 390, height: 844 })
        await page.locator('.results').screenshot({ path: path.join(output, 'train-tracking-mobile.png') })
        await page.evaluate(() => { window.trackingAllowed = false })
        await start().click()
        await page.getByText('请开启列车追踪通知', { exact: true }).waitFor()
        assert.equal(await page.evaluate(() => window.trackingStarts), 0)

        await page.evaluate(() => { window.trackingAllowed = true })
        await start().click()
        await stop().waitFor()
        assert.equal(await page.evaluate(() => window.lastSchedule.stops[2].arrivalDay), 1)
        await page.reload()
        await stop().waitFor()
        // The visible button refreshes at the native expiry without waiting for the minute poll.
        await page.evaluate(() => {
            const state = JSON.parse(sessionStorage.getItem('tracking'))
            state.expiresAt = Date.now() + 1_000
            sessionStorage.setItem('tracking', JSON.stringify(state))
            window.dispatchEvent(new Event('focus'))
        })
        await start().waitFor({ timeout: 5_000 })
        assert.equal(await page.evaluate(() => sessionStorage.getItem('tracking')), null)
        await start().click()
        await stop().waitFor()
        await stop().click()
        await start().waitFor()

        // Simulate stopping from the notification while the detail page is in the background.
        await start().click()
        await stop().waitFor()
        await page.evaluate(() => {
            sessionStorage.removeItem('tracking')
            window.dispatchEvent(new Event('focus'))
        })
        await start().waitFor()
        const historicalDate = new Date(Date.now() + 8 * 3600000 - 86400000)
            .toISOString().slice(0, 10)
        await page.goto(`${address}/#/train?train=1461&date=${historicalDate}`)
        await start().waitFor()
        await page.waitForFunction(() => !document.querySelector('.result-title-row button')?.disabled)
        await start().click()
        await page.getByText('已显示列车终到通知', { exact: true }).waitFor()
        assert.equal(await page.evaluate(() => window.lastSchedule.date), historicalDate)
        await stop().waitFor()
        assert.equal(await page.evaluate(() => JSON.parse(sessionStorage.getItem('tracking')).finished), true)
        await page.reload()
        await stop().waitFor()
        await stop().click()
        await start().waitFor()
        assert.equal(await page.evaluate(() => sessionStorage.getItem('tracking')), null)
        assert.deepEqual(errors, [])
        console.log('PASS: responsive primary button, permission denial, start/stop, reload restoration, notification stop synchronization and historical terminal notices.')
    } finally {
        await browser.close()
        server.close()
    }
}

main().catch(error => {
    console.error(error)
    process.exitCode = 1
})
