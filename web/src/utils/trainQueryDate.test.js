import test from 'node:test'
import assert from 'node:assert/strict'
import { isTrainQueryDateInRange, trainQueryRange } from './trainQueryDate.js'

test('车次查询使用北京时间 T-2 至 T+14，包含边界并拒绝 T+15', () => {
  const now = Date.parse('2026-09-28T08:00:00+08:00')
  assert.deepEqual(trainQueryRange(now), {
    today: '2026-09-28', min: '2026-09-26', max: '2026-10-12',
  })
  for (const day of ['2026-09-26', '2026-10-12']) {
    assert.equal(isTrainQueryDateInRange(day, now), true)
  }
  for (const day of ['2026-09-25', '2026-10-13']) {
    assert.equal(isTrainQueryDateInRange(day, now), false)
  }
  assert.deepEqual(trainQueryRange(Date.parse('2026-12-31T16:00:00Z')), {
    today: '2027-01-01', min: '2026-12-30', max: '2027-01-15',
  })
})
