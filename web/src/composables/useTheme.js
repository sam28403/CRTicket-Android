import { computed, ref, watch } from 'vue'
import { normalizeTheme } from './themePreference.js'
import { rememberNativePalette, applyNativePalette } from './nativePalette.js'

const key = 'crticket-theme'
const normalize = normalizeTheme
const preference = ref('classic-system')
const systemDark = ref(false)
const isDark = computed(() =>
  preference.value.endsWith('-dark') || (preference.value.endsWith('-system') && systemDark.value))
const highContrast = computed(() => preference.value.startsWith('contrast-'))
const resolvedTheme = computed(() =>
  `${highContrast.value ? 'contrast' : 'classic'}-${isDark.value ? 'dark' : 'light'}`)
let initialized = false

function readSystemDark(media) {
  const native = window.CRTicketAndroid
  if (typeof native?.systemDarkMode === 'function') {
    return Boolean(native.systemDarkMode())
  }
  return media.matches
}

export function initializeTheme() {
  if (initialized) return
  initialized = true
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  systemDark.value = readSystemDark(media)
  try {
    const saved = localStorage.getItem(key)
    preference.value = normalize(saved)
  } catch { /* 受限存储时仍可在当前页面切换主题。 */ }
  const refreshSystem = () => { systemDark.value = readSystemDark(media) }
  media.addEventListener('change', refreshSystem)
  window.addEventListener('crticket-system-theme', event => {
    if (typeof event.detail?.dark === 'boolean') systemDark.value = event.detail.dark
  })
  window.addEventListener('crticket-native-palette', event => {
    rememberNativePalette(event.detail)
    applyNativePalette(highContrast.value)
  })
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) refreshSystem()
  })
  window.addEventListener('focus', refreshSystem)
  window.addEventListener('storage', event => {
    if (event.key === key || event.key === null) preference.value = normalize(event.newValue)
  })
  watch(preference, value => {
    try { localStorage.setItem(key, value) } catch { /* 存储不可用时保留内存设置。 */ }
    window.CRTicketAndroid?.setThemePreference?.(value)
  }, { immediate: true, flush: 'sync' })
  watch(resolvedTheme, () => {
    const dark = isDark.value
    document.documentElement.classList.toggle('high-contrast', highContrast.value)
    document.documentElement.classList.toggle('dark', dark)
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
    applyNativePalette(highContrast.value)
    const native = window.CRTicketAndroid
    if (typeof native?.setThemeAppearance === 'function') {
      native.setThemeAppearance(dark, highContrast.value)
    } else native?.setAppearance?.(dark)
  }, { immediate: true, flush: 'sync' })
}

export function useTheme() {
  return { preference, isDark, highContrast, resolvedTheme }
}
