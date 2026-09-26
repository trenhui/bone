import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import { App } from './App';
import './index.css';
import { subscribeLocaleChange } from '@bone/shared-utils';

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
      <App />
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
