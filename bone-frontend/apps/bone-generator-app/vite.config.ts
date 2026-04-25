import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { name } from './package.json';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  return {
    base: mode === 'production' ? `/${name}/` : '/',
    plugins: [
      react({ jsxRuntime: 'automatic' })
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
        '@components': resolve(__dirname, 'src/components'),
        '@pages': resolve(__dirname, 'src/pages'),
        '@services': resolve(__dirname, 'src/services'),
        '@store': resolve(__dirname, 'src/store'),
        '@utils': resolve(__dirname, 'src/utils')
      }
    },
    server: {
      port: 3009,
      open: true,
      origin: 'http://localhost:3009',
      proxy: {
        '/api': {
          target: 'http://localhost:8085',
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: mode !== 'production',
      minify: 'terser',
      rollupOptions: {
        output: {
          assetFileNames: 'assets/[name].[hash:8].[ext]',
          chunkFileNames: 'chunks/[name].[hash:8].js',
          entryFileNames: 'entry/[name].[hash:8].js',
          manualChunks: {
            vendor: ['react', 'react-dom', 'react-router-dom'],
            antd: ['antd']
          }
        }
      }
    }
  };
});
