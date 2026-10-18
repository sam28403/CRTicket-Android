import test from 'node:test'
import assert from 'node:assert/strict'
import { createNativeRouteReporter, createNativeRouteSync } from './nativeRouteSync.js'

test('query form replacements retain one entry while links push distinct entries', () => {
  let position = 0
  const calls = []
  const report = createNativeRouteReporter({
    readPosition: () => position,
    report: (route, replaced) => calls.push([route, replaced]),
  })
  position++
  report('/train')
  report('/train?train=G1')
  position++
  report('/station?station=北京南')
  position++
  report('/train?train=G3')
  position--
  report('/station?station=北京南')
  assert.deepEqual(calls, [
    ['/train', false],
    ['/train?train=G1', true],
    ['/station?station=北京南', false],
    ['/train?train=G3', false],
    ['/station?station=北京南', false],
  ])
})

function fixture(current, previous) {
  const calls = []
  const router = {
    currentRoute: { value: { path: current.split('?')[0], fullPath: current } },
    isReady: async () => {},
    push: route => calls.push(['push', route]),
    replace: route => calls.push(['replace', route]),
    back: () => calls.push(['back']),
  }
  return {
    calls,
    sync: createNativeRouteSync({
      router, readBack: () => previous,
      acknowledge: route => calls.push(['acknowledge', route]),
    }),
  }
}

test('a native settings entry goes straight to its destination', async () => {
  const f = fixture('/settings', '/home')
  await f.sync('/monitor/settings', false)
  assert.deepEqual(f.calls, [['push', '/monitor/settings']])
})

test('native back reuses web history instead of inserting another settings page', async () => {
  const f = fixture('/monitor/settings', '/settings')
  await f.sync('/settings', true)
  assert.deepEqual(f.calls, [['back']])
})

test('a restored native back stack replaces an incompatible web destination', async () => {
  const f = fixture('/monitor/settings', '/home')
  await f.sync('/settings', true)
  assert.deepEqual(f.calls, [['replace', '/settings']])
})

test('matching destinations acknowledge the native request without a navigation loop', async () => {
  const f = fixture('/monitor/settings', '/settings')
  await f.sync('/monitor/settings', false)
  assert.deepEqual(f.calls, [['acknowledge', '/monitor/settings']])
})

test('station and train query arguments identify separate native destinations', async () => {
  const f = fixture('/station?station=北京南', '/station?station=上海虹桥')
  await f.sync('/station?station=上海虹桥', true)
  assert.deepEqual(f.calls, [['back']])
})

test('changing only query arguments still loads the destination data', async () => {
  const f = fixture('/train?train=G1', '/home')
  await f.sync('/train?train=G3', false)
  assert.deepEqual(f.calls, [['push', '/train?train=G3']])
})
