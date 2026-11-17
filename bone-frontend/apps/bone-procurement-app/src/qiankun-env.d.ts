/// <reference types="vite/client" />

/**
 * `vite-plugin-qiankun/helper` 子路径类型声明。
 *
 * 运行时模块由 monorepo 根 node_modules 提供（vite-plugin-qiankun/dist/helper.js），
 * 但该包的 package.json 无 exports 字段、包根无 helper.d.ts，tsc 的 bundler 解析
 * 在「子应用 node_modules 为空（npm workspaces 提升）」的仓库现状下解析不到类型。
 * 此处按 dist/helper.d.ts 的公开面显式声明，与 bone-commerce-app 的用法一致。
 */
declare module 'vite-plugin-qiankun/helper' {
  export interface QiankunProps {
    container?: HTMLElement | null;
    token?: string;
    [key: string]: unknown;
  }

  export interface Lifecycle {
    bootstrap?: () => Promise<void> | void;
    mount: (props: QiankunProps) => Promise<void> | void;
    unmount: (props: QiankunProps) => Promise<void> | void;
    update?: (props: QiankunProps) => Promise<void> | void;
  }

  export const qiankunWindow: Window & { __POWERED_BY_QIANKUN__?: boolean };

  export function renderWithQiankun(lifecycle: Lifecycle): void;
}
