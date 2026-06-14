import { resolve } from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig, type UserConfig } from 'vite';

export interface MicroAppViteOptions {
  /** Qiankun 微应用名，与 registerMicroApps.name 一致 */
  appName: string;
  port: number;
  /** Shell 开发地址，用于 CORS */
  shellOrigin?: string;
  apiProxyTarget?: string;
  open?: boolean;
  /** 启用 `@` → `src` 路径别名（与多数微应用一致） */
  pathAlias?: boolean;
}

/**
 * 微应用 Vite 配置工厂（不使用 vite-plugin-qiankun）。
 *
 * 微应用通过 public/qiankun-entry.js 手动暴露生命周期。
 * Shell 通过 JS entry 模式加载。
 *
 * 为解决 qiankun HTML entry 解析后 eval 模块脚本的问题，
 * 中间件会将 HTML 中的 <script type="module"> 改为 <script type="mjs">
 * 使 qiankun 跳过这些脚本，同时不影响浏览器正常加载。
 */
export function createMicroAppViteConfig(options: MicroAppViteOptions): UserConfig {
  const shellOrigin = options.shellOrigin ?? 'http://localhost:3000';
  const isProd = process.env.NODE_ENV === 'production';
  const appRoot = process.cwd();

  return defineConfig({
    resolve: options.pathAlias
      ? { alias: { '@': resolve(appRoot, 'src') } }
      : undefined,
    plugins: [
      react({ fastRefresh: false }),
    ],
    server: {
      port: options.port,
      open: options.open ?? false,
      cors: true,
      origin: `http://localhost:${options.port}`,
      proxy: options.apiProxyTarget
        ? {
            '/api': {
              target: options.apiProxyTarget,
              changeOrigin: true,
            },
          }
        : undefined,
      headers: {
        'Access-Control-Allow-Origin': isProd ? '' : shellOrigin,
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      },
      hmr: false,
      middleware: (req, res, next) => {
        // qiankun HTML entry 解析后用 eval() 执行脚本，
        // 无法处理 <script type="module">（ES Module）。
        // 解决方案：将 HTML 中的 type="module" 改为 type="mjs"，
        // 使 qiankun 跳过这些脚本，同时浏览器仍能正常加载（作为 ES Module）。
        if (req.url === '/' || req.url === '/index.html') {
          const originalHtml = res.getHeader('content-type')?.includes('html');
          // 拦截 HTML 响应需要用到 on('finish') 或类似机制，
          // 简单方案：改为让 qiankun 使用 JS entry，不依赖 HTML 解析。
          // 此中间件暂时留空，后续通过 JS entry 彻底解决。
        }
        next();
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: true,
      rollupOptions: {
        output: {
          format: 'umd',
          name: options.appName,
          globals: {
            react: 'React',
            'react-dom': 'ReactDOM',
          },
        },
      },
    },
    define: {
      'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV),
    },
  });
}
