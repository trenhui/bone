import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'fs'
import path from 'path'

// 微前端开发环境插件配置
function createMicroFrontendDevPlugin(options = {}) {
  return {
    name: 'micro-frontend-dev-plugin',
    apply: 'serve',
    configureServer(server) {
      // 添加开发环境中的微前端支持中间件
      server.middlewares.use((req, res, next) => {
        // 注入微前端开发环境标识
        if (req.url.endsWith('.html')) {
          const originalSend = res.send.bind(res)
          res.send = function(body) {
            if (typeof body === 'string') {
              // 在HTML中注入微前端开发工具包的初始化脚本
              body = body.replace('</body>', `
                <script>
                  // 微前端开发环境标识
                  window.__MICRO_DEV_ENV__ = true;
                  window.__MICRO_APP_NAME__ = '${options.appName || 'extension-studio-ui'}';
                </script>
              </body>`)
            }
            originalSend(body)
          }
        }
        next()
      })
    },
    transformIndexHtml(html) {
      return {
        html,
        tags: [
          {
            tag: 'script',
            attrs: { defer: true },
            children: `
              // 微前端环境模拟
              if (!window.__POWERED_BY_WUJIE__ && !window.__MICRO_APP_ENVIRONMENT__) {
                console.log('启动微前端开发环境模拟...');
              }
            `
          }
        ]
      }
    }
  }
}

// 加载Mock数据插件
function createMockPlugin(mockDir) {
  return {
    name: 'vite-plugin-mock',
    apply: 'serve',
    configureServer(server) {
      // 只在开发环境启用mock
      if (!process.env.USE_MOCK || process.env.USE_MOCK !== 'true') return
      
      const mockData = {}
      
      // 尝试加载mock数据目录
      try {
        if (fs.existsSync(mockDir)) {
          const files = fs.readdirSync(mockDir)
          files.forEach(file => {
            if (file.endsWith('.js') || file.endsWith('.json')) {
              const filePath = path.join(mockDir, file)
              try {
                const data = require(filePath)
                Object.assign(mockData, data.default || data)
              } catch (e) {
                console.warn(`Failed to load mock file ${file}:`, e)
              }
            }
          })
        }
      } catch (e) {
        console.warn('Failed to load mock data:', e)
      }
      
      // 添加mock中间件
      server.middlewares.use((req, res, next) => {
        const { url, method } = req
        const mockKey = `${method.toUpperCase()}:${url}`
        
        if (mockData[mockKey]) {
          res.setHeader('Content-Type', 'application/json')
          res.statusCode = 200
          res.end(JSON.stringify(mockData[mockKey]))
          return
        }
        
        next()
      })
    }
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    createMicroFrontendDevPlugin({
      appName: 'extension-studio-ui'
    }),
    createMockPlugin(path.resolve(__dirname, 'src/mock-data'))
  ],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    },
    hmr: {
      overlay: true
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '~': path.resolve(__dirname, 'node_modules')
    }
  },
  css: {
    preprocessorOptions: {
      css: {
        additionalData: `
          @import '@/assets/styles/variables.css';
        `
      }
    }
  }
})