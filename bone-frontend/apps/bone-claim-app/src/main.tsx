import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import { App } from './App';
import './index.css';
import { subscribeLocaleChange } from '@bone/shared-utils';
import { AppAntdProvider } from './components/AppAntdProvider';

let root: Root | null = null;

function render(props?: QiankunProps) {
  const { container } = props || {};
  const mountNode = container ? container.querySelector('#root') : document.getElementById('root');
  if (!mountNode) return;

  /**
   * 是否在 shell 容器内运行（决定是否渲染微应用自带导航）。
   *
   * 判据取「mount props 带 container」或 qiankun 注入的全局标记，任一成立即为嵌入式；
   * 两者都不成立（`:3013` 直开）才是独立调试。嵌入式的唯一后果是**不渲染侧边栏**——
   * 全局导航由 shell 依据 IAM 菜单统一渲染，微应用不再持有第二份导航副本。
   */
  const embedded = Boolean(container) || Boolean(qiankunWindow.__POWERED_BY_QIANKUN__);

  const token = (props as { token?: string })?.token;
  if (token) {
    localStorage.setItem('token', token);
  }

  root = createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <AppAntdProvider>
        <App embedded={embedded} />
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

export async function bootstrap(): Promise<void> {}
export async function mount(props: QiankunProps): Promise<void> {
  const token = (props as { token?: string })?.token;
  if (token) {
    localStorage.setItem('token', token);
  }
  render(props);
}
export async function unmount(): Promise<void> {
  if (root) {
    root.unmount();
    root = null;
  }
}
