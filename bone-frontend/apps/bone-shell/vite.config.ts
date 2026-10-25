import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import qiankun from 'vite-plugin-qiankun'
import path from 'node:path'

// 联调专用：本机未构建 workspace 包的 dist，这里把各 @bone/* 包别名到其 TS 源码入口，
// 并排除 optimizeDeps 预打包，让 vite 直接以源码喂给浏览器（dev 模式无需先 build 子包）。
const pkg = (...p: string[]) => path.resolve(__dirname, '..', '..', ...p)
const boneAliases: Record<string, string> = {
  '@bone/ui': pkg('packages/ui/design-system/src/index.ts'),
  '@bone/core-event-bus': pkg('packages/core/event-bus/src/index.ts'),
  '@bone/shared-types': pkg('packages/shared-types/src/index.ts'),
  '@bone/shared-services': pkg('packages/shared-services/src/index.ts'),
  '@bone/shared-config': pkg('packages/shared-config/src/index.ts'),
  '@bone/shared-utils': pkg('packages/shared-utils/src/index.ts'),
  '@bone/ui-schema': pkg('packages/ui-schema/src/index.ts'),
  '@bone/shared-components': pkg('packages/shared-components/src/index.ts'),
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    qiankun('bone-shell', { useDevMode: true }),
  ],
  resolve: {
    alias: boneAliases,
  },
  optimizeDeps: {
    // workspace 源码包不参与 esbuild 预打包扫描（否则会因 dist 缺失而解析失败）
    exclude: Object.keys(boneAliases),
    // antd 图标/颜色库有数千个相对重导出，交给 esbuild 预打包一次性合成，避免逐文件解析失败
    include: ['@ant-design/icons', '@ant-design/colors'],
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
    cors: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true
  }
})
