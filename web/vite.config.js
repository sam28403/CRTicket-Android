import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { readFileSync } from 'node:fs'

const appVersion = /^crticket.version=(.+)$/m.exec(
  readFileSync(new URL('../gradle.properties', import.meta.url), 'utf8'),
)[1].trim()
const ci = process.env.CRTICKET_CI === 'true'
const publicAlias = (pattern, file) => ({
  find: pattern,
  replacement: fileURLToPath(new URL(file, import.meta.url)),
})

export default defineConfig({
  base: '/',
  define: { __CRTICKET_VERSION__: JSON.stringify(appVersion), __CRTICKET_CI__: ci },
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
  resolve: { alias: [
    ...(!ci ? [
      publicAlias(/^.*\/(?:networkFeatures|timetableVisibility|debugGeneration|timetableSettings)\.js$/,
        './src/android/publicFeatureDefaults.js'),
      publicAlias(/^.*\/use(?:NetworkFeatures|TimetableVisibility)\.js$/,
        './src/composables/publicFeatureDefaults.js'),
    ] : []),
    { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
  ] },
  build: {
    target: 'es2019',
    outDir: process.env.CRTICKET_WEB_OUTPUT || '../app/build/generated/web-public/web',
    emptyOutDir: true,
  },
})
