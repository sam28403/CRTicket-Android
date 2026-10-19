import test from 'node:test'
import assert from 'node:assert/strict'
import { addTrainHistory, dailyTrainRecommendations } from './trainDiscovery.js'
import { trainQueryRange } from './trainQueryDate.js'

test('history normalizes codes, deduplicates, and retains ten recent queries', () => {
  let history = []
  for (let i = 1; i <= 12; i++) history = addTrainHistory(history, `G${i}`)
  history = addTrainHistory(history, 'g5')
  assert.equal(history.length, 10)
  assert.equal(history[0], 'G5')
  assert.equal(new Set(history).size, 10)
  assert.ok(!history.includes('G1'))
  assert.deepEqual(addTrainHistory(history, 'invalid'), history)
  assert.equal(addTrainHistory(history, 'G1A/G4')[0], 'G1')
})

const trains = Array.from({ length: 50 }, (_, i) => ({ train: `G${i + 1}`, from: '北京', to: '上海' }))
test('daily recommendations are distinct, stable, and drawn from the supplied daily list', () => {
  const picks = dailyTrainRecommendations(trains, '2026-10-19')
  assert.equal(picks.length, 3)
  assert.equal(new Set(picks.map(row => row.queryTrain)).size, 3)
  assert.deepEqual(picks, dailyTrainRecommendations(trains, '2026-10-19'))
  assert.notDeepEqual(picks, dailyTrainRecommendations(trains, '2026-10-20'))
  assert.ok(picks.every(row => trains.some(train => train.train === row.queryTrain)))
  assert.equal(trains[0].train, 'G1')
})

test('small lists and duplicate offline records never produce repeated trains', () => {
  assert.deepEqual(dailyTrainRecommendations([], '2026-10-19'), [])
  const picks = dailyTrainRecommendations([{ train: 'G1A/G4' }, { train: 'G1B/G4' }], '2026-10-19')
  assert.equal(picks.length, 1)
  assert.equal(picks[0].queryTrain, 'G1')
})

test('recommendation day rolls over exactly at Beijing midnight', () => {
  assert.equal(trainQueryRange(Date.parse('2026-10-19T15:59:59Z')).today, '2026-10-19')
  assert.equal(trainQueryRange(Date.parse('2026-10-19T16:00:00Z')).today, '2026-10-20')
})
