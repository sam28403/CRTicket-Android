const assert = require('node:assert/strict')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function choose(page, label) {
    await page.getByRole('button', { name: '切换主题', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '切换主题', exact: true })
    assert.equal(await dialog.getByRole('radio').count(), 3)
    await dialog.getByText(label, { exact: true }).click()
    try {
        await dialog.waitFor({ state: 'hidden', timeout: 1000 })
    } catch {
        await dialog.locator('.el-dialog__headerbtn').click()
    }
}

async function nativeMode(page, dark) {
    await page.evaluate(value => {
        window.nativeDark = value
        window.dispatchEvent(new CustomEvent('crticket-system-theme', { detail: { dark: value } }))
    }, dark)
}

async function checkDark(page, dark) {
    await page.waitForFunction(value => document.documentElement.classList.contains('dark') === value, dark)
}

async function main() {
    const { server, address } = await assetServer()
    const browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
    try {
        const page = await browser.newPage({
            viewport: { width: 390, height: 844 },
            colorScheme: 'light',
        })
        await page.addInitScript(() => {
            window.nativeDark = true
            window.CRTicketAndroid = {
                configuration: () => JSON.stringify({ backend: 'phone' }),
                systemDarkMode: () => window.nativeDark,
                setAppearance: dark => { window.appearanceDark = dark },
                setThemeAppearance: (dark, pureBlack) => {
                    window.appearanceDark = dark
                    window.appearancePureBlack = pureBlack
                    window.dispatchEvent(new CustomEvent('crticket-native-palette', { detail: {
                        '--app-page': dark ? '#102030' : '#F0F4FA',
                        '--app-text': dark ? '#EEF0FA' : '#101820',
                    } }))
                },
            }
        })
        await page.goto(`${address}/#/settings`)
        await page.getByRole('heading', { name: '设置', exact: true }).waitFor()
        assert.equal(await page.locator('.theme-select').count(), 0)
        assert.equal(await page.getByText('手机内置后端 · 默认用户').count(), 0)
        // Android's actual state takes precedence over the WebView app theme media query.
        await checkDark(page, true)
        await nativeMode(page, false)
        await checkDark(page, false)
        await choose(page, '跟随系统')
        await page.locator('.el-switch').click()
        await nativeMode(page, true)
        await checkDark(page, true)
        assert.equal(await page.evaluate(() => document.documentElement.classList.contains('high-contrast')), true)
        await choose(page, '亮色')
        await checkDark(page, false)
        await nativeMode(page, true)
        await checkDark(page, false)
        await page.locator('.el-switch').click()
        await choose(page, '暗色')
        await nativeMode(page, false)
        await checkDark(page, true)
        assert.equal(await page.evaluate(() => window.appearanceDark), true)
        assert.equal(await page.evaluate(() => document.documentElement.style.getPropertyValue('--app-page')), '#102030')
        await page.reload()
        await checkDark(page, true)
        await choose(page, '跟随系统')
        await nativeMode(page, false)
        await checkDark(page, false)

        const fallback = await browser.newPage({ colorScheme: 'dark' })
        await fallback.goto(address)
        await checkDark(fallback, true)
        await fallback.emulateMedia({ colorScheme: 'light' })
        await checkDark(fallback, false)
        console.log('PASS: Android system mode, live changes, both system themes, explicit overrides and reload persistence.')
    } finally {
        await browser.close()
        server.close()
    }
}

main().catch(error => {
    console.error(error)
    process.exitCode = 1
})
