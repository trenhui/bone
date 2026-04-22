import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/lib/locale/zh_CN';

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
    </React.StrictMode>,
  );
}

if (!window.__POWERED_BY_QIANKUN__) {
  render({});
}

export async function bootstrap() {
  console.log('[bone-iam-app] bootstraped');
}

export async function mount(props: any) {
  console.log('[bone-iam-app] mounted', props);
  render(props);
}

export async function unmount(props: any) {
  if (root) {
    root.unmount();
    root = null;
  }
  console.log('[bone-iam-app] unmounted');
}