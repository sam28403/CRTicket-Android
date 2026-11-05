import { readonly, ref } from 'vue'

const online = readonly(ref(true))
const hidden = readonly(ref(false))
export const useNetworkFeatures = () => online
export const useTimetableVisibility = () => hidden
