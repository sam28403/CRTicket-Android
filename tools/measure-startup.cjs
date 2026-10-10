const { writeFile, mkdir } = require('node:fs/promises')
const path = require('node:path')
const { assetServer, getPlaywright } = require('./browser-support.cjs')

async function main() {
    const { server, address } = await assetServer()
    const { chromium } = getPlaywright()
    const browser = await chromium.launch({ channel: 'chrome', headless: true })
    const runs = []

    try {
        for (let index = 0; index < 3; index++) {
            const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
            const page = await context.newPage()
            const session = await context.newCDPSession(page)
            await session.send('Emulation.setCPUThrottlingRate', { rate: 4 })
            await page.addInitScript(() => {
                window.CRTicketAndroid = {
                    configuration: () => JSON.stringify({ backend: '' }),
                }
                window.startupLongTasks = []
                new PerformanceObserver(list => {
                    for (const entry of list.getEntries()) {
                        window.startupLongTasks.push(entry.duration)
                    }
                }).observe({ type: 'longtask', buffered: true })
            })
            await page.goto(address, { waitUntil: 'domcontentloaded' })
            await page.locator('.main-aside input').first().waitFor()
            const readyMs = await page.evaluate(() => performance.now())
            await page.waitForTimeout(800)
            const resources = await page.evaluate(() => ({
                requests: performance.getEntriesByType('resource')
                    .map(entry => ({ name: entry.name, bytes: entry.decodedBodySize })),
                longTaskMs: window.startupLongTasks.reduce((sum, value) => sum + value, 0),
            }))
            runs.push({ readyMs, ...resources })
            await context.close()
        }
        const result = {
            cpuThrottle: 4,
            averageReadyMs: runs.reduce((sum, run) => sum + run.readyMs, 0) / runs.length,
            startupJsBytes: runs[0].requests.filter(item => item.name.endsWith('.js'))
                .reduce((sum, item) => sum + item.bytes, 0),
            fontBytes: runs[0].requests.filter(item => /\.(ttf|woff2)$/.test(item.name))
                .reduce((sum, item) => sum + item.bytes, 0),
            runs,
        }
        const output = path.resolve(__dirname, '../build/verification')
        await mkdir(output, { recursive: true })
        await writeFile(path.join(output, process.argv[2] || 'startup.json'), JSON.stringify(result, null, 2))
        console.log(JSON.stringify(result, (key, value) => key === 'runs' ? undefined : value))
    } finally {
        await browser.close()
        server.close()
    }
}

main().catch(error => {
    console.error(error)
    process.exitCode = 1
})
