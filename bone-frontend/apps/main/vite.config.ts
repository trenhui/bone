import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { microFePreset } from '../../tools/build/src/micro-fe-preset';
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
      babel: {
        plugins: [
          // 可选的 Babel 插件
          ['@babel/plugin-proposal-decorators', { legacy: true }],
          ['@babel/plugin-proposal-class-properties', { loose: true }]
        ]
      }
    }),
    // 微前端预设配置
    microFePreset({
      appName: 'main',
      isMain: true,
      sandbox: {
        enabled: true,
        // 沙箱配置
        scopes: {
          // 允许子应用访问的全局变量
          allowedGlobals: ['__BONE_CONFIG__', 'console', 'window', 'document']
        }
      },
      // 性能监控配置
      performance: {
        enabled: true,
        // 收集关键性能指标
        collectMetrics: ['FCP', 'LCP', 'CLS', 'TTFB']
      }
    })
  ],
  
  // 服务器配置
  server: {
    port: 3000,
    host: true,
    open: true,
    // 代理配置
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    },
    // 允许跨域
    cors: true
  },
  
  // 构建配置
  build: {
    outDir: '../../dist/main',
    // 启用源映射
    sourcemap: process.env.NODE_ENV === 'development',
    // 优化配置
    optimizeDeps: {
      // 预构建依赖
      include: [
        'react',
        'react-dom',
        'react-router-dom',
        '@bone/core/micro-fe-runtime'
      ]
    },
    // 分割代码
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom'],
          router: ['react-router-dom'],
          core: ['@bone/core/micro-fe-runtime']
        }
      }
    },
    // 最小化配置
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: process.env.NODE_ENV === 'production',
        drop_debugger: true
      }
    }
  },
  
  // 解析配置
  resolve: {
    // 别名配置
    alias: {
      '@': resolve(__dirname, './src'),
      '@bone/core': resolve(__dirname, '../../packages/core'),
      '@bone/ui': resolve(__dirname, '../../packages/ui')
    },
    // 解析文件扩展名
    extensions: ['.tsx', '.ts', '.jsx', '.js', '.json', '.scss', '.css']
  },
  
  // CSS 配置
  css: {
    // 启用 CSS 模块化
    modules: {
      localsConvention: 'camelCaseOnly',
      generateScopedName: '[name]__[local]__[hash:base64:5]'
    },
    // 预处理器配置
    preprocessorOptions: {
      scss: {
        additionalData: `@import "../../packages/ui/design-system/src/tokens/index.scss";`
      }
    }
  },
  
  // 环境变量配置
  envDir: '../../',
  envPrefix: 'VITE_',
  
  // 预览服务器配置
  preview: {
    port: 8080,
    open: true
  }
});