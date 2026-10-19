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
        await page.goto(`${address}/#/train?train=N1&date=${date}`)
        await page.getByText('请输入正确的车次', { exact: true }).first().waitFor()
        assert.equal(await page.evaluate(() => window.onlineQueries), 0)
        await page.goto(`${address}/#/train?train=G97&date=${date}`)
        await page.waitForFunction(() => document.querySelectorAll('.mobile-stop').length === 3)
        assert.equal(await page.evaluate(() => window.onlineQueries), 1)
        await page.goto(`${address}/#/train?train=G100%2FG97B&date=${date}&source=llt`)
        await page.waitForFunction(() => document.querySelectorAll('.mobile-stop').length === 3)
        assert.equal(new URLSearchParams(page.url().split('?')[1]).get('train'), 'G100/G97B')

        await page.goto(`${address}/#/monitor`)
        const filter = page.locator('.train-filter-input input')
        await filter.fill('X1')
        await page.getByText('请输入正确的车次', { exact: true }).first().waitFor()
        await page.getByRole('button', { name: '开始监控', exact: true }).click()
        await page.locator('.monitor-page .el-alert').filter({ hasText: '请输入正确的车次' }).waitFor()
        await page.locator('.train-filter-input .el-tag').filter({ hasText: 'X1' })
            .locator('.el-tag__close').click()
        await filter.fill('G1')
        await filter.press('Enter')
        await page.waitForFunction(() => !document.querySelector('.el-form-item__error'))
        await page.locator('.monitor-page .el-alert').filter({ hasText: '请输入正确的车次' })
            .waitFor({ state: 'hidden' })
        assert.deepEqual(errors, [])
        console.log('PASS: current train-number validation, offline combined record, invalid monitor input and recovery.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
