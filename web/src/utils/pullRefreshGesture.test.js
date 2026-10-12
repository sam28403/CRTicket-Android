import test from 'node:test'
import assert from 'node:assert/strict'
import { createPullRefreshGesture } from './pullRefreshGesture.js'

test('a short downward pull at the top triggers on release without a time gate', () => {
  const gesture = createPullRefreshGesture()
  gesture.begin(100, 40, true)
  assert.equal(gesture.move(103, 60).engaged, true)
  assert.equal(gesture.finish(), true)
  assert.equal(gesture.finish(), false)
})

test('scrolling to the top requires a fresh downward gesture to refresh', () => {
  const gesture = createPullRefreshGesture()
  gesture.begin(100, 40, false)
  assert.equal(gesture.move(100, 300).engaged, false)
  assert.equal(gesture.finish(), false)
  gesture.begin(100, 40, true)
  gesture.move(100, 60)
  assert.equal(gesture.finish(), true)
})

test('tap jitter does not refresh', () => {
  const gesture = createPullRefreshGesture()
  gesture.begin(100, 40, true)
  assert.equal(gesture.move(103, 45).engaged, false)
  assert.equal(gesture.finish(), false)
})

test('upward and horizontal browsing permanently cancel that gesture', () => {
  for (const [x, y] of [[100, 10], [160, 45], [145, 80]]) {
    const gesture = createPullRefreshGesture()
    gesture.begin(100, 40, true)
    assert.equal(gesture.move(x, y).engaged, false)
    gesture.move(100, 300)
    assert.equal(gesture.finish(), false)
  }
})

test('pinch gestures and touch cancellation never refresh', () => {
  const gesture = createPullRefreshGesture()
  gesture.begin(100, 40, true)
  gesture.move(100, 250, 2)
  assert.equal(gesture.finish(), false)
  gesture.begin(100, 40, true)
  gesture.move(100, 250)
  gesture.cancel()
  assert.equal(gesture.finish(), false)
})

test('returning to the starting position before release cancels refresh', () => {
  const gesture = createPullRefreshGesture()
  gesture.begin(100, 40, true)
  gesture.move(100, 250)
  gesture.move(100, 40)
  assert.equal(gesture.finish(), false)
})
