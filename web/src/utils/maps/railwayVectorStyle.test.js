import test from 'node:test'
import assert from 'node:assert/strict'
import { railwayVectorStyle } from './railwayVectorStyle.js'

test('official relative vector resources resolve to the provider without inventing glyph URLs', () => {
  const input = {
    version: 8, sources: { rail: { type: 'vector', url: '/rail' } },
    sprite: [{ id: 'default', url: '/sprite/symbols' }], layers: [],
  }
  const style = railwayVectorStyle(input)
  assert.equal(style.sources.rail.url, 'https://openrailwaymap.app/rail')
  assert.equal(style.sprite[0].url, 'https://openrailwaymap.app/sprite/symbols')
  assert.equal(style.glyphs, undefined)
  assert.equal(style.layers[0].source, 'background')
  assert.equal(input.sources.rail.url, '/rail')
})
