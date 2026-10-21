import test from 'node:test'
import assert from 'node:assert/strict'
import { createTrainQueryCache } from './trainQueryCache.js'

test('返回上一车次恢复同一结果，日期和在线/离线来源独立', () => {
  const cache = createTrainQueryCache()
  const original = { result: { train: 'G1' } }
  cache.set('G1', '2026-10-21', 'llt', original)
  cache.set('G32', '2026-10-21', 'llt', { result: { train: 'G32' } })
  assert.equal(cache.get(' g1 ', '2026-10-21', 'llt'), original)
  assert.equal(cache.get('G1', '2026-10-22', 'llt'), undefined)
  assert.equal(cache.get('G1', '2026-10-21', ''), undefined)
})

test('手动查询的新结果替换旧结果，缓存数量有上限', () => {
  const cache = createTrainQueryCache(2)
  cache.set('G1', '2026-10-21', '', { old: true })
  cache.set('G1', '2026-10-21', undefined, { fresh: true })
  assert.deepEqual(cache.get('G1', '2026-10-21', ''), { fresh: true })
  cache.set('G2', '2026-10-21', '', {})
  cache.set('G3', '2026-10-21', '', {})
  assert.equal(cache.get('G1', '2026-10-21', ''), undefined)
})
