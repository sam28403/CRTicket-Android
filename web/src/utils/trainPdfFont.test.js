import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { PDFDocument, PDFName, decodePDFRawStream } from 'pdf-lib'
import fontkit from '@pdf-lib/fontkit'
import { buildTrainInfoPdf, trainPdfRows, trainPdfTitle } from './trainInfoPdf.js'

const mobileBytes = await readFile(new URL('../../public/fonts/train-pdf.ttf', import.meta.url))
const originalBytes = await readFile(new URL('../../public/google_sans_rounded_regular.ttf', import.meta.url))
const originalFont = fontkit.create(originalBytes)
const stationNames = [
  '上海松江', '嘉兴', '杭州南', '诸暨', '义乌', '金华', '衢州', '上饶',
  '鹰潭', '南昌', '九江', '黄石', '武昌', '随州', '襄阳', '南阳',
  '漯河', '郴州', '亳州', '鄠邑', '焉耆', '鲅鱼圈', '呼和浩特东', '锡林呼都嘎',
]

for (const count of [16, 60]) {
  test(`${count} 站导出的每个嵌入字形均与电脑版原字体一致`, async () => {
    const result = {
      train: 'K1127/K1126',
      date: '2026-10-10',
      stops: Array.from({ length: count }, (_, index) => ({
        no: index + 1,
        train: index < count / 2 ? 'K1127' : 'K1126',
        station: stationNames[index % stationNames.length],
        arrival: index ? '12:52' : null,
        departure: index === count - 1 ? null : '18:22',
        stay: index === 0 || index === count - 1 ? null : 3,
        mileage: index * 54,
      })),
    }
    const { bytes } = await buildTrainInfoPdf(result, mobileBytes)
    const document = await PDFDocument.load(bytes)
    const fonts = document.getPages()[0].node.Resources().lookup(PDFName.of('Font'))
    const pdfFont = fonts.lookup(fonts.keys()[0])
    const descendant = pdfFont.lookup(PDFName.of('DescendantFonts')).lookup(0)
    const descriptor = descendant.lookup(PDFName.of('FontDescriptor'))
    const fontStream = descriptor.lookup(PDFName.of('FontFile2'))
    const embedded = fontkit.create(decodePDFRawStream(fontStream).decode())
    const unicodeStream = pdfFont.lookup(PDFName.of('ToUnicode'))
    const cmap = new TextDecoder().decode(decodePDFRawStream(unicodeStream).decode())
    const mappings = [...cmap.split('beginbfchar')[1].split('endbfchar')[0]
      .matchAll(/<([0-9A-F]+)>\s*<([0-9A-F]+)>/g)]
    const mappedCharacters = new Set()

    // Extractable text alone passes for the broken PDF: check actual glyph outlines.
    for (const [, glyphHex, unicodeHex] of mappings) {
      const codePoint = Number.parseInt(unicodeHex, 16)
      const character = String.fromCodePoint(codePoint)
      const original = originalFont.glyphForCodePoint(codePoint)
      assert.notEqual(original.id, 0, `原字体缺少 ${character}`)
      const actual = embedded.getGlyph(Number.parseInt(glyphHex, 16))
      assert.deepEqual(actual.path.commands, original.path.commands, `PDF 字形损坏：${character}`)
      mappedCharacters.add(character)
    }

    const expectedText = [
      trainPdfTitle(result), '站序站名到站时间出发时间停留时间里程',
      ...trainPdfRows(result).flat(),
    ].join('')
    for (const character of expectedText) {
      assert.ok(mappedCharacters.has(character), `PDF 未嵌入 ${character}`)
    }
    assert.ok(bytes.length < 100_000, '应仅嵌入当前时刻表用到的字形')
  })
}
