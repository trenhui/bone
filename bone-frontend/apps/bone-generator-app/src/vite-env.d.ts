/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module 'vite-plugin-qiankun/helper' {
  export interface QiankunProps {
    container: HTMLElement;
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
