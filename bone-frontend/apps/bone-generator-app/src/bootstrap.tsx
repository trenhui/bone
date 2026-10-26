import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

// 扩展 Window 接口
declare global {
  interface Window {
    __POWERED_BY_QIANKUN__?: boolean;
  }
}

interface QiankunMountProps {
  container?: HTMLElement;
  [key: string]: unknown;
}

let root: ReactDOM.Root | null = null;

export const bootstrap = async (): Promise<void> => {};

export const mount = async (props: QiankunMountProps): Promise<void> => {
  const container = props.container || document.getElementById('root');
  root = ReactDOM.createRoot(container!);
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
};

export const unmount = async (): Promise<void> => {
  if (root) {
    root.unmount();
    root = null;
  }
};

// qiankun 要求导出 update 钩子（props 签名须保留），本应用无增量更新逻辑故为空实现；
// 未使用参数必须以 _ 开头，否则 noUnusedParameters 下 `npm run typecheck` 直接报错。
export const update = async (_props: QiankunMountProps): Promise<void> => {};

// 独立运行时
if (!window.__POWERED_BY_QIANKUN__) {
  const container = document.getElementById('root');
  root = ReactDOM.createRoot(container!);
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}
