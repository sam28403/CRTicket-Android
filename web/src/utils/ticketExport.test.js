import assert from 'node:assert/strict'
import test from 'node:test'
import { PDFDocument } from 'pdf-lib'
import { buildTicketPdf } from './ticketExport.js'

test('车票 PDF 按票面实际尺寸导出', async () => {
  const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0ZkAAAAASUVORK5CYII='
  const pdf = await PDFDocument.load(await buildTicketPdf(png))
  assert.equal(pdf.getPageCount(), 1)
  const { width, height } = pdf.getPage(0).getSize()
  assert.ok(Math.abs(width - 8.6 * 72 / 2.54) < 0.01)
  assert.ok(Math.abs(height - 5.4 * 72 / 2.54) < 0.01)
})
