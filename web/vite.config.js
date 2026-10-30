import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/',
  publicDir: false,
  plugins: [vue()],
  server: {
    proxy: {
      '/railway-map/openrailway': {
        target: 'https://openrailwaymap.app', changeOrigin: true,
        rewrite: path => path.replace(/^\/railway-map\/openrailway/, ''),
        headers: { 'User-Agent': 'CRTicket development map preview' },
      },
    },
  },
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: { target: 'es2019', outDir: '../app/src/main/assets/web', emptyOutDir: true },
})
