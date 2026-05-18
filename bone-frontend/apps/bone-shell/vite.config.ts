import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: '0.0.0.0',
    proxy: {
      // 扩展：经 Gateway 统一入口（Studio 直连可改为 8088）
      '/api/v1/extension': {
        target: process.env.BONE_EXTENSION_PROXY_TARGET ?? 'http://localhost:8888',
        changeOrigin: true,
      },
      '/api/v1/iam': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/v1/masterdata': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/v1/system': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api/v1/console': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api/v1/integration': {
        target: 'http://localhost:8085',
        changeOrigin: true,
      },
      '/api/v1/generator': {
        target: process.env.BONE_GENERATOR_PROXY_TARGET ?? 'http://localhost:8888',
        changeOrigin: true,
      },
      '/api/v1/metadata': {
        target: 'http://localhost:9001',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true
  }
})