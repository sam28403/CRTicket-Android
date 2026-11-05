import test from 'node:test'
import assert from 'node:assert/strict'
import { createTrainListSnapshot } from './trainListSnapshot.js'

test('repeat native snapshots reuse rows but keep loading and error updates', () => {
  const encode = createTrainListSnapshot()
  const rows = [{ train: 'G1' }]
  const first = { date: '2026-11-04', result: { trains: rows }, loading: false }
  assert.equal(encode(first), first)
  const loading = { ...first, result: null, loading: true }
  assert.equal(encode(loading), loading)
  assert.deepEqual(encode(first), { ...first, result: null, reuseRows: true })
  const error = { ...loading, loading: false, error: 'unavailable' }
  assert.equal(encode(error), error)
  assert.equal(first.result.trains, rows)
})

test('new dates, visibility changes and replacement databases send complete rows', () => {
  const encode = createTrainListSnapshot()
  const first = { date: '2026-11-04', result: { trains: [{ train: 'G1' }] } }
  encode(first)
  const imported = { ...first, result: { trains: [{ train: 'G1' }] } }
  assert.equal(encode(imported), imported)
  const date = { ...imported, date: '2026-11-05' }
  assert.equal(encode(date), date)
  const visibility = { ...date, result: { trains: [{ train: 'G1' }, { train: 'G2', notRunning: true }] } }
  assert.equal(encode(visibility), visibility)
})
