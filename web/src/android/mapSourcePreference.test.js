import test from 'node:test'
import assert from 'node:assert/strict'
import { nextTick } from 'vue'

test('map selection refreshes immediately after backup restoration and keeps normal selection persistent', async () => {
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window')
  const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
  const values = new Map([['mapSource', 'hot']])
  const target = new EventTarget()
  Object.defineProperty(globalThis, 'window', { configurable: true, value: target })
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  } })
  try {
    const { useMapSource } = await import('../composables/useMapSource.js')
    const selected = useMapSource()
    assert.equal(selected.value, 'hot')
    values.set('mapSource', 'france')
    target.dispatchEvent(new Event('crticket-backup-restored'))
    assert.equal(selected.value, 'france')
    await nextTick()
    selected.value = 'transport'
    await nextTick()
    assert.equal(values.get('mapSource'), 'transport')
    values.set('mapSource', 'topo')
    target.dispatchEvent(Object.assign(new Event('storage'), { key: 'mapSource' }))
    assert.equal(selected.value, 'topo')
    await nextTick()
  } finally {
    if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow)
    else delete globalThis.window
    if (originalStorage) Object.defineProperty(globalThis, 'localStorage', originalStorage)
    else delete globalThis.localStorage
  }
})
