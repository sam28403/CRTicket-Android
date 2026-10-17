const assert = require('node:assert/strict')
const { mkdir } = require('node:fs/promises')
const path = require('node:path')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function main() {
    const { default: stationData } = await import('../web/src/station_name.js')
    const cities = new Map()
    stationData.split('@').filter(Boolean).map(row => row.split('|'))
        .sort((a, b) => (b[7]?.length || 0) - (a[7]?.length || 0))
        .forEach(row => { if (row[7] && !cities.has(row[7])) cities.set(row[7], row[1]) })
    const stations = [...cities.values()].slice(0, 10)
    const tickets = stations.map((station, index) => ({
        id: `statistics-${index}`, departure_station: station, arrival_station: '北京南',
        travel_date: '2026年10月17日', departure_time: '09:00', train_no: 'G1',
    }))
    const { server, address } = await assetServer()
    const output = path.resolve(__dirname, '../build/verification/statistics')
    await mkdir(output, { recursive: true })
    let browser
    try {
        browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
        const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        await page.addInitScript(tickets => {
            localStorage.setItem('crticket-device-tickets-v1', JSON.stringify(tickets))
            window.CRTicketAndroid = {
                configuration: () => JSON.stringify({ backend: 'phone' }),
                systemDarkMode: () => false,
            }
            window.canvasClears = 0
            window.chartTexts = { station: [], map: [] }
            const fillText = CanvasRenderingContext2D.prototype.fillText
            CanvasRenderingContext2D.prototype.fillText = function (text, ...args) {
                if (this.canvas.closest('.chart-container')) window.chartTexts.station.push(String(text))
                if (this.canvas.closest('.map-container')) window.chartTexts.map.push(String(text))
                return fillText.call(this, text, ...args)
            }
            const clear = CanvasRenderingContext2D.prototype.clearRect
            CanvasRenderingContext2D.prototype.clearRect = function (...args) {
                window.canvasClears++
                return clear.apply(this, args)
            }
        }, tickets)
        await page.goto(`${address}/#/user`)
        await page.locator('.city-legend li').nth(9).waitFor()
        await page.locator('.chart-container canvas').waitFor()
        assert.equal(await page.locator('.map-container canvas').count(), 0)
        for (const selector of ['.city-chart', '.calendar-container', '.map-container']) {
            await page.locator(selector).scrollIntoViewIfNeeded()
            await page.locator(`${selector} canvas`).first().waitFor()
        }
        await page.waitForTimeout(500)
        await page.evaluate(() => { window.canvasClears = 0 })
        await page.waitForTimeout(600)
        assert.equal(await page.evaluate(() => window.canvasClears), 0, 'static map must stop redrawing')
        const stationInstance = await page.locator('.chart-container').getAttribute('_echarts_instance_')
        assert.equal(await page.locator('h1').textContent(), '运转数据')
        assert.deepEqual(await page.locator('h2').allTextContents(), [
            '最常到达站点统计', '最常访问城市统计', '月度运转日历', '历史运转轨迹',
        ])
        assert.equal(await page.locator('.user-sidebar, .statistics-card-header, .top-header').count(), 0)
        for (const width of [320, 390, 600, 840, 1280]) {
            await page.evaluate(() => { window.chartTexts.station = [] })
            await page.setViewportSize({ width, height: 900 })
            await page.locator('.chart-container').scrollIntoViewIfNeeded()
            const expectedStations = ['北京南', ...stations.filter(name => name !== '北京南').slice(0, 9)]
            await page.waitForFunction(expected => expected.every(name =>
                window.chartTexts.station.includes(name)), expectedStations)
            await page.locator('.city-chart').scrollIntoViewIfNeeded()
            await page.waitForFunction(() => {
                const el = document.querySelector('.city-chart')
                return Math.abs(el.clientWidth - el.querySelector('canvas').clientWidth) < 2
            })
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
            const overflow = await page.locator('.city-legend li').evaluateAll(items => items.some(item => {
                const box = item.getBoundingClientRect()
                return [...item.children].some(child => {
                    const rect = child.getBoundingClientRect()
                    return rect.left < box.left - 1 || rect.right > box.right + 1
                })
            }))
            assert.equal(overflow, false, `city labels overflow at ${width}px`)
            for (const selector of ['.calendar-container', '.map-container']) {
                await page.locator(selector).scrollIntoViewIfNeeded()
            }
            await page.screenshot({ path: path.join(output, `light-${width}.png`), fullPage: true })
        }
        // Extremely long names still remain readable and inside the legend.
        await page.locator('.legend-name').first().evaluate(el => {
            el.textContent = '克孜勒苏柯尔克孜自治州'.repeat(5)
        })
        await page.setViewportSize({ width: 320, height: 900 })
        await page.waitForFunction(() => document.documentElement.scrollWidth <= innerWidth)
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
        await page.locator('.calendar-header input').fill('2026-09')
        await page.locator('.calendar-header input').press('Enter')
        await page.waitForFunction(() => document.querySelector('.calendar-header input').value === '2026-09')
        assert.equal(await page.locator('.chart-container').getAttribute('_echarts_instance_'), stationInstance)
        await page.evaluate(() => localStorage.setItem('crticket-theme', 'classic-dark'))
        await page.reload()
        await page.locator('html.dark .city-legend li').nth(9).waitFor()
        for (const selector of ['.city-chart', '.calendar-container', '.map-container']) {
            await page.locator(selector).scrollIntoViewIfNeeded()
            await page.locator(`${selector} canvas`).first().waitFor()
        }
        await page.screenshot({ path: path.join(output, 'dark-320.png'), fullPage: true })
        await page.getByRole('button', { name: '返回', exact: true }).click()
        await page.waitForURL('**/#/home')
        assert.deepEqual(errors, [])

        const native = await browser.newPage()
        await native.addInitScript(() => {
            window.nativePages = []
            window.statisticsState = null
            window.CRTicketAndroid = {
                configuration: () => JSON.stringify({ backend: 'phone' }),
                setHomeVisible: () => {},
                setNativePage: route => window.nativePages.push(route),
                supportsStatisticsPage: () => true,
                statisticsState: (empty, loading) => { window.statisticsState = { empty, loading } },
            }
        })
        await native.goto(`${address}/#/user`)
        await native.waitForFunction(() => window.statisticsState?.empty && !window.statisticsState.loading)
        assert.equal(await native.locator('.el-empty').count(), 0)
        await native.waitForFunction(() => window.nativePages.at(-1) === '/user')
        assert.equal(await native.locator('.operations-toolbar').count(), 0)
        const { default: coordinates } = await import('../web/src/utils/maps/stationCoordinates.js')
        const mapStations = [
            '北京南', '南京南', '上海虹桥', '杭州东', '广州南', '长沙南', '武汉', '郑州东',
            '深圳北', '沈阳北', '长春', '哈尔滨', '天津南', '济南西', '合肥南', '福州南',
            '厦门北', '南昌西', '太原南', '西安北', '成都东', '重庆北',
        ].filter(name => coordinates[name])
        assert.ok(mapStations.length > 20)
        const labels = await browser.newPage({ viewport: { width: 1280, height: 900 } })
        await labels.addInitScript(stations => {
            localStorage.setItem('crticket-device-tickets-v1', JSON.stringify(stations.map((station, index) => ({
                id: `map-label-${index}`, departure_station: station, arrival_station: '北京南',
                travel_date: '2026年10月17日', departure_time: '09:00', train_no: 'G1',
            }))))
            window.CRTicketAndroid = { configuration: () => JSON.stringify({ backend: 'phone' }) }
            window.mapLabels = []
            const fillText = CanvasRenderingContext2D.prototype.fillText
            CanvasRenderingContext2D.prototype.fillText = function (text, ...args) {
                if (this.canvas.closest('.map-container')) window.mapLabels.push(String(text))
                return fillText.call(this, text, ...args)
            }
        }, mapStations)
        await labels.goto(`${address}/#/user`)
        await labels.locator('.map-container').scrollIntoViewIfNeeded()
        await labels.waitForFunction(stations => stations.every(name => window.mapLabels.includes(name)), mapStations)
        await labels.screenshot({ path: path.join(output, 'map-all-stations.png') })
        console.log('Statistics: every top-ten station label at five widths, all 22 map labels, lazy rendering, no idle redraw, reuse, themes and navigation passed.')
    } finally {
        await browser?.close()
        server.close()
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
