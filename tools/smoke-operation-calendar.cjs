const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function main() {
    const { server, address } = await assetServer()
    let browser
    try {
        browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
        const page = await browser.newPage({ viewport: { width: 320, height: 900 } })
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        await page.addInitScript(() => {
            const dates = ['2026年10月17日', '2026年10月02日', '2026年10月17日',
                '2026年09月01日', '2026年10月02日', '2026年10月17日', '2024年02月29日']
            localStorage.setItem('crticket-device-tickets-v1', JSON.stringify(dates.map((date, index) => ({
                id: `month-${index}`, departure_station: '北京南', arrival_station: '上海虹桥',
                travel_date: date, departure_time: '09:00', train_no: 'G1',
            }))))
            window.monthRequests = []
            window.CRTicketAndroid = {
                configuration: () => JSON.stringify({ backend: 'phone' }),
                chooseStatisticsMonth: month => window.monthRequests.push(month),
            }
        })
        await page.goto(`${address}/#/user`)
        const select = month => page.evaluate(month => {
            window.dispatchEvent(new CustomEvent('crticket-statistics-month', { detail: month }))
        }, month)
        const list = async () => (await page.locator('.calendar-records li').allTextContents())
            .map(text => text.replace(/\s/g, ''))
        const checkSpacing = async expectedHeight => {
            await page.locator('.calendar-container canvas').first().waitFor()
            await page.waitForFunction(height =>
                document.querySelector('.calendar-container').clientHeight === height, expectedHeight)
            const gap = await page.evaluate(() => {
                const calendar = document.querySelector('.calendar-container').getBoundingClientRect()
                const records = document.querySelector('.calendar-records, .calendar-empty').getBoundingClientRect()
                return records.top - calendar.bottom
            })
            assert.ok(gap >= 0 && gap <= 8, `Calendar/list gap is ${gap}px`)
        }
        await page.getByRole('button', { name: '选择运转月份' }).waitFor()
        assert.equal(await page.locator('.calendar-header input').count(), 0)
        await select('2026-10')
        await page.waitForFunction(() => document.querySelectorAll('.calendar-records li').length === 2)
        assert.deepEqual(await list(), ['10月02日2次', '10月17日3次'])
        await page.locator('.calendar-container').scrollIntoViewIfNeeded()
        await checkSpacing(244)
        await fs.mkdir('build/verification/statistics-calendar', { recursive: true })
        await page.locator('.chart-section').filter({ has: page.locator('#calendar-title') })
            .screenshot({ path: 'build/verification/statistics-calendar/compact-october.png' })
        await page.getByRole('button', { name: '选择运转月份' }).click()
        assert.equal(await page.evaluate(() => window.monthRequests.at(-1)), '2026-10')
        await select('2026-09')
        await page.waitForFunction(() => document.querySelector('.calendar-records')?.textContent.includes('09月01日'))
        assert.deepEqual(await list(), ['09月01日1次'])
        await select('2024-02')
        await page.waitForFunction(() => document.querySelector('.calendar-records')?.textContent.includes('02月29日'))
        assert.deepEqual(await list(), ['02月29日1次'])
        await checkSpacing(244)
        await select('2026-12')
        await page.getByText('本月暂无运转记录', { exact: true }).waitFor()
        assert.deepEqual(await list(), [])
        await checkSpacing(244)
        await select('2026-02')
        await page.getByText('本月暂无运转记录', { exact: true }).waitFor()
        await checkSpacing(244)
        await select('2027-02')
        await checkSpacing(202)
        await select('2026-03')
        await checkSpacing(286)
        await select('2026-09')
        await select('2026-10')
        await page.waitForFunction(() => document.querySelectorAll('.calendar-records li').length === 2)
        assert.deepEqual(await list(), ['10月02日2次', '10月17日3次'])
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
        assert.deepEqual(errors, [])
        console.log('PASS: native month request/result, ordered daily counts, leap day, empty month and rapid month switching.')
    } finally {
        await browser?.close()
        server.close()
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
