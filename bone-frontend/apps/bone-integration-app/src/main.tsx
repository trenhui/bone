import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import './index.css';

// 微应用模式
if (window.__POWERED_BY_QIANKUN__) {
  // Vite 中不需要设置 __webpack_public_path__
  // window.__publicPath = window.__INJECTED_PUBLIC_PATH_BY_QIANKUN__;
}

let root: ReactDOM.Root | null = null;

function render(props: any) {
  const { container, user } = props;
  const rootElement = (container || document.getElementById('root'))!;
  root = ReactDOM.createRoot(rootElement);
  root.render(
    <React.StrictMode>
      <App user={user} />
    </React.StrictMode>,
  );
}

if (!window.__POWERED_BY_QIANKUN__) {
  render({});
}

export async function bootstrap() {
  console.log('[bone-integration-app] bootstraped');
}

export async function mount(props: any) {
  console.log('[bone-integration-app] mounted', props);
  render(props);
}

export async function unmount(props: any) {
  if (root) {
    root.unmount();
    root = null;
  }
  console.log('[bone-integration-app] unmounted');
}