import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'

test('default owner restores without login and shares a cached backend session lookup', async t => {
    const storage = new Map()
    const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
    Object.defineProperty(globalThis, 'localStorage', {
        configurable: true,
        value: {
            getItem: key => storage.get(key) ?? null,
            setItem: (key, value) => storage.set(key, String(value)),
            removeItem: key => storage.delete(key),
        },
    })
    const vite = await createServer({
        server: { middlewareMode: true },
        appType: 'custom',
    })
    const { useUserStore } = await vite.ssrLoadModule('/src/stores/user.js')
    const { default: api } = await vite.ssrLoadModule('/src/api.js')
    const originalGet = api.get
    t.after(async () => {
        api.get = originalGet
        if (originalStorage) Object.defineProperty(globalThis, 'localStorage', originalStorage)
        else delete globalThis.localStorage
        await vite.close()
    })

    setActivePinia(createPinia())
    const store = useUserStore()
    store.init()
    assert.equal(store.isLogin, true)
    assert.equal(JSON.parse(storage.get('user')).username, '默认用户')
    let requests = 0
    api.get = async () => {
        requests++
        return { data: { success: true, user: { id: 1, username: '默认用户', avatar: 'avatar' } } }
    }
    await Promise.all([store.checkSession(), store.checkSession()])
    await store.checkSession()
    assert.equal(requests, 1)
    store.init()
    assert.equal(JSON.parse(storage.get('user')).avatar, 'avatar')
})
