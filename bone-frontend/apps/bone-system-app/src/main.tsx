import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import App from './App';
import { subscribeLocaleChange, i18n } from '@bone/shared-utils';

// i18n 单例每应用须各自初始化（见 shared-utils/i18n/index.ts 头注）；
// 此处 import 即触发语言包加载，void 仅表达「副作用导入」以过 noUnusedLocals。
void i18n;
import './dayjs-setup';
import './index.css';
import { AppAntdProvider } from './components/AppAntdProvider';

let root: Root | null = null;

function render(props?: QiankunProps) {
  const { container } = props || {};
  const mountNode = container
    ? container.querySelector('#root')
    : document.getElementById('root');

  if (!mountNode) return;

  const token = (props as { token?: string })?.token;
  if (token) {
    localStorage.setItem('token', token);
  }

  root = createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <AppAntdProvider>
      <App />
    </AppAntdProvider>
    </React.StrictMode>,
  );
}

renderWithQiankun({
  bootstrap() {},
  mount(props: QiankunProps) {
  subscribeLocaleChange();
    const token = (props as { token?: string })?.token;
    if (token) {
      localStorage.setItem('token', token);
    }
    render(props);
  },
  unmount() {
    if (root) {
      root.unmount();
      root = null;
    }
  },
});

if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  subscribeLocaleChange();
  render();
}

export async function bootstrap() {}
export async function mount(props: QiankunProps) {
  const token = (props as { token?: string })?.token;
  if (token) {
    localStorage.setItem('token', token);
  }
  render(props);
}
export async function unmount() {
  if (root) {
    root.unmount();
    root = null;
  }
}
