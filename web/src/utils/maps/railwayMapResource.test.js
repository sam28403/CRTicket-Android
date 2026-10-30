import test from 'node:test'
import assert from 'node:assert/strict'
import { railwayMapResource } from './railwayMapResource.js'

test('map gateway retains tile templates and keeps provider paths separate', () => {
  assert.equal(railwayMapResource('https://openrailwaymap.app/rail/{z}/{x}/{y}', 'openrailway'),
    '/railway-map/openrailway/rail/{z}/{x}/{y}')

  assert.equal(railwayMapResource('https://tile.openstreetmap.org/1/2/3.png', 'openrailway'),
    'https://tile.openstreetmap.org/1/2/3.png')
  assert.throws(() => railwayMapResource('/style.json', 'unknown'))
})

test('removed map providers cannot generate resource requests', () => {
  assert.throws(() => railwayMapResource('/style.json', 'cnrail'))
})
