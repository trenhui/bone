import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { resolve } from 'path';

export default defineConfig({
  root: '.',
  mode: process.env.NODE_ENV || 'development',
  plugins: [
    react({
      jsxRuntime: 'automatic',
      // 禁用React Refresh，避免生成导致问题的脚本标签
      fastRefresh: false
    }),
    qiankun('bone-masterdata-app', {
      useDevMode: true
    })
  ],
  server: {
    port: 3005,
    open: true,
    cors: true,
    historyApiFallback: true,
    headers: {
      'Access-Control-Allow-Origin': process.env.NODE_ENV === 'production' ? '' : 'http://localhost:3000',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    optimizeDeps: {
      include: [
        'react',
        'react-dom',
        'antd',
        '@ant-design/icons',
        '@ant-design/pro-components',
      ],
    },
    rollupOptions: {
      output: {
        format: 'umd',
        name: 'bone-masterdata-app',
        globals: {
          react: 'React',
          'react-dom': 'ReactDOM'
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
    },
    extensions: ['.tsx', '.ts', '.jsx', '.js', '.json', '.scss', '.css'],
  },
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
      },
    },
  },
});
