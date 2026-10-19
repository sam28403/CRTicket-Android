const assert = require('node:assert/strict')
const path = require('node:path')
const { mkdir } = require('node:fs/promises')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function main() {
    const { server, address } = await assetServer()
    const browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
    const context = await browser.newContext({ viewport: { width: 320, height: 640 } })
    const errors = []
    const open = async () => {
        const page = await context.newPage()
        page.on('pageerror', error => errors.push(error.message))
        await page.goto(`${address}/#/home`)
        await page.getByRole('heading', { name: 'CRTicket', exact: true }).waitFor()
        return page
    }
    const notice = page => page.getByRole('dialog', { name: '重要通知', exact: true })
    try {
        let page = await open()
        await notice(page).waitFor()
        await page.evaluate(() => Promise.all(document.getAnimations()
            .filter(animation => Number.isFinite(animation.effect.getTiming().iterations))
            .map(animation => animation.finished.catch(() => {}))))
        for (const viewport of [{ width: 320, height: 640 }, { width: 640, height: 360 }]) {
            await page.setViewportSize(viewport)
            const box = await notice(page).locator('.el-dialog').boundingBox()
            assert.ok(box.x >= 0 && box.x + box.width <= viewport.width)
            assert.ok(box.y >= 0 && box.y + box.height <= viewport.height)
            assert.ok(await notice(page).getByRole('button', { name: '确定', exact: true }).isVisible())
            const output = path.resolve(__dirname, '../build/verification')
            await mkdir(output, { recursive: true })
            await page.screenshot({ path: path.join(output, `important-notice-${viewport.width}.png`) })
        }
        const toggle = () => notice(page).locator('.el-checkbox').click()
        await toggle()
        assert.ok(await notice(page).getByRole('checkbox', { name: '不再弹出', exact: true }).isChecked())
        assert.equal(await page.evaluate(() => localStorage.getItem('crticket-important-notice-hidden')), null)
        await toggle()
        await notice(page).getByRole('button', { name: '确定', exact: true }).click()
        await notice(page).waitFor({ state: 'hidden' })
        await page.reload()
        await page.getByRole('heading', { name: 'CRTicket', exact: true }).waitFor()
        assert.equal(await notice(page).isVisible(), false)
        await page.close()

        page = await open()
        await notice(page).waitFor()
        await toggle()
        await notice(page).getByRole('button', { name: '确定', exact: true }).click()
        await notice(page).waitFor({ state: 'hidden' })
        assert.equal(await page.evaluate(() => localStorage.getItem('crticket-important-notice-hidden')), '1')
        await page.close()
        page = await open()
        assert.equal(await notice(page).isVisible(), false)
        assert.deepEqual(errors, [])
        console.log('PASS: important notice, portrait/landscape layout, confirmation, per-launch visibility and persisted opt-out.')
    } finally {
        await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
