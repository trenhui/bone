import { resolve } from 'node:path';

import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
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
 * 微应用 Vite 配置工厂（Qiankun UMD + fastRefresh 关闭）。
 * 与 doc/architecture/bone-前端架构.md 主链路一致。
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
      qiankun(options.appName, { useDevMode: true }),
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
