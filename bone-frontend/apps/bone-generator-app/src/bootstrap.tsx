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

export const update = async (props: QiankunMountProps): Promise<void> => {};

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
