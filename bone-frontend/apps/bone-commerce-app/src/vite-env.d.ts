/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_PORT?: number;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module 'vite-plugin-qiankun/helper' {
  export interface QiankunProps {
    container: HTMLElement;
    /**
     * 主应用通过 qiankun 透传的自定义 props（当前用到 `token`）。
     *
     * 用 `unknown` 而非 `any`：索引签名的存在只是声明「允许额外键」，并不需要它们
     * 丧失类型。取值方需先窄化（`props.token as string | undefined`），这正是我们
     * 想要的——`any` 会让拼错的键名静默通过类型检查。
     */
    [key: string]: unknown;
  }
  export interface QiankunLifeCycle {
    bootstrap(): Promise<void>;
    mount(props: QiankunProps): Promise<void>;
    unmount(props: QiankunProps): Promise<void>;
    update?(props: QiankunProps): Promise<void>;
  }
  export interface QiankunWindow extends Window {
    __POWERED_BY_QIANKUN__?: boolean;
    __INJECTED_PUBLIC_PATH_BY_QIANKUN__?: string;
  }
  export const qiankunWindow: QiankunWindow;
  export function renderWithQiankun(lifeCycles: {
    bootstrap?(props?: QiankunProps): void | Promise<void>;
    mount?(props: QiankunProps): void | Promise<void>;
    unmount?(props: QiankunProps): void | Promise<void>;
    update?(props: QiankunProps): void | Promise<void>;
  }): void;
}
