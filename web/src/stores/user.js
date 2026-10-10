import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api.js'
import { configuration, deviceMode } from '@/android/platform.js'
import { createLocalApi } from '@/android/localApi.js'

export const useUserStore = defineStore('user', () => {
    // Retain the existing views' property name; this app always has its default owner.
    const isLogin = ref(true)
    const scope = deviceMode ? 'device' : configuration.backend
    const ownerKey = `crticket-default-owner:${scope}`
    let checkedAt = 0
    let pending = null

    function remember(user) {
        localStorage.setItem(ownerKey, JSON.stringify(user))
        localStorage.setItem('user', JSON.stringify(user))
        localStorage.setItem('login', 'true')
        isLogin.value = true
    }

    function init() {
        let saved = null
        try {
            saved = JSON.parse(localStorage.getItem(ownerKey) || 'null')
        } catch {
            localStorage.removeItem(ownerKey)
        }
        const user = deviceMode
            ? createLocalApi(localStorage).owner()
            : saved || { id: 1, username: '默认用户', avatar: null }
        remember(user)
    }

    async function checkSession() {
        if (deviceMode || Date.now() - checkedAt < 30000) return true
        if (!pending) {
            pending = api.get('/user/session').then(response => {
                if (!response.data.success || !response.data.user?.id) {
                    throw new Error('后端默认用户初始化失败')
                }
                remember(response.data.user)
                checkedAt = Date.now()
                return true
            }).finally(() => { pending = null })
        }
        return pending
    }

    return { isLogin, init, checkSession, setUser: remember }
})
