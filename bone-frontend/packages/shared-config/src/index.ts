import { defineConfig, type Plugin, type UserConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import path from 'path';

/**
 * 移除 React Refresh 注入脚本（Qiankun 子应用运行时不兼容）
 */
function removeReactRefreshPlugin(): Plugin {
  return {
    name: 'remove-react-refresh',
    transformIndexHtml(html) {
      return html.replace(/<script type="module">import\s*\{[^}]*\}\s*from\s*["']\/@react-refresh["'];[\s\S]*?<\/script>/g, '');
    },
  };
}

export interface CreateQiankunViteConfigOptions {
  /** 额外 resolve.alias（如 `{ '@': path.resolve(__dirname, 'src') }`） */
  aliases?: Record<string, string>;
  /** 代理目标（默认 http://localhost:8888） */
  proxyTarget?: string;
  /** 额外配置，覆盖默认（展开在末尾，优先级最高） */
  config?: UserConfig;
}

/**
 * 生成 Qiankun 微应用的标准 Vite 配置工厂。
 * 依赖各微应用以独立 node_modules 运行（process.cwd() 为 app 目录）。
 *
 * ```ts
 * export default createQiankunViteConfig('bone-iam-app', 3003);
 * ```
 */
export function createQiankunViteConfig(name: string, port: number, options: CreateQiankunViteConfigOptions = {}): UserConfig {
  const helper = path.resolve(process.cwd(), '../../node_modules/vite-plugin-qiankun/dist/helper.js');
  const { aliases = {}, proxyTarget = 'http://localhost:8888', config = {} } = options;

  return defineConfig({
    plugins: [
      react({ fastRefresh: false }),
      removeReactRefreshPlugin(),
      qiankun(name, { useDevMode: true }),
    ],
    resolve: {
      alias: {
        'vite-plugin-qiankun/helper': helper,
        ...aliases,
      },
    },
    server: {
      port,
      host: '0.0.0.0',
      cors: true,
      origin: `http://localhost:${port}`,
      hmr: { overlay: false },
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: true,
    },
    ...config,
  });
}
