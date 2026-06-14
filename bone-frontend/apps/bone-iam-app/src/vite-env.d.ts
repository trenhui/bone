/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_PORT?: number;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

// vite-plugin-qiankun/helper 类型声明
declare module 'vite-plugin-qiankun/helper' {
  export interface QiankunProps {
    container?: HTMLElement;
    [x: string]: any;
  }

  export interface QiankunLifeCycle {
    bootstrap(): void | Promise<void>;
    mount(props: QiankunProps): void | Promise<void>;
    unmount(props: QiankunProps): void | Promise<void>;
    update?(props: QiankunProps): void | Promise<void>;
  }

  export interface QiankunWindow {
    __POWERED_BY_QIANKUN__?: boolean;
    [x: string]: any;
  }

  export const qiankunWindow: QiankunWindow;
  export const renderWithQiankun: (qiankunLifeCycle: QiankunLifeCycle) => void;
}
