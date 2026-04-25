import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

// 微应用模式
if (window.__POWERED_BY_QIANKUN__) {
  // Vite 中不需要设置 __webpack_public_path__
  // window.__publicPath = window.__INJECTED_PUBLIC_PATH_BY_QIANKUN__;
}

let root: ReactDOM.Root | null = null;

function render(props: { container?: HTMLElement; user?: Record<string, any> }): void {
  const { container } = props;
  const rootElement = (container || document.getElementById('root'))!;
  root = ReactDOM.createRoot(rootElement);
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}

export async function bootstrap(): Promise<void> {
  console.log('[bone-system-app] bootstraped');
}

export async function mount(props: { container?: HTMLElement; user?: Record<string, any> }): Promise<void> {
  console.log('[bone-system-app] mounted', props);
  render(props);
}

export async function unmount(_props: { container?: HTMLElement }): Promise<void> {
  if (root) {
    root.unmount();
    root = null;
  }
  console.log('[bone-system-app] unmounted');
}
