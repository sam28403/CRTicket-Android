import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeMapSource, rasterCandidates } from './mapSources.js'

test('automatic selection starts with HOT and tries each available raster source once', () => {
  const candidates = rasterCandidates('auto')
  assert.deepEqual(candidates.map(source => source.id), ['hot', 'osm', 'france', 'topo'])
  assert.equal(new Set(candidates.map(source => source.url)).size, candidates.length)
  assert.ok(candidates.every(source => source.url.startsWith('https://')))
  assert.ok(candidates.every(source => source.attribution.includes('OpenStreetMap')))
})

test('manual selection never silently switches to a different provider', () => {
  for (const source of ['hot', 'osm', 'france', 'topo']) {
    assert.deepEqual(rasterCandidates(source).map(candidate => candidate.id), [source])
  }
  assert.deepEqual(rasterCandidates('transport'), [])
  assert.equal(normalizeMapSource('invalid'), 'auto')
  assert.deepEqual(rasterCandidates('invalid'), rasterCandidates('auto'))
})
