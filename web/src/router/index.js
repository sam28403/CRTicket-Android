import { createRouter, createWebHashHistory } from 'vue-router'
import MainView from '../views/MainView.vue'
import { useUserStore } from '@/stores/user.js'
import { ElMessage } from 'element-plus'

const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        { path: '/', component: MainView },
        { path: '/home', component: () => import('@/views/HomeView.vue') },
        { path: '/search', component: { render: () => null } },
        { path: '/about', component: () => import('@/views/AboutView.vue') },
        { path: '/settings', component: () => import('@/views/SettingsView.vue') },
        { path: '/history', component: () => import('@/views/HistoryView.vue') },
        { path: '/user', component: () => import('@/views/User.vue') },
        { path: '/train', component: () => import('@/views/TrainQuery.vue') },
        { path: '/station', component: () => import('@/views/Station.vue') },
        { path: '/station-map', component: () => import('@/views/StationMap.vue') },
        { path: '/list', component: () => import('@/views/TrainList.vue') },
        { path: '/monitor/settings', component: () => import('@/views/Monitor.vue') },
        { path: '/monitor', component: () => import('@/views/Monitor.vue') },
        { path: '/login', redirect: '/' },
        { path: '/register', redirect: '/' },
    ],
})

router.beforeEach(async to => {
    const user = useUserStore()
    user.init()
    if (to.path === '/history' || to.path === '/user') {
        try {
            await user.checkSession()
        } catch {
            ElMessage.error('无法读取手机中的默认用户资料')
        }
    }
    return true
})

export default router
