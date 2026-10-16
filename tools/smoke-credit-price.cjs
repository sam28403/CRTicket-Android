const { createServer } = require('node:http')
const { readFile, writeFile, mkdir } = require('node:fs/promises')
const path = require('node:path')
const assert = require('node:assert/strict')
const { chromium } = require(process.env.CRTICKET_PLAYWRIGHT_PACKAGE || 'playwright')

const root = path.resolve(__dirname, '../app/src/main/assets/web')
const output = path.resolve(__dirname, '../build/verification')
const types = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.ttf': 'font/ttf', '.jpg': 'image/jpeg', '.png': 'image/png', '.woff2': 'font/woff2' }
const server = createServer(async (req, res) => {
  try {
    const filename = path.resolve(root, '.' + new URL(req.url, 'http://localhost').pathname.replace(/\/$/, '/index.html'))
    if (!filename.startsWith(root + path.sep)) throw new Error('Invalid path')
    res.setHeader('Content-Type', types[path.extname(filename)] || 'application/octet-stream')
    res.end(await readFile(filename))
  } catch { res.writeHead(404); res.end('Not found') }
})

;(async () => {
  await mkdir(output, { recursive: true })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => {
      window.exportsCaptured = []
      window.CRTicketAndroid = {
        configuration: () => JSON.stringify({ backend: '' }),
        save: (id, filename, mime, data) => {
          window.exportsCaptured.push({ filename, data })
          setTimeout(() => window.crticketSaveResult(id, { success: true }), 0)
        },
      }
      if (!localStorage.getItem('crticket-device-tickets-v1')) {
        localStorage.setItem('crticket-device-tickets-v1', JSON.stringify([{
          id: 'credit-fixture', user_id: 'device', ticket_number: 'E50000', train_no: 'G25',
          departure_station: '北京南', arrival_station: '上海虹桥', travel_date: '2026年10月16日',
          departure_time: '08:00', price: 50000, use_credit: 1, seat_type: '二等座',
          seat_no: '03车12A号', sell_place: '北京南站', theme: 'EMU_Green.jpg', distance: 1318,
          message: '中国铁路祝您旅途愉快',
        }]))
      }
    })
    const address = `http://127.0.0.1:${server.address().port}`
    await page.goto(address + '/#/')
    const price = page.getByPlaceholder('请输入数字')
    await price.fill('50000')
    await price.press('Tab')
    const credit = page.locator('.el-form-item').filter({ hasText: '使用积分' }).locator('.el-switch')
    await credit.click()
    await page.waitForFunction(() => document.querySelector('.ticket-container .price')?.textContent === '￥50000元')
    assert.equal(await price.inputValue(), '50000')
    await credit.click()
    await page.waitForFunction(() => document.querySelector('.ticket-container .price')?.textContent === '￥50000元')
    await page.goto(address + '/#/history')
    const download = async expected => {
      const before = await page.evaluate(() => window.exportsCaptured.length)
      await page.getByRole('button', { name: '记录操作' }).click()
      await page.getByText('下载车票', { exact: true }).click()
      await page.waitForFunction(count => window.exportsCaptured.length > count, before)
      assert.equal(await page.locator('.history-download-render .price').textContent(), expected)
    }
    await download('￥500元')
    const png = await page.evaluate(() => window.exportsCaptured[0].data)
    await writeFile(path.join(output, 'credit-500-yuan.png'), Buffer.from(png, 'base64'))
    await page.getByRole('button', { name: '记录操作' }).click()
    await page.getByText('编辑记录', { exact: true }).click()
    const dialog = page.locator('.ticket-record-dialog')
    assert.equal(await dialog.getByRole('spinbutton', { name: /票价/ }).inputValue(), '50000')
    const editorCredit = dialog.locator('.el-form-item').filter({ hasText: '使用积分' }).locator('.el-switch')
    await editorCredit.click()
    assert.equal(await dialog.getByRole('spinbutton', { name: /票价/ }).inputValue(), '500')
    await editorCredit.click()
    assert.equal(await dialog.getByRole('spinbutton', { name: /票价/ }).inputValue(), '50000')
    await dialog.getByRole('spinbutton', { name: /票价/ }).fill('60000')
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    await download('￥600元')
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('crticket-device-tickets-v1'))[0].price), 60000)
    assert.deepEqual(errors, [])
    console.log('PASS: maker keeps original behavior; history toggles convert both ways; PNG fare and saved points stay correct.')
  } finally { await browser.close(); server.close() }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
