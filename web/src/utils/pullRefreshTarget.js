const protectedTargets = [
  'input', 'textarea', 'select', '[contenteditable]', '[role="combobox"]', '[role="slider"]',
  '.el-select', '.el-autocomplete', '.el-date-editor', '.el-input',
  'canvas', '.leaflet-container', '.el-slider', '.el-switch', '[data-pull-refresh-disabled]',
].join(',')

export function isPullRefreshAtTop(target, document) {
  if (!target || document.scrollingElement.scrollTop > 2) return false
  for (let element = target; element; element = element.parentElement) {
    if (element.scrollTop > 2) return false
  }
  return true
}

export function canStartPullRefresh(target, document, window) {
  if (!target?.closest || !isPullRefreshAtTop(target, document)) return false
  if (target.closest(protectedTargets)) return false
  if (window.getSelection()?.isCollapsed === false) return false
  const overlays = document.querySelectorAll('.el-overlay, [role="dialog"], .el-popper')
  if ([...overlays].some(element => element.getClientRects().length
    && window.getComputedStyle(element).visibility !== 'hidden')) return false

  return true
}
