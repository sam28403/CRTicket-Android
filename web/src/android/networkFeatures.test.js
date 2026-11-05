import test from 'node:test'
import assert from 'node:assert/strict'
import { networkEnabled, requireNetwork } from './networkFeatures.js'
import { nativeFetch } from './railway/nativeFetch.js'
import { requestRailway } from './railway/api.js'

test('online features default on and follow the native switch without restarting JavaScript', async () => {
  const original = globalThis.CRTicketDeveloper
  try {
    delete globalThis.CRTicketDeveloper
    assert.equal(networkEnabled(), true)
    requireNetwork()
    let enabled = false
    globalThis.CRTicketDeveloper = { networkEnabled: () => enabled }
    assert.equal(networkEnabled(), false)
    assert.throws(requireNetwork, /联网功能已关闭/)
    await assert.rejects(nativeFetch('https://kyfw.12306.cn/'), /联网功能已关闭/)
    await assert.rejects(requestRailway('/left-ticket'), /联网功能已关闭/)
    await assert.rejects(requestRailway('/train-info?train=G1&date=2026-11-04'), /联网功能已关闭/)
    enabled = true
    assert.equal(networkEnabled(), true)
    requireNetwork()
  } finally {
    if (original === undefined) delete globalThis.CRTicketDeveloper
    else globalThis.CRTicketDeveloper = original
  }
})
