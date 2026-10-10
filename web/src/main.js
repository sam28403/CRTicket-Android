import { createApp } from 'vue'
import ElementPlus from './ui/elementPlus.js'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './assets/styles/theme.css'
import { initializeTheme } from './composables/useTheme'

initializeTheme()
import './assets/styles/fonts.css'
import App from './App.vue'
import router from './router'
import {createPinia} from "pinia";
import { initializePlatform } from './android/platform.js';

initializePlatform(router)

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
