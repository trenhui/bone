import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderWithQiankun, qiankunWindow, type QiankunProps } from 'vite-plugin-qiankun/helper';
import App from './App';
import { setQiankunToken } from './services/api';
import './index.css';

let root: Root | null = null;

function render(props?: QiankunProps) {
  const { container } = props || {};
  const mountNode = container
    ? container.querySelector('#root')
    : document.getElementById('root');

  if (!mountNode) return;

  // qiankun 通过 props 传递 token，写入内存 + localStorage
  const token = (props as { token?: string })?.token;
  if (token) {
    setQiankunToken(token);
  }

  root = createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}

// 注册 qiankun 生命周期
renderWithQiankun({
  bootstrap() {
    // 首次加载时调用，仅一次
  },
  mount(props: QiankunProps) {
    // qiankun 挂载时将 token 写入内存 + localStorage
    const token = (props as { token?: string })?.token;
    if (token) {
      setQiankunToken(token);
    }
    render(props);
  },
  unmount() {
    // 卸载时清除内存 token 并销毁 React root
    setQiankunToken(null);
    if (root) {
      root.unmount();
      root = null;
    }
  },
});

// 独立运行时直接渲染
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  render();
}

// 导出生命周期供 qiankun 识别（开发模式下 vite-plugin-qiankun 会自动处理）
export async function bootstrap() {}
export async function mount(props: QiankunProps) {
  const token = (props as { token?: string })?.token;
  if (token) {
    setQiankunToken(token);
  }
  render(props);
}
export async function unmount() {
  setQiankunToken(null);
  if (root) {
    root.unmount();
    root = null;
  }
}
