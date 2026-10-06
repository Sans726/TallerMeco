import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwind from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const apiTarget = loadEnv(mode, '.', 'TALLERMECO_').TALLERMECO_API_TARGET || 'http://127.0.0.1:8080'
  return {
    plugins: [vue(), tailwind()],
    server: { proxy: { '/api': apiTarget } },
    build: { outDir: '../backend/src/main/resources/static', emptyOutDir: true }
  }
})
