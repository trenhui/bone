import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import App from './App';
import './index.css';
import { subscribeLocaleChange } from '@bone/shared-utils';
import { setQiankunToken } from '@bone/shared-services';
import { AppAntdProvider } from './components/AppAntdProvider';

let root: Root | null = null;

function render(props?: QiankunProps) {
  const { container } = props || {};
  const mountNode = container
    ? container.querySelector('#root')
    : document.getElementById('root');
  if (!mountNode) return;

  const token = (props as { token?: string })?.token;
  if (token) setQiankunToken(token);

  root = createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <AppAntdProvider>
        <App />
      </AppAntdProvider>
    </React.StrictMode>,
  );
}

const lifecycle = {
  async bootstrap(): Promise<void> {},
  async mount(props: QiankunProps): Promise<void> {
    subscribeLocaleChange();
    render(props);
  },
  async unmount(): Promise<void> {
    if (root) {
      root.unmount();
      root = null;
    }
    setQiankunToken(null);
  },
};

renderWithQiankun(lifecycle);

if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  subscribeLocaleChange();
  render();
}

export const { bootstrap, mount, unmount } = lifecycle;
