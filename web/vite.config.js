import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { readFileSync } from 'node:fs'

const appVersion = /^crticket.version=(.+)$/m.exec(
  readFileSync(new URL('../gradle.properties', import.meta.url), 'utf8'),
)[1].trim()

export default defineConfig({
  base: '/',
  define: { __CRTICKET_VERSION__: JSON.stringify(appVersion) },
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
