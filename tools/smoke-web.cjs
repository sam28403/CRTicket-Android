const { createServer } = require('node:http')
const { readFile, writeFile, mkdir } = require('node:fs/promises')
const path = require('node:path')
const assert = require('node:assert/strict')
const { chromium } = require(process.env.CRTICKET_PLAYWRIGHT_PACKAGE || 'playwright')

const root = path.resolve(__dirname, '../app/src/main/assets/web')
const output = path.resolve(__dirname, '../build/verification')
const types = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.ttf': 'font/ttf', '.jpg': 'image/jpeg', '.png': 'image/png', '.json': 'application/json', '.geojson': 'application/json' }
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
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1, isMobile: true, hasTouch: true })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    window.exportsCaptured = []
    window.CRTicketAndroid = {
      configuration: () => JSON.stringify({ backend: '' }),
      settings: () => { window.settingsOpened = true }, external: url => { window.externalOpened = url },
      save: (id, filename, mime, data) => {
        window.exportsCaptured.push({ filename, mime, data })
        setTimeout(() => window.crticketSaveResult(id, { success: true }), 10)
      },
    }
  })
  const address = `http://127.0.0.1:${server.address().port}`
  try {
    await page.goto(address)
    await page.getByPlaceholder('例如：E351822734').waitFor()
    assert.equal(await page.getByText('手机内置后端 · 默认用户').count(), 0)
    assert.equal(await page.getByRole('button', { name: '后端设置' }).count(), 0)
    await page.getByPlaceholder('例如：E351822734').fill('E351822734')
    await page.getByPlaceholder('输入站名或拼音').nth(0).fill('北京南')
    await page.getByPlaceholder('输入站名或拼音').nth(1).fill('上海虹桥')
    await page.getByPlaceholder('例如：G25 或 1461').fill('G1')
    await page.getByPlaceholder('请选择乘车日期').fill('2026年10月10日')
    await page.getByPlaceholder('请选择乘车日期').press('Enter')
    await page.getByPlaceholder('选择开车时间').fill('09:00')
    await page.getByPlaceholder('选择开车时间').press('Enter')
    await page.getByPlaceholder('请输入数字').fill('553')
    await page.getByPlaceholder('请输入数字').press('Tab')
    await page.locator('.el-form-item').filter({ hasText: '席位名称' }).locator('.el-select__wrapper').click()
    await page.getByRole('option', { name: '二等座', exact: true }).click()
    await page.getByPlaceholder('03车12A号').fill('01车01A号')
    await page.getByPlaceholder('XX站').fill('北京南')
    await page.evaluate(() => document.fonts.ready)
    assert.equal(await page.locator('.ticket-container').first().evaluate(
      element => getComputedStyle(element).width,
    ), '430px')
    await page.screenshot({ path: path.join(output, 'mobile-ticket.png'), fullPage: true })
    await page.getByRole('button', { name: '下载 PNG' }).click()
    await page.waitForFunction(() => window.exportsCaptured.some(item => item.filename.endsWith('.png')))
    await page.getByRole('button', { name: '下载 PDF' }).click()
    await page.waitForFunction(() => window.exportsCaptured.some(item => item.filename.endsWith('.pdf')))
    for (const item of await page.evaluate(() => window.exportsCaptured)) {
      const bytes = Buffer.from(item.data, 'base64')
      if (item.filename.endsWith('.png')) assert.equal(bytes.subarray(1, 4).toString(), 'PNG')
      if (item.filename.endsWith('.pdf')) assert.equal(bytes.subarray(0, 5).toString(), '%PDF-')
      await writeFile(path.join(output, item.filename), bytes)
    }
    await page.getByRole('button', { name: '存储到本机' }).click()
    await page.getByRole('button', { name: '存储车票', exact: true }).click()
    await page.getByRole('button', { name: '历史记录', exact: true }).click()
    await page.getByRole('cell', { name: 'G1', exact: true }).waitFor()
    await page.reload()
    await page.getByRole('cell', { name: 'G1', exact: true }).waitFor()
    await page.locator('.el-table__row').first().locator('button').nth(1).click()
    const recordDialog = page.getByRole('dialog', { name: '编辑记录' })
    await recordDialog.waitFor()
    const dialogLayout = await recordDialog.evaluate(element => {
      const bounds = element.getBoundingClientRect()
      const body = element.querySelector('.el-dialog__body')
      return {
        left: bounds.left,
        right: bounds.right,
        height: bounds.height,
        overflow: getComputedStyle(body).overflowY,
      }
    })
    assert.ok(dialogLayout.left >= 0 && dialogLayout.right <= 390)
    assert.ok(dialogLayout.height <= 844)
    assert.equal(dialogLayout.overflow, 'auto')
    await recordDialog.getByRole('spinbutton', { name: /票价/ }).fill('600')
    await recordDialog.getByRole('button', { name: '保存', exact: true }).click()
    await recordDialog.waitFor({ state: 'hidden' })
    await page.reload()
    await page.getByRole('cell', { name: '600', exact: true }).waitFor()
    await page.screenshot({ path: path.join(output, 'mobile-history.png'), fullPage: true })
    await page.goto(address + '/#/user')
    await page.getByRole('heading', { name: '本机车票数据' }).waitFor()
    await page.getByPlaceholder('输入用户名').fill('手机旅客')
    await page.getByRole('button', { name: '保存用户名' }).click()
    await page.getByText('用户名已保存', { exact: true }).waitFor()
    await page.reload()
    await page.getByPlaceholder('输入用户名').waitFor()
    assert.equal(await page.getByPlaceholder('输入用户名').inputValue(), '手机旅客')
    await page.getByRole('button', { name: '车票历史', exact: true }).click()
    await page.getByRole('heading', { name: 'Hi, 手机旅客', exact: true }).waitFor()
    await page.getByRole('button', { name: '数据管理', exact: true }).click()
    await page.getByRole('button', { name: '导出备份', exact: true }).click()
    await page.waitForFunction(() => window.exportsCaptured.some(item => item.filename.endsWith('.json')))
    await page.getByRole('button', { name: '导出数据到Excel' }).click()
    await page.waitForFunction(() => window.exportsCaptured.some(item => item.filename.endsWith('.xlsx')))
    // Each page navigation resets the mock capture array; preserve exports before navigating.
    const exports = await page.evaluate(() => window.exportsCaptured)
    for (const item of exports) await writeFile(path.join(output, item.filename), Buffer.from(item.data, 'base64'))
    assert.equal(exports.filter(item => item.filename.endsWith('.json')).length, 1)
    assert.equal(exports.filter(item => item.filename.endsWith('.xlsx')).length, 1)
    assert.equal(errors.length, 0, errors.join('\n'))
    console.log('PASS: mobile maker, PNG/PDF exports, scrollable record editor, persistent edits and backup/Excel exports; no page errors.')
  } finally { await browser.close(); server.close() }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
