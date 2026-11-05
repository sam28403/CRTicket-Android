import { readonly, ref } from 'vue'
import { networkEnabled } from '../android/networkFeatures.js'

const enabled = ref(networkEnabled())
globalThis.window?.addEventListener('crticket-network-changed', () => {
  enabled.value = networkEnabled()
})

export function useNetworkFeatures() {
  return readonly(enabled)
}
