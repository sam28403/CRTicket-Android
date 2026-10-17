const assert = require('node:assert/strict')
const { mkdir } = require('node:fs/promises')
const path = require('node:path')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function main() {
    const { server, address } = await assetServer()
    const output = path.resolve(__dirname, '../build/verification/station-interaction')
    await mkdir(output, { recursive: true })
    let browser
    try {
        browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
        for (const width of [280, 320, 360, 390, 600, 840, 1280]) {
            const page = await browser.newPage({
                viewport: { width, height: 900 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2,
            })
            const errors = []
            page.on('pageerror', error => errors.push(error.message))
            await page.addInitScript(() => {
                const names = ['乌鲁木齐南', '呼和浩特东', '上海虹桥', '北京南', '哈尔滨西',
                    '长沙南', '广州南', '天津南', '南京南', '杭州东']
                localStorage.setItem('crticket-device-tickets-v1', JSON.stringify(
                    Array.from({ length: 2400 }, (_, index) => ({
                        id: `static-${index}`, departure_station: names[index % names.length],
                        arrival_station: '新疆生产建设兵团第十二师三坪农场站',
                        travel_date: '2026年10月17日', departure_time: '09:00', train_no: 'G1',
                    })),
                ))
                window.CRTicketAndroid = { configuration: () => JSON.stringify({ backend: 'phone' }) }
                window.stationTextBounds = []
                window.staticRedraws = 0
                const clear = CanvasRenderingContext2D.prototype.clearRect
                const text = CanvasRenderingContext2D.prototype.fillText
                CanvasRenderingContext2D.prototype.clearRect = function (...args) {
                    if (this.canvas.closest('.chart-container, .city-chart, .calendar-container')) window.staticRedraws++
                    return clear.apply(this, args)
                }
                CanvasRenderingContext2D.prototype.fillText = function (value, x, y, ...args) {
                    if (this.canvas.closest('.chart-container')) {
                        const bounds = this.measureText(String(value))
                        const matrix = this.getTransform()
                        const corners = [
                            [x - bounds.actualBoundingBoxLeft, y - bounds.actualBoundingBoxAscent],
                            [x + bounds.actualBoundingBoxRight, y - bounds.actualBoundingBoxAscent],
                            [x - bounds.actualBoundingBoxLeft, y + bounds.actualBoundingBoxDescent],
                            [x + bounds.actualBoundingBoxRight, y + bounds.actualBoundingBoxDescent],
                        ].map(([px, py]) => new DOMPoint(px, py).matrixTransform(matrix))
                        window.stationTextBounds.push({
                            text: String(value),
                            left: Math.min(...corners.map(point => point.x)),
                            right: Math.max(...corners.map(point => point.x)),
                            top: Math.min(...corners.map(point => point.y)),
                            bottom: Math.max(...corners.map(point => point.y)),
                            width: this.canvas.width, height: this.canvas.height,
                        })
                    }
                    return text.call(this, value, x, y, ...args)
                }
            })
            await page.goto(`${address}/#/user`)
            await page.locator('.chart-container canvas').waitFor()
            await page.waitForTimeout(200)
            const overflow = await page.evaluate(() => window.stationTextBounds.filter(rect =>
                rect.left < -2 || rect.top < -2 || rect.right > rect.width + 2 || rect.bottom > rect.height + 2))
            assert.deepEqual(overflow, [], `text outside bar chart at ${width}px`)
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
            for (const selector of ['.chart-container', '.city-chart', '.calendar-container']) {
                await page.locator(selector).scrollIntoViewIfNeeded()
                await page.locator(`${selector} canvas`).first().waitFor()
                await page.waitForTimeout(200)
                const before = await page.locator(`${selector} canvas`).first().evaluate(canvas => canvas.toDataURL())
                await page.evaluate(() => { window.staticRedraws = 0 })
                const box = await page.locator(selector).boundingBox()
                for (let tap = 0; tap < 5; tap++) {
                    await page.touchscreen.tap(box.x + box.width * (0.4 + tap * 0.08), box.y + 50)
                }
                await page.waitForTimeout(200)
                assert.equal(await page.evaluate(() => window.staticRedraws), 0, `tap redraws ${selector}`)
                const after = await page.locator(`${selector} canvas`).first().evaluate(canvas => canvas.toDataURL())
                assert.equal(after, before, `tap changes ${selector}`)
            }
            await page.locator('.chart-container').scrollIntoViewIfNeeded()
            await page.screenshot({ path: path.join(output, `static-${width}.png`) })
            assert.deepEqual(errors, [])
            await page.close()
        }
        console.log('PASS: seven widths, all bar labels remain inside; bar/pie/calendar taps produce zero redraws or pixel changes.')
    } finally {
        await browser?.close()
        server.close()
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
