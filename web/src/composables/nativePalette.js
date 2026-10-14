// Inline values are only applied in classic mode; contrast mode retains its existing palette.
let palette = {}
const keys = new Set()

export function rememberNativePalette(value) {
  palette = value || {}
}

export function applyNativePalette(highContrast) {
  const style = document.documentElement.style
  for (const key of keys) style.removeProperty(key)
  keys.clear()
  if (highContrast) return
  for (const [key, value] of Object.entries(palette)) {
    if (!key.startsWith('--') || !/^#[0-9a-f]{6}$/i.test(value)) continue
    style.setProperty(key, value)
    keys.add(key)
  }
}
