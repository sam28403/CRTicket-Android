import test from 'node:test'
import assert from 'node:assert/strict'
import { getTrainQueryNumberError } from './trainQueryValidation.js'

test('在线查询复用已启用 N、V 号段的车次规则', () => {
  for (const train of ['G1', 'g97', '1461', '50001', 'N1', 'N1998', 'V1', 'V6501', 'V7998']) {
    assert.equal(getTrainQueryNumberError(train), '')
  }
  for (const train of ['N1999', 'V6000', 'X1', 'G12345', 'G1/G2', 'G1B', ' G1']) {
    assert.equal(getTrainQueryNumberError(train), '请输入正确的车次')
  }
  assert.equal(getTrainQueryNumberError(''), '请输入车次')
})

test('离线列表和通知入口保留完整换号记录，方案后缀不绕过车次规则', () => {
  assert.equal(getTrainQueryNumberError('G100/G97', true, 'G100/G97B'), '')
  assert.equal(getTrainQueryNumberError('G97/100', true, 'G97/100B'), '')
  assert.equal(getTrainQueryNumberError('1461/1462', true), '')
  assert.equal(getTrainQueryNumberError('N1/N2', true), '')
  assert.equal(getTrainQueryNumberError('V6501/6504', true, 'V6501/6504B'), '')
  assert.equal(getTrainQueryNumberError('G97', true, 'G100/G97B'), '')
  for (const [train, record] of [
    ['N1999', 'N1999'], ['G97/X1', 'G97/X1B'], ['G97/', 'G97/'], ['G12345', 'G12345B'],
  ]) {
    assert.notEqual(getTrainQueryNumberError(train, true, record), '')
  }
  assert.notEqual(getTrainQueryNumberError('G97B', true), '')
})
