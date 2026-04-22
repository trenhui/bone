import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import qiankun from 'vite-plugin-qiankun'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react({
      // 禁用React Refresh，避免生成导致问题的脚本标签
      fastRefresh: false
    }),
    qiankun('bone-integration-app', {
      useDevMode: true
    })
  ],
  server: {
    port: 3006,
    open: true,
    headers: {
      'Access-Control-Allow-Origin': process.env.NODE_ENV === 'production' ? '' : 'http://localhost:3000',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      output: {
        format: 'umd',
        name: 'bone-integration-app',
        globals: {
          react: 'React',
          'react-dom': 'ReactDOM'
        }
      }
    }
  }
})