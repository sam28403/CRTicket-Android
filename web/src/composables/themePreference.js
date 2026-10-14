export const appearanceModes = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '亮色' },
  { value: 'dark', label: '暗色' },
]

export function normalizeTheme(value) {
  if (['system', 'light', 'dark'].includes(value)) return `contrast-${value}`
  return /^(classic|contrast)-(system|light|dark)$/.test(value) ? value : 'classic-system'
}

export function appearancePreference(mode, pureBlack) {
  if (!appearanceModes.some(option => option.value === mode)) throw new Error('无效的主题模式')
  return `${pureBlack ? 'contrast' : 'classic'}-${mode}`
}
