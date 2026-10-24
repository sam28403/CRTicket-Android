import test from 'node:test'
import assert from 'node:assert/strict'
import { validTrackSegments } from './recordedTrack.js'

test('track segments retain recording gaps and reject invalid points without drawing across them', () => {
  const a = [39.9, 116.4]
  const b = [39.91, 116.41]
  const c = [39.92, 116.42]
  assert.deepEqual(validTrackSegments([[a, b], [c]]), [[a, b], [c]])
  assert.deepEqual(validTrackSegments([[a, [NaN, 116], b, [90, 10], c]]), [[a], [b], [c]])
  assert.deepEqual(validTrackSegments([[a, [39, 181], b]]), [[a], [b]])
  assert.deepEqual(validTrackSegments(null), [])
  assert.deepEqual(validTrackSegments([[[39, 116, 1000], [39.1, 116.1, 2000]]]),
    [[[39, 116, 1000], [39.1, 116.1, 2000]]])
})
