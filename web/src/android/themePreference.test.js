import test from 'node:test'
import assert from 'node:assert/strict'
import { appearancePreference, normalizeTheme } from '../composables/themePreference.js'
import { applyNativePalette, rememberNativePalette } from '../composables/nativePalette.js'

test('theme modes and black-background preference preserve older saved choices', () => {
  for (const mode of ['system', 'light', 'dark']) {
    assert.equal(appearancePreference(mode, false), `classic-${mode}`)
    assert.equal(appearancePreference(mode, true), `contrast-${mode}`)
    assert.equal(normalizeTheme(`classic-${mode}`), `classic-${mode}`)
    assert.equal(normalizeTheme(mode), `contrast-${mode}`)
  }
  assert.equal(normalizeTheme(null), 'classic-system')
  assert.equal(normalizeTheme('bad'), 'classic-system')
  assert.throws(() => appearancePreference('automatic', false))
})

test('system palette updates Web surfaces and yields to the original high-contrast theme', () => {
  const values = new Map()
  globalThis.document = { documentElement: { style: {
    setProperty: (key, value) => values.set(key, value),
    removeProperty: key => values.delete(key),
  } } }
  try {
    rememberNativePalette({ '--app-page': '#102030', '--app-text': '#EEF0FA' })
    applyNativePalette(false)
    assert.equal(values.get('--app-page'), '#102030')
    applyNativePalette(true)
    assert.equal(values.size, 0)
    rememberNativePalette({ '--app-page': '#304050' })
    applyNativePalette(false)
    assert.equal(values.get('--app-page'), '#304050')
    assert.equal(values.has('--app-text'), false)
  } finally {
    delete globalThis.document
  }
})
