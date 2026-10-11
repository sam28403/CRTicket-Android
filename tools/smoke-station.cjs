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
            const date = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
            window.stationRequests = 0
            window.CRTicketAndroid = {
                configuration: () => JSON.stringify({ backend: 'phone' }),
                systemDarkMode: () => false,
                setAppearance() {},
                cancel() {},
                request(id, method, rawUrl) {
                    const url = new URL(rawUrl)
                    let data = {}
                    if (url.pathname === '/otn/czxx/query') {
                        window.stationRequests++
                        data = { status: true, data: { flag: true, data: Array.from(
                            { length: 300 }, (_, index) => ({
                                train_no: `fixture-${index}`, station_train_code: `G${index + 1}`,
                                station_name: url.searchParams.get('train_station_name'),
                                start_station_name: '北京南', end_station_name: '上海',
                                start_time: `${String(Math.floor(index / 13)).padStart(2, '0')}:${String(index % 13 * 4).padStart(2, '0')}`,
                                start_train_date: date, checkin: 'A1',
                            }),
                        ) } }
                    } else if (url.pathname.includes('/train/search')) {
                        const train = url.searchParams.get('keyword')
                        data = { data: [{ station_train_code: train, train_no: 'fixture',
                            from_station: '北京南', to_station: '上海' }] }
                    } else if (url.pathname.includes('/queryTrainInfo/query')) {
                        data = { data: { data: [
                            { station_name: '北京南', start_time: '08:00', station_no: '01' },
                            { station_name: '上海', arrive_time: '12:00', station_no: '02' },
                        ] } }
                    } else if (url.host.includes('lltskb') || url.host === '223.107.87.50') {
                        queueMicrotask(() => window.crticketApiResult(id, { error: '测试未提供离线数据' }))
                        return
                    }
                    queueMicrotask(() => window.crticketApiResult(id, {
                        status: 200, contentType: 'application/json', body: JSON.stringify(data),
                    }))
                },
            }
        })
        await page.clock.setFixedTime(new Date('2026-10-11T12:00:00+08:00'))
        await page.goto(`${address}/#/station?station=${encodeURIComponent('北京南')}`)
        await page.getByText('共 300 趟', { exact: false }).waitFor()
        for (const width of [320, 390, 800, 1200]) {
            await page.setViewportSize({ width, height: 844 })
            await page.getByRole('button', { name: '定位当前时间', exact: true }).click()
            await page.waitForFunction(() => document.querySelector('.current-train, .upcoming-train'))
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
            assert.ok(await page.locator('.el-table-v2__row').count() < 60)
        }
        await page.setViewportSize({ width: 390, height: 844 })
        await page.getByRole('tab', { name: '出发大屏', exact: true }).click()
        await page.getByText('共 300 趟', { exact: false }).waitFor()
        assert.equal(await page.getByRole('tab', { name: '出发大屏', exact: true })
            .getAttribute('aria-selected'), 'true')
        await page.getByRole('tab', { name: '到达大屏', exact: true }).click()
        await page.getByText('本站当天没有该类型的车次', { exact: true }).waitFor()
        await page.getByRole('tab', { name: '全部信息', exact: true }).click()
        await page.locator('.mobile-train').first().waitFor()
        await page.screenshot({ path: path.join(output, 'station-mobile.png'), fullPage: true })
        await page.locator('.mobile-train .train-link').first().click()
        await page.getByText('车站数量：2 站', { exact: false }).waitFor()
        await page.locator('.mobile-stop .station-link').first().click()
        await page.getByRole('heading', { name: '车站大屏', exact: true }).waitFor()
        await page.getByText('共 300 趟', { exact: false }).waitFor()
        assert.equal(await page.evaluate(() => window.stationRequests), 1)
        await page.goto(`${address}/#/`)
        await page.getByRole('button', { name: '车站大屏', exact: true }).click()
        await page.getByRole('heading', { name: '车站大屏', exact: true }).waitFor()
        assert.deepEqual(errors, [])
        console.log('Station smoke passed: phone dispatch, tabs, virtual rows, 320–1200px, navigation and cache.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
