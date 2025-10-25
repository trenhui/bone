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
      jsxRuntime: 'automatic'
    })
  ],
  
  // 服务器配置
  server: {
    port: 3001,
    open: true
  },
  
  // 构建配置
  build: {
    outDir: 'dist',
    sourcemap: true
  }
});