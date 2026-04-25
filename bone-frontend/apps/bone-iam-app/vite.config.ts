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
    qiankun('bone-iam-app', {
      useDevMode: true
    })
  ],
  server: {
    port: 3003,
    open: true,
    cors: true,
    origin: 'http://localhost:3003',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
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
        name: 'bone-iam-app',
        globals: {
          react: 'React',
          'react-dom': 'ReactDOM'
        }
      }
    }
  },
  define: {
    'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV)
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: '../../tests/setup.ts',
  }
})