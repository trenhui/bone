import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  // 项目根目录
  root: '.',
  // 环境配置
  mode: process.env.NODE_ENV || 'development',
  
  // 插件配置
  plugins: [
    react({
      // 配置 React 插件
      jsxRuntime: 'automatic',
    }),
  ],
  
  // 服务器配置
  server: {
    port: 3001,
    open: true,
    // 允许跨域
    cors: true,
    // 为单页应用配置路由重定向
    historyApiFallback: true,
  },
  
  // 构建配置
  build: {
    outDir: 'dist',
    sourcemap: true,
    // 优化配置
    optimizeDeps: {
      include: [
        'react',
        'react-dom',
        'antd',
        '@ant-design/icons',
        '@ant-design/pro-components',
      ],
    },
  },
  
  // 解析配置
  resolve: {
    // 别名配置
    alias: {
      '@': resolve(__dirname, './src'),
    },
    // 解析文件扩展名
    extensions: ['.tsx', '.ts', '.jsx', '.js', '.json', '.scss', '.css'],
  },
  
  // CSS 配置
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
      },
    },
  },
});