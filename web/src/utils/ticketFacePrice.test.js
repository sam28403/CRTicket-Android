import test from 'node:test'
import assert from 'node:assert/strict'
import { ticketFacePrice, buildTicketPayload } from './ticketShared.js'
import { switchHistoryCreditPrice } from './historyCredit.js'

test('补登编辑切换积分开关时换算数值，反复切换不产生小数误差', () => {
  assert.equal(switchHistoryCreditPrice(500, true), 50000)
  assert.equal(switchHistoryCreditPrice(50000, false), 500)
  assert.equal(switchHistoryCreditPrice(0.29, true), 29)
  let amount = 500.01
  for (let i = 0; i < 10; i++) {
    amount = switchHistoryCreditPrice(switchHistoryCreditPrice(amount, true), false)
  }
  assert.equal(amount, 500.01)
  assert.equal(switchHistoryCreditPrice('', true), '')
  assert.equal(switchHistoryCreditPrice(0, false), 0)
})

test('积分票面按100积分兑换1元，现金票价保持原值', () => {
  assert.equal(ticketFacePrice(50000, true), 500)
  assert.equal(ticketFacePrice('50001', true), 500.01)
  assert.equal(ticketFacePrice(100, true), 1)
  assert.equal(ticketFacePrice(0, true), 0)
  assert.equal(ticketFacePrice(50000, false), 50000)
  assert.equal(ticketFacePrice('500.00', false), '500.00')
})

test('空积分输入不显示零元，非法值不显示NaN', () => {
  assert.equal(ticketFacePrice('', true), '')
  assert.equal(ticketFacePrice(null, true), null)
  assert.equal(ticketFacePrice('invalid', true), '')
})

test('票面换算不修改记录中的原始积分，编辑后不会重复除以100', () => {
  const ticket = { price: 50000 }
  const payload = buildTicketPayload(ticket, { userId: 'device', useCredit: true })
  assert.equal(payload.price, 50000)
  assert.equal(payload.use_credit, 1)
  assert.equal(ticketFacePrice(payload.price, payload.use_credit === 1), 500)
  assert.equal(ticket.price, 50000)
})
