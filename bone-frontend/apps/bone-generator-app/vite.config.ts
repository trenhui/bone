import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import qiankun from 'vite-plugin-qiankun'
import path from 'path'

export default defineConfig({
  plugins: [
    react({ fastRefresh: false }),
    qiankun('bone-generator-app', { useDevMode: true }),
  ],
  resolve: {
    alias: {
      'vite-plugin-qiankun/helper': path.resolve(__dirname, '../../node_modules/vite-plugin-qiankun/dist/helper.js'),
    },
  },
  server: {
    port: 3009,
    host: '0.0.0.0',
    cors: true,
    origin: 'http://localhost:3009',
    proxy: {
      '/api': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
})
