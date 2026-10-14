const assert = require('node:assert/strict')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function main() {
    const { server, address } = await assetServer()
    let browser
    try {
        browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
        const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        await page.addInitScript(() => {
            window.homeVisibility = []
            window.nativePages = []
            window.CRTicketAndroid = {
                configuration: () => JSON.stringify({ backend: 'phone' }),
                setHomeVisible: visible => window.homeVisibility.push(visible),
                setNativePage: route => {
                    window.nativePages.push(route)
                    window.homeVisibility.push(route === '/home')
                },
                systemDarkMode: () => false,
                setAppearance: dark => { window.appearanceDark = dark },
            }
        })
        const navigate = route => page.evaluate(route => window.crticketNavigate(route), route)
        const back = () => page.evaluate(() => window.crticketBack())
        const waitRoute = route => page.waitForFunction(route => location.hash === `#${route}`, route)
        const visible = expected => page.waitForFunction(
            expected => window.homeVisibility.at(-1) === expected, expected,
        )

        await page.goto(`${address}/#/home`)
        await visible(true)
        assert.equal(await page.locator('.home-fallback').count(), 0)
        assert.equal(await back(), false)

        // A cancelled/duplicate navigation must not hide the actual home route.
        await page.evaluate(() => Promise.all([
            window.crticketNavigate('/user'), window.crticketNavigate('/home'),
        ]))
        await waitRoute('/home')
        await visible(true)

        await navigate('/')
        await page.getByPlaceholder('例如：E351822734').waitFor()
        await visible(false)
        assert.equal(await back(), true)
        await waitRoute('/home')
        await visible(true)

        await navigate('/user')
        await page.getByRole('heading', { name: '本机车票数据' }).waitFor()
        await visible(false)
        assert.equal(await back(), true)
        await waitRoute('/home')
        await visible(true)

        await navigate('/about')
        await waitRoute('/about')
        await page.waitForFunction(() => window.nativePages.at(-1) === '/about')
        assert.equal(await page.locator('.about-fallback').count(), 0)
        assert.equal(await back(), true)
        await waitRoute('/home')
        await visible(true)

        // Notification/deep-link entry has no preceding home in its history.
        await page.goto(`${address}/#/train`)
        await visible(false)
        assert.equal(await back(), true)
        await waitRoute('/home')
        await visible(true)
        await page.reload()
        await visible(true)

        // Existing dialogs must close before back navigation reaches home.
        await navigate('/')
        await page.getByPlaceholder('例如：E351822734').waitFor()
        await page.evaluate(() => {
            const close = document.createElement('button')
            close.className = 'el-dialog__headerbtn'
            close.textContent = 'Close fixture'
            close.onclick = () => { window.dialogClosed = true; close.remove() }
            document.body.append(close)
        })
        assert.equal(await back(), true)
        assert.equal(await page.evaluate(() => window.dialogClosed), true)
        assert.equal(await page.evaluate(() => location.hash), '#/')
        assert.equal(await back(), true)
        await waitRoute('/home')
        assert.deepEqual(errors, [])
        await page.close()

        const fallback = await browser.newPage()
        await fallback.goto(`${address}/#/home`)
        await fallback.getByRole('heading', { name: 'CRTicket' }).waitFor()
        for (const width of [320, 390, 840]) {
            await fallback.setViewportSize({ width, height: 844 })
            assert.equal(await fallback.locator('nav a').count(), 8)
            assert.ok(await fallback.evaluate(() =>
                document.documentElement.scrollWidth <= innerWidth))
        }
        await fallback.getByRole('link', { name: /^关于/ }).click()
        await fallback.getByRole('heading', { name: 'CRTicket' }).waitFor()
        assert.equal(await fallback.getByRole('link', { name: 'GitHub' }).getAttribute('href'),
            'https://github.com/sam28403/CRTicket')
        await fallback.getByRole('button', { name: '返回' }).click()
        await fallback.getByRole('link', { name: /^关于/ }).waitFor()
        await fallback.getByRole('link', { name: /车票生成/ }).click()
        await fallback.getByPlaceholder('例如：E351822734').waitFor()
        console.log('PASS: native home route visibility, maker/settings navigation, back, dialogs, deep links, reload and browser fallback.')
    } finally {
        await browser?.close()
        server.close()
    }
}

main().catch(error => {
    console.error(error)
    process.exitCode = 1
})
