import test from 'node:test'
import assert from 'node:assert/strict'
import { validateHistoryTicket } from './historyValidation.js'
import { ticketDistanceError, ticketPriceError } from './ticketNumericValidation.js'

const ticket = {
  number: 'E123456789', trainNo: 'G25', from: '北京南', to: '上海虹桥',
  date: '2026年10月16日', time: '08:00', price: '553', distance: '1318',
  seatType: '二等座', seatNo: '01车01A号',
  sellPlace: '北京南站',
}

test('历史记录接受半元票价、可选里程及中英文日期格式', () => {
  assert.equal(validateHistoryTicket({ ...ticket, price: '0.5', distance: '' }), '')
  assert.equal(validateHistoryTicket({ ...ticket, date: '2026-10-16' }), '')
  assert.equal(validateHistoryTicket({ ...ticket, trainNo: 'g25' }), '')
})

test('历史记录拒绝非法车次、缺失字段和空白票价', () => {
  assert.equal(validateHistoryTicket({ ...ticket, trainNo: 'bad' }), '请输入正确的车次')
  assert.equal(validateHistoryTicket({ ...ticket, seatNo: '  ' }), '请填写完整车票信息')
  assert.equal(validateHistoryTicket({ ...ticket, sellPlace: ' ' }), '请填写完整车票信息')
  assert.equal(validateHistoryTicket({ ...ticket, price: '' }), '票价须大于0，且为整数或以.5结尾')
})

test('历史记录拒绝负数、无穷数和非数字里程', () => {
  for (const price of ['-1', 'Infinity', 'NaN']) {
    assert.equal(validateHistoryTicket({ ...ticket, price }), '票价须大于0，且为整数或以.5结尾')
  }
  for (const distance of ['-1', 'Infinity', 'abc']) {
    assert.equal(validateHistoryTicket({ ...ticket, distance }), '里程须为正整数')
  }
})

test('历史记录检查日期存在性、闰年和24小时时间范围', () => {
  for (const date of ['2026年02月29日', '2026-04-31', '2026-13-01', 'bad']) {
    assert.equal(validateHistoryTicket({ ...ticket, date }), '请选择有效的开车日期')
  }
  assert.equal(validateHistoryTicket({ ...ticket, date: '2028年02月29日' }), '')
  for (const time of ['24:00', '12:60', '1:05']) {
    assert.equal(validateHistoryTicket({ ...ticket, time }), '请选择有效的开车时间')
  }
})

test('票价为正数且以半元为步长，积分为以00或50结尾的正整数', () => {
  for (const price of ['0.5', '.5', '1', '12.5', '12.50', '12.0', '10000000']) {
    assert.equal(ticketPriceError(price), '', price)
  }
  for (const price of ['0', '0.0', '-1', '12.1', '12.51', '1e2', '10000000.5']) {
    assert.notEqual(ticketPriceError(price), '', price)
  }
  for (const points of ['50', '100', '150', '55350']) {
    assert.equal(ticketPriceError(points, true), '', points)
    assert.equal(validateHistoryTicket({ ...ticket, price: points, useCredit: '1' }), '')
  }
  for (const points of ['0', '1', '51', '100.5', '100.0', '-50']) {
    assert.notEqual(ticketPriceError(points, true), '', points)
    assert.notEqual(validateHistoryTicket({ ...ticket, price: points, useCredit: 1 }), '')
  }
})

test('可不填里程，填写后必须是大于0的整数', () => {
  for (const value of ['', '1', '1318']) assert.equal(ticketDistanceError(value), '', value)
  for (const value of ['0', '-1', '1.0', '1.5', '1e2', '1,000', ' ']) {
    assert.notEqual(ticketDistanceError(value), '', value)
  }
})
