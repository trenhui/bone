import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import qiankun from 'vite-plugin-qiankun'
import path from 'path'

function removeReactRefreshPlugin(): Plugin {
  return {
    name: 'remove-react-refresh',
    transformIndexHtml(html) {
      return html.replace(/<script type="module">import\s*\{[^}]*\}\s*from\s*["']\/@react-refresh["'];[\s\S]*?<\/script>/g, '');
    },
  };
}

export default defineConfig({
  plugins: [
    react({ fastRefresh: false }),
    removeReactRefreshPlugin(),
    qiankun('bone-extension-app', { useDevMode: true }),
  ],
  resolve: {
    alias: {
      'vite-plugin-qiankun/helper': path.resolve(__dirname, '../../node_modules/vite-plugin-qiankun/dist/helper.js'),
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3008,
    host: '0.0.0.0',
    cors: true,
    origin: 'http://localhost:3008',
    hmr: { overlay: false },
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
