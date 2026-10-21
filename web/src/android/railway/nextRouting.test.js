import test from 'node:test'
import assert from 'node:assert/strict'
import { nextRoutingTrain } from './nextRouting.js'

const date = '2026-10-21'
function database(trains = ['G32', 'G1', 'G1958/5']) {
  return {
    version: '20261024', trains: new Map(),
    routing: [{ id: 1, start: 20260101, end: 20991231, model: '', trains }],
  }
}

test('下一担当按交路顺序选择一张卡片，支持换号与末项回到首项', () => {
  assert.equal(nextRoutingTrain(database(), 'G32', date).train, 'G1')
  assert.equal(nextRoutingTrain(database(), 'G1', date).train, 'G1958/5')
  assert.equal(nextRoutingTrain(database(), 'G1955', date).train, 'G32')
  assert.equal(nextRoutingTrain(database(['G1']), 'G1', date).train, 'G1')
})

test('无交路隐藏模块，特殊担当保留卡片但禁用详情', () => {
  assert.equal(nextRoutingTrain(database(), 'G999', date), null)
  assert.equal(nextRoutingTrain({ trains: new Map() }, 'G1', date), null)
  const special = nextRoutingTrain(database(['G1', '0D55']), 'G1', date)
  assert.equal(special.train, '0D55')
  assert.equal(special.clickable, false)
})
