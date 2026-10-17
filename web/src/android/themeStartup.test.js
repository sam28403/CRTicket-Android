import test from 'node:test'
import assert from 'node:assert/strict'
import { initializeTheme, useTheme } from '../composables/useTheme.js'

test('startup and mode changes persist the choice even when the resolved color stays the same', async () => {
  const choices = []
  const appearances = []
  const classes = new Set()
  globalThis.window = {
    CRTicketAndroid: {
      systemDarkMode: () => true,
      setThemePreference: value => choices.push(value),
      setThemeAppearance: (...value) => appearances.push(value),
    },
    matchMedia: () => ({ matches: false, addEventListener() {} }),
    addEventListener() {},
  }
  globalThis.document = {
    documentElement: {
      classList: {
        toggle(name, enabled) {
          if (enabled) classes.add(name)
          else classes.delete(name)
        },
      },
      style: { setProperty() {}, removeProperty() {} },
    },
    addEventListener() {},
  }
  globalThis.localStorage = {
    getItem: () => 'classic-system',
    setItem() {},
  }
  try {
    initializeTheme()
    assert.deepEqual(choices, ['classic-system'])
    assert.deepEqual(appearances, [[true, false]])
    assert.equal(classes.has('dark'), true)

    const { preference } = useTheme()
    preference.value = 'classic-dark'
    preference.value = 'classic-system'
    assert.deepEqual(choices, ['classic-system', 'classic-dark', 'classic-system'])
    assert.equal(appearances.length, 1)
  } finally {
    delete globalThis.window
    delete globalThis.document
    delete globalThis.localStorage
  }
})
