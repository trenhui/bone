import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';

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
      <ConfigProvider locale={zhCN}>
        <App user={user} />
      </ConfigProvider>
    </React.StrictMode>
  );
}

if (!window.__POWERED_BY_QIANKUN__) {
  render({});
}

export async function bootstrap() {
  console.log('[bone-masterdata-app] bootstraped');
}

export async function mount(props: any) {
  console.log('[bone-masterdata-app] mounted', props);
  render(props);
}

export async function unmount(props: any) {
  if (root) {
    root.unmount();
    root = null;
  }
  console.log('[bone-masterdata-app] unmounted');
}
