// Native Navigation owns the UI stack; the web router mirrors data-page locations.
export function createNativeRouteSync({
  router,
  acknowledge,
  readBack = () => globalThis.window?.history.state?.back,
}) {
  return async (route, back) => {
    await router.isReady()
    const current = router.currentRoute.value.fullPath || router.currentRoute.value.path
    if (current === route) {
      acknowledge(route)
      return
    }
    if (!back) return router.push(route)
    const previous = String(readBack() || '')
    if (previous === route) router.back()
    else return router.replace(route)
  }
}
