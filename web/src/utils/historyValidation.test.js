import test from 'node:test'
import assert from 'node:assert/strict'
import { validateHistoryTicket } from './historyValidation.js'

const ticket = {
  number: 'E123456789', trainNo: 'G25', from: '北京南', to: '上海虹桥',
  date: '2026年10月16日', time: '08:00', price: '553', distance: '1318',
  seatType: '二等座', seatNo: '01车01A号',
}

test('历史记录接受零元票价、可选里程及中英文日期格式', () => {
  assert.equal(validateHistoryTicket({ ...ticket, price: '0', distance: '' }), '')
  assert.equal(validateHistoryTicket({ ...ticket, date: '2026-10-16' }), '')
  assert.equal(validateHistoryTicket({ ...ticket, trainNo: 'g25' }), '')
})

test('历史记录拒绝非法车次、缺失字段和空白票价', () => {
  assert.equal(validateHistoryTicket({ ...ticket, trainNo: 'bad' }), '请输入正确的车次')
  assert.equal(validateHistoryTicket({ ...ticket, seatNo: '  ' }), '请填写完整车票信息')
  assert.equal(validateHistoryTicket({ ...ticket, price: '' }), '请输入有效的票价或积分')
})

test('历史记录拒绝负数、无穷数和非数字里程', () => {
  for (const price of ['-1', 'Infinity', 'NaN']) {
    assert.equal(validateHistoryTicket({ ...ticket, price }), '请输入有效的票价或积分')
  }
  for (const distance of ['-1', 'Infinity', 'abc']) {
    assert.equal(validateHistoryTicket({ ...ticket, distance }), '请输入有效的里程')
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
