const assert = require('node:assert/strict')
const path = require('node:path')
const { mkdir } = require('node:fs/promises')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function main() {
    const { server, address } = await assetServer()
    const browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
    const output = path.resolve(__dirname, '../build/verification')
    await mkdir(output, { recursive: true })
    const { stationArchive } = await import('../web/src/android/railway/fixtures/stationArchive.js')
    const archive = Buffer.from(stationArchive({ count: 300 })).toString('base64')
    try {
        const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        await page.addInitScript(archive => {
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
                        queueMicrotask(() => {
                            if (url.pathname === '/android.ver') window.crticketApiResult(id, {
                                status: 200, body: '{"version":{"data":"20261016"}}',
                            })
                            else {
                                window.stationRequests++
                                window.crticketBinaryChunk(id, { chunk: archive })
                                window.crticketApiResult(id, { status: 200, binary: true, body: '' })
                            }
                        })
                        return
                    }
                    queueMicrotask(() => window.crticketApiResult(id, {
                        status: 200, contentType: 'application/json', body: JSON.stringify(data),
                    }))
                },
            }
            if (localStorage.getItem('native-station-test')) {
                window.stationSnapshots = []
                window.CRTicketAndroid.stationBoardResult = payload => {
                    window.stationSnapshots.push(JSON.parse(payload))
                }
                window.CRTicketAndroid.setHomeVisible = () => {}
                window.CRTicketAndroid.setNativePage = route => { window.nativePage = route }
                window.CRTicketAndroid.setNativeBackTarget = route => { window.nativeBackTarget = route }
            }
        }, archive)
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
        await page.route('https://a.tile.openstreetmap.fr/**', route => route.fulfill({
            contentType: 'image/png',
            body: Buffer.from(
                'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=',
                'base64',
            ),
        }))
        await page.getByRole('tab', { name: '地图', exact: true }).click()
        await page.locator('.leaflet-tile-loaded').first().waitFor()
        assert.equal(await page.locator('.station-location-map iframe').count(), 0)
        assert.equal(await page.locator('.leaflet-interactive').count(), 0)
        assert.equal(await page.locator('.leaflet-overlay-pane path').count(), 1)
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
        await page.screenshot({ path: path.join(output, 'station-map-mobile.png'), fullPage: true })
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
        await page.evaluate(() => localStorage.setItem('native-station-test', 'true'))
        await page.goto(`${address}/#/station?station=${encodeURIComponent('北京南')}`)
        await page.reload()
        await page.waitForFunction(() => window.stationSnapshots?.some(value => value.tabs?.all?.length === 300))
        const board = await page.evaluate(() => window.stationSnapshots.find(value => value.tabs?.all?.length === 300))
        assert.equal(board.tabs.departure.length, 300)
        assert.equal(board.tabs.arrival.length, 0)
        assert.ok(board.tabs.all.every(row => row.route.includes('source=llt')))
        assert.equal(await page.locator('.station-page').count(), 0)
        assert.equal(await page.evaluate(() => window.nativePage), '/station')
        await page.evaluate(() => window.crticketNavigate('/station?station=' + encodeURIComponent('上海')))
        await page.waitForFunction(() => window.stationSnapshots.findLast(value => value.kind === 'board')
            ?.resultStation === '上海')
        assert.equal(await page.evaluate(() => decodeURIComponent(window.nativeBackTarget)), '/station?station=北京南')
        await page.evaluate(() => window.crticketBack())
        await page.waitForFunction(() => window.stationSnapshots.findLast(value => value.kind === 'board')
            ?.resultStation === '北京南')
        await page.evaluate(() => window.crticketNavigate('/train?train=G1&date=2026-10-11&source=llt'))
        await page.getByText('车站数量：2 站', { exact: false }).waitFor()
        assert.equal(await page.evaluate(() => window.stationSnapshots.findLast(value => value.kind === 'board')
            ?.resultStation), '北京南')
        await page.evaluate(() => window.crticketBack())
        await page.waitForFunction(() => window.nativePage === '/station')
        assert.equal(await page.evaluate(() => new URLSearchParams(location.hash.split('?')[1]).get('station')), '北京南')
        await page.evaluate(() => window.dispatchEvent(new CustomEvent('crticket-station-request', {
            detail: { action: 'suggest', station: 'beijingnan' },
        })))
        await page.waitForFunction(() => window.stationSnapshots.some(value =>
            value.kind === 'suggestions' && value.suggestions.some(item => item.value === '北京南')))
        await page.evaluate(() => window.dispatchEvent(new CustomEvent('crticket-station-request', {
            detail: { action: 'query', station: '不存在的车站' },
        })))
        await page.waitForFunction(() => window.stationSnapshots.some(value =>
            value.resultStation === '不存在的车站' && value.tabs?.all?.length === 0))
        await page.goto(`${address}/#/station-map?station=${encodeURIComponent('北京南')}&dark=true`)
        await page.locator('.leaflet-tile-loaded').first().waitFor()
        assert.equal(await page.locator('html.dark').count(), 1)
        assert.equal(await page.locator('.native-station-map').count(), 1)
        assert.deepEqual(errors, [])
        console.log('Station smoke passed: LLT only, native snapshots, search, map, tabs, 320–1200px and navigation.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
